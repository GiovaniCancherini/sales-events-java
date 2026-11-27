package com.giovanicancherini.inventoryservice;

import com.giovanicancherini.inventoryservice.domain.ReservationStatus;
import com.giovanicancherini.inventoryservice.repository.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTracing
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class InventoryServiceApplicationTests {

	@Autowired
	KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	ConsumerFactory<?, ?> consumerFactory;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	MeterRegistry meterRegistry;

	@Value("${app.kafka.topic.orders}")
	String topic;

	@Value("${app.kafka.topic.orders-dead-letter}")
	String deadLetterTopic;

	@Test
	void contextLoads() {
	}

	// Caminho real do consumidor: token no header, desserialização tipada e
	// @KafkaHandler. O valor chega em centavos inteiros, sem escala para perder.
	@Test
	void consumesCreatedEventWithAmountInCents(CapturedOutput output) throws Exception {
		send("created", "cliente-1", created(UUID.randomUUID(), UUID.randomUUID()));

		waitUntil(() -> output.getOut().contains("Pedido criado"));
		assertThat(output.getOut()).contains("Pedido criado: cliente=cliente-1 produto=Notebook valorEmCentavos=450000");
	}

	// Redelivery do created depois do cancelamento: sem o processed_events, o save
	// regravaria a reserva como RESERVED e o estoque do pedido cancelado voltaria a ficar preso.
	@Test
	void duplicateCreatedAfterCancelDoesNotReserveAgain(CapturedOutput output) throws Exception {
		String customerId = "cliente-duplicado";
		UUID orderId = UUID.randomUUID();
		UUID createdEventId = UUID.randomUUID();
		UUID markerOrderId = UUID.randomUUID();

		send("created", customerId, created(createdEventId, orderId, customerId));
		send("cancelled", customerId, cancelled(UUID.randomUUID(), orderId, customerId));
		send("created", customerId, created(createdEventId, orderId, customerId));
		// Mesma key, mesma partição: quando este chega ao banco, os três anteriores já foram processados.
		send("created", customerId, created(UUID.randomUUID(), markerOrderId, customerId));

		waitUntil(() -> reservationRepository.existsById(markerOrderId));
		assertThat(reservationRepository.findById(orderId).orElseThrow().getStatus())
				.isEqualTo(ReservationStatus.RELEASED);
		assertThat(output.getOut()).contains("Evento duplicado ignorado: eventId=" + createdEventId);
	}

	// Mensagem que não desserializa não trava a partição: vai para o DLT, crua, com o motivo nos headers.
	@Test
	void poisonMessageGoesToDeadLetterTopic() throws Exception {
		String poison = "nao-e-json-" + UUID.randomUUID();
		send("created", "cliente-veneno", poison);

		ConsumerRecord<?, ?> deadLetter = findInDeadLetterTopic(poison);
		assertThat(deadLetter).isNotNull();
		assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
		assertThat(meterRegistry.get("orders.dead.letter").counter().count()).isGreaterThanOrEqualTo(1);
	}

	// Falha no processamento (produtor quebrou o contrato e mandou sem eventId): o INSERT em
	// processed_events falha, o handler tenta 3 vezes e só então manda para o DLT, com o header
	// de tipo original, para dar para reprocessar depois de corrigir.
	@Test
	void processingFailureIsRetriedThenSentToDeadLetterTopic(CapturedOutput output) throws Exception {
		String customerId = "cliente-sem-event-id-" + UUID.randomUUID();
		send("created", customerId, """
				{"orderId":"%s","customerId":"%s","product":"Notebook",\
				"totalAmountInCents":450000,"occurredAt":"2026-10-19T20:00:00Z"}"""
				.formatted(UUID.randomUUID(), customerId));

		ConsumerRecord<?, ?> deadLetter = findInDeadLetterTopic(customerId);
		assertThat(deadLetter).isNotNull();
		assertThat(new String(deadLetter.headers().lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME).value(),
				StandardCharsets.UTF_8)).isEqualTo("created");
		assertThat(output.getOut().split("Pedido criado: cliente=" + customerId + " ", -1)).hasSize(4);
	}

	private ConsumerRecord<?, ?> findInDeadLetterTopic(String valueFragment) {
		Properties overrides = new Properties();
		overrides.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		overrides.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		try (Consumer<?, ?> consumer = consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), null, null, overrides)) {
			consumer.subscribe(List.of(deadLetterTopic));
			long deadline = System.currentTimeMillis() + 30_000;
			while (System.currentTimeMillis() < deadline) {
				for (ConsumerRecord<?, ?> record : consumer.poll(Duration.ofMillis(500))) {
					if (String.valueOf(record.value()).contains(valueFragment)) {
						return record;
					}
				}
			}
			return null;
		}
	}

	// Headers como o relay do order-service manda: o consumidor continua o trace da requisição
	// original, e o X-Correlation-Id do gateway aparece no log de quem consome.
	@Test
	void consumerContinuesTheTraceAndLogsTheCorrelationId(CapturedOutput output) throws Exception {
		String traceId = "0af7651916cd43dd8448eb211c80319c";
		send("created", "cliente-trace", created(UUID.randomUUID(), UUID.randomUUID(), "cliente-trace"), Map.of(
				"traceparent", "00-" + traceId + "-b7ad6b7169203331-01",
				"X-Correlation-Id", "corr-teste-consumidor"));

		waitUntil(() -> output.getOut().contains("cliente=cliente-trace"));
		assertThat(output.getOut().lines().filter(line -> line.contains("cliente=cliente-trace")).findFirst().orElseThrow())
				.contains(traceId)
				.contains("corr-teste-consumidor");
	}

	private void send(String type, String key, String json) throws Exception {
		send(type, key, json, Map.of());
	}

	private void send(String type, String key, String json, Map<String, String> extraHeaders) throws Exception {
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
		record.headers().add(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
				type.getBytes(StandardCharsets.UTF_8));
		extraHeaders.forEach((name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
		kafkaTemplate.send(record).get();
	}

	private static String created(UUID eventId, UUID orderId) {
		return created(eventId, orderId, "cliente-1");
	}

	private static String created(UUID eventId, UUID orderId, String customerId) {
		return """
				{"eventId":"%s","orderId":"%s","customerId":"%s",\
				"product":"Notebook","totalAmountInCents":450000,"occurredAt":"2026-10-19T20:00:00Z"}"""
				.formatted(eventId, orderId, customerId);
	}

	private static String cancelled(UUID eventId, UUID orderId, String customerId) {
		return """
				{"eventId":"%s","orderId":"%s","customerId":"%s","occurredAt":"2026-10-19T20:05:00Z"}"""
				.formatted(eventId, orderId, customerId);
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 30_000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(200);
		}
	}

}
