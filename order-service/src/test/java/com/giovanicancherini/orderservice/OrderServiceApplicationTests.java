package com.giovanicancherini.orderservice;

import com.giovanicancherini.orderservice.api.OrderCreateRequest;
import com.giovanicancherini.orderservice.domain.Order;
import com.giovanicancherini.orderservice.outbox.OutboxEvent;
import com.giovanicancherini.orderservice.outbox.OutboxEventRepository;
import com.giovanicancherini.orderservice.repository.OrderRepository;
import com.giovanicancherini.orderservice.service.OrderService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderServiceApplicationTests {

	@Autowired
	OrderService orderService;

	@Autowired
	OutboxEventRepository outboxEventRepository;

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	ConsumerFactory<?, ?> consumerFactory;

	@Autowired
	JsonMapper jsonMapper;

	@Value("${app.kafka.topic.orders}")
	String topic;

	@Test
	void contextLoads() {
	}

	// O pedido grava o evento no outbox; o relay o publica com o mesmo eventId,
	// a key do cliente e o token de tipo no header, e marca a linha como enviada.
	@Test
	void createdOrderIsPublishedThroughTheOutbox() throws Exception {
		Order order = orderService.create(new OrderCreateRequest("cliente-outbox", "Notebook", 450000L));

		OutboxEvent outboxEvent = outboxEventRepository.findAll().stream()
				.filter(event -> event.getPayload().contains(order.getId().toString()))
				.findFirst().orElseThrow();
		waitUntil(() -> outboxEventRepository.findById(outboxEvent.getId()).orElseThrow().getSentAt() != null);

		ConsumerRecord<?, ?> record = findInOrdersTopic(outboxEvent.getId().toString());
		assertThat(record).isNotNull();
		assertThat(record.key()).isEqualTo("cliente-outbox");
		assertThat(new String(record.headers().lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME).value(),
				StandardCharsets.UTF_8)).isEqualTo("created");
		assertThat(String.valueOf(record.value())).contains("\"totalAmountInCents\":450000");
	}

	// A falha vem depois de o evento ir para o outbox (como um commit recusado pelo banco):
	// o rollback leva o pedido e o evento juntos, e nada é publicado.
	@Test
	void rolledBackTransactionLeavesNeitherOrderNorEvent() {
		UUID[] orderId = new UUID[1];

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			orderId[0] = orderService.create(
					new OrderCreateRequest("cliente-rollback", "Notebook", 1000L)).getId();
			throw new IllegalStateException("falha depois de gravar");
		})).hasMessage("falha depois de gravar");

		assertThat(orderRepository.existsById(orderId[0])).isFalse();
		assertThat(outboxEventRepository.findAll())
				.noneMatch(event -> event.getPayload().contains(orderId[0].toString()));
	}

	// Centavos fracionados não podem ser truncados em silêncio (1055.5 viraria 1055).
	@Test
	void fractionalCentsAreRejected() {
		assertThatThrownBy(() -> jsonMapper.readValue(
				"{\"customerId\":\"c\",\"product\":\"p\",\"totalAmountInCents\":1055.5}", OrderCreateRequest.class))
				.isInstanceOf(tools.jackson.core.JacksonException.class);
	}

	private ConsumerRecord<?, ?> findInOrdersTopic(String valueFragment) {
		Properties overrides = new Properties();
		overrides.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		overrides.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		try (Consumer<?, ?> consumer = consumerFactory.createConsumer("outbox-test-" + UUID.randomUUID(), null, null, overrides)) {
			consumer.subscribe(List.of(topic));
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

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 30_000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(200);
		}
	}

}
