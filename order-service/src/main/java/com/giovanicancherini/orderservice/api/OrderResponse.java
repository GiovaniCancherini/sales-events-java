package com.giovanicancherini.orderservice.api;

import com.giovanicancherini.orderservice.Cents;
import com.giovanicancherini.orderservice.domain.Order;
import com.giovanicancherini.orderservice.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        String product,
        long totalAmountInCents,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProduct(),
                Cents.fromAmount(order.getTotalAmount()),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
