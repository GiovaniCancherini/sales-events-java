package com.giovanicancherini.inventoryservice.event;

import java.time.Instant;
import java.util.UUID;

public record OrderUpdatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        long totalAmountInCents,
        Instant occurredAt
) {
}
