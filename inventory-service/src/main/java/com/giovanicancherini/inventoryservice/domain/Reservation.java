package com.giovanicancherini.inventoryservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Reservation() {
        // exigido pelo JPA
    }

    public Reservation(UUID orderId, String customerId, String product) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.product = product;
        this.status = ReservationStatus.RESERVED;
        this.updatedAt = Instant.now();
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getProduct() {
        return product;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
