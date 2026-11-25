package com.giovanicancherini.orderservice.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// Evento gravado na mesma transação do pedido; o OutboxRelay o publica depois.
// O id é o eventId do payload, então o consumidor deduplica pelo mesmo valor.
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    // Token do header de tipo: created, updated, cancelled (ADR 5)
    @Column(nullable = false)
    private String type;

    // Key da mensagem no Kafka (customerId), que decide a partição
    @Column(nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant sentAt;

    protected OutboxEvent() {
        // exigido pelo JPA
    }

    public OutboxEvent(UUID id, String type, String messageKey, String payload) {
        this.id = id;
        this.type = type;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.sentAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
