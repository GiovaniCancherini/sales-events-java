package com.giovanicancherini.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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

}
