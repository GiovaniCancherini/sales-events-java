package com.giovanicancherini.inventoryservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

// Kafka próprio e descartável: sem ele, com o compose no ar, o teste entraria no mesmo
// grupo do consumidor de verdade (inventory-service) e poderia consumir eventos reais.
// Como bean, o Spring só desliga o container depois do listener que depende dele.
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer("apache/kafka:3.8.0");
	}

}
