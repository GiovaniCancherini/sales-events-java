package com.giovanicancherini.orderconsumer.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Espelha o contrato publicado pelo order-service. Em produção isso seria
 * uma dependência de contrato compartilhada (ou Avro + Schema Registry);
 * aqui, duplicado deliberadamente pelo escopo do projeto.
 */
public record OrderCreatedEvent(
        UUID orderId,
        String customerId,
        String product,
        BigDecimal totalAmount,
        Instant occurredAt
) {
}
