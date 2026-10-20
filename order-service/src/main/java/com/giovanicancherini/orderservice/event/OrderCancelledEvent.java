package com.giovanicancherini.orderservice.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        String customerId,
        Instant occurredAt
) {
}
