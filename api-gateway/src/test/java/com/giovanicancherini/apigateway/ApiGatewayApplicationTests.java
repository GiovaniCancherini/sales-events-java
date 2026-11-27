package com.giovanicancherini.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// O order-service aqui é um HttpServer do próprio JDK, que guarda o caminho e o
// X-Correlation-Id que recebeu: o teste confere o que o gateway entrega ao serviço.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
class ApiGatewayApplicationTests {

	static final AtomicReference<String> receivedPath = new AtomicReference<>();
	static final AtomicReference<String> receivedCorrelationId = new AtomicReference<>();
	static final AtomicReference<String> receivedTraceparent = new AtomicReference<>();
	static final HttpServer orderService = startOrderService();

	@LocalServerPort
	int port;

	WebTestClient client;

	@DynamicPropertySource
	static void routeToFakeOrderService(DynamicPropertyRegistry registry) {
		registry.add("ORDER_SERVICE_URL", () -> "http://localhost:" + orderService.getAddress().getPort());
	}

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
		receivedPath.set(null);
		receivedCorrelationId.set(null);
		receivedTraceparent.set(null);
	}

	@AfterAll
	static void stopOrderService() {
		orderService.stop(0);
	}

	@Test
	void routesOrdersWithoutTheApiPrefixAndCreatesACorrelationId() {
		String responseId = client.get().uri("/api/orders?page=0&size=10")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo("{\"content\":[]}")
				.returnResult().getResponseHeaders().getFirst(CorrelationIdFilter.HEADER);

		assertThat(receivedPath.get()).isEqualTo("/orders?page=0&size=10");
		assertThat(responseId).isNotBlank().isEqualTo(receivedCorrelationId.get());
	}

	@Test
	void keepsAValidCorrelationIdFromTheClient() {
		client.get().uri("/api/orders").header(CorrelationIdFilter.HEADER, "abc-123")
				.exchange()
				.expectHeader().valueEquals(CorrelationIdFilter.HEADER, "abc-123");

		assertThat(receivedCorrelationId.get()).isEqualTo("abc-123");
	}

	// Texto livre no header iria parar no log dos serviços; o gateway troca por um id novo.
	@Test
	void replacesAnInvalidCorrelationId() {
		client.get().uri("/api/orders").header(CorrelationIdFilter.HEADER, "linha falsa\tno log")
				.exchange()
				.expectStatus().isOk();

		assertThat(receivedCorrelationId.get()).isNotEqualTo("linha falsa\tno log").hasSize(36);
	}

	// O gateway abre o trace (span raiz) e o entrega ao order-service no header W3C.
	@Test
	void startsTheTraceAndPassesItToTheOrderService() {
		client.get().uri("/api/orders").exchange().expectStatus().isOk();

		assertThat(receivedTraceparent.get()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
	}

	@Test
	void allowsCorsPreflightFromTheShell() {
		client.method(HttpMethod.OPTIONS).uri("/api/orders")
				.header(HttpHeaders.ORIGIN, "http://localhost:4200")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200");
	}

	@Test
	void rejectsCorsPreflightFromAnUnknownOrigin() {
		client.method(HttpMethod.OPTIONS).uri("/api/orders")
				.header(HttpHeaders.ORIGIN, "https://site-qualquer.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.expectStatus().isForbidden();
	}

	private static HttpServer startOrderService() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				receivedPath.set(exchange.getRequestURI().toString());
				receivedCorrelationId.set(exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER));
				receivedTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
				byte[] body = "{\"content\":[]}".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream output = exchange.getResponseBody()) {
					output.write(body);
				}
			});
			server.start();
			return server;
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
