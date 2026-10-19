package com.giovanicancherini.orderservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Postgres e Kafka próprios e descartáveis, em portas aleatórias: os testes não dependem
// do compose, de porta fixa nem de outro banco que esteja rodando na máquina.
// Como beans, o Spring só desliga os containers depois de quem depende deles.
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:16-alpine");
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer("apache/kafka:3.8.0");
	}

}
