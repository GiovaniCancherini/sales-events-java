package com.giovanicancherini.orderservice;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

	public static void main(String[] args) {
		String dbPassword = System.getenv("DB_PASSWORD");
		if (dbPassword == null || dbPassword.isBlank()) {
			throw new IllegalStateException(
					"DB_PASSWORD is not set. Set it to ORDER_POSTGRES_PASSWORD from .env (see .env.example).");
		}
		SpringApplication.run(OrderServiceApplication.class, args);
	}

	// O relay roda a cada 500 ms; observado, geraria dois traces vazios por segundo.
	// O que importa dele já viaja no trace da requisição, pelos headers guardados no outbox.
	@Bean
	ObservationPredicate ignoreScheduledTasks() {
		return (name, context) -> !name.equals("tasks.scheduled.execution");
	}

}
