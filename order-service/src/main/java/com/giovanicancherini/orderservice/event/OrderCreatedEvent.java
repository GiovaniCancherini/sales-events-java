package com.giovanicancherini.orderservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato publicado no tópico "orders". Numa arquitetura maior isso viraria
 * um schema versionado (Avro + Schema Registry); aqui é JSON simples,
 * deliberadamente, dado o tamanho do projeto.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        String product,
        long totalAmountInCents,
        Instant occurredAt
) {
}
