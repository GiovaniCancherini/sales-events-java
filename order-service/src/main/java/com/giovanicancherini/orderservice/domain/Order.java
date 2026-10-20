package com.giovanicancherini.orderservice.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {
        // exigido pelo JPA
    }

    public Order(String customerId, String product, BigDecimal totalAmount) {
        this.customerId = customerId;
        this.product = product;
        this.totalAmount = money(totalAmount);
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markUpdated(BigDecimal newTotalAmount) {
        this.totalAmount = money(newTotalAmount);
        this.status = OrderStatus.UPDATED;
        this.updatedAt = Instant.now();
    }

    // Sem arredondar: a API já recusa mais de 2 casas, então se chegar aqui é bug.
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProduct() {
        return product;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
