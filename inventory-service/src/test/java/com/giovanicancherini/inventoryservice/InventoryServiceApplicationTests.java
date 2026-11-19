package com.giovanicancherini.inventoryservice;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class InventoryServiceApplicationTests {

	@Autowired
	KafkaTemplate<String, String> kafkaTemplate;

	@Value("${app.kafka.topic.orders}")
	String topic;

	@Test
	void contextLoads() {
	}

	// Caminho real do consumidor: token no header, desserialização tipada e
	// @KafkaHandler. O valor tem que chegar com as 2 casas (4500.00, não 4500.0).
	@Test
	void consumesCreatedEventKeepingTheAmountScale(CapturedOutput output) throws Exception {
		String json = """
				{"orderId":"6f1c2a8e-0000-4000-8000-000000000001","customerId":"cliente-1",\
				"product":"Notebook","totalAmount":4500.00,"occurredAt":"2026-10-19T20:00:00Z"}""";
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, "cliente-1", json);
		record.headers().add(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
				"created".getBytes(StandardCharsets.UTF_8));

		kafkaTemplate.send(record).get();

		long deadline = System.currentTimeMillis() + 30_000;
		while (!output.getOut().contains("Pedido criado") && System.currentTimeMillis() < deadline) {
			Thread.sleep(200);
		}
		assertThat(output.getOut()).contains("Pedido criado: cliente=cliente-1 produto=Notebook valor=4500.00");
	}

}
