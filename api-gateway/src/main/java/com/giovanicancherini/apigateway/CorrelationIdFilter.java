package com.giovanicancherini.apigateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

// Toda requisição sai do gateway com um X-Correlation-Id, repassado ao serviço e devolvido ao
// navegador. Um id vindo do cliente só é aceito se for curto e sem caracteres especiais: ele
// acaba em log, e texto livre ali permite forjar linhas de log.
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String received = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = received != null && VALID_ID.matcher(received).matches()
                ? received
                : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, correlationId)))
                .build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
