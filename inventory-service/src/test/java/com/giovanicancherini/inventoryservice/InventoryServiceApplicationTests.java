package com.giovanicancherini.inventoryservice;

import com.giovanicancherini.inventoryservice.domain.ReservationStatus;
import com.giovanicancherini.inventoryservice.repository.ReservationRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class InventoryServiceApplicationTests {

	@Autowired
	KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	ConsumerFactory<?, ?> consumerFactory;

	@Autowired
	ReservationRepository reservationRepository;

	@Value("${app.kafka.topic.orders}")
	String topic;

	@Value("${app.kafka.topic.orders-dead-letter}")
	String deadLetterTopic;

	@Test
	void contextLoads() {
	}

	// Caminho real do consumidor: token no header, desserialização tipada e
	// @KafkaHandler. O valor tem que chegar com as 2 casas (4500.00, não 4500.0).
	@Test
	void consumesCreatedEventKeepingTheAmountScale(CapturedOutput output) throws Exception {
		send("created", "cliente-1", created(UUID.randomUUID(), UUID.randomUUID()));

		waitUntil(() -> output.getOut().contains("Pedido criado"));
		assertThat(output.getOut()).contains("Pedido criado: cliente=cliente-1 produto=Notebook valor=4500.00");
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

		Properties overrides = new Properties();
		overrides.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		overrides.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		try (Consumer<?, ?> consumer = consumerFactory.createConsumer("dlt-test", null, null, overrides)) {
			consumer.subscribe(List.of(deadLetterTopic));
			ConsumerRecord<?, ?> deadLetter = null;
			long deadline = System.currentTimeMillis() + 30_000;
			while (deadLetter == null && System.currentTimeMillis() < deadline) {
				for (ConsumerRecord<?, ?> record : consumer.poll(Duration.ofMillis(500))) {
					if (poison.equals(record.value())) {
						deadLetter = record;
					}
				}
			}
			assertThat(deadLetter).isNotNull();
			assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
		}
	}

	private void send(String type, String key, String json) throws Exception {
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
		record.headers().add(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
				type.getBytes(StandardCharsets.UTF_8));
		kafkaTemplate.send(record).get();
	}

	private static String created(UUID eventId, UUID orderId) {
		return created(eventId, orderId, "cliente-1");
	}

	private static String created(UUID eventId, UUID orderId, String customerId) {
		return """
				{"eventId":"%s","orderId":"%s","customerId":"%s",\
				"product":"Notebook","totalAmount":4500.00,"occurredAt":"2026-10-19T20:00:00Z"}"""
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
