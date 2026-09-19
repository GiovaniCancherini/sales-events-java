package com.giovanicancherini.orderservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderUpdatedEvent(
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        Instant occurredAt
) {
}
