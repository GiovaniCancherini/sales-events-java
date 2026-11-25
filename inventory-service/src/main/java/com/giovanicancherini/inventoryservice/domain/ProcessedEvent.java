package com.giovanicancherini.inventoryservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// Só declara a tabela: a gravação é o INSERT ... ON CONFLICT do ProcessedEventRepository.
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // exigido pelo JPA
    }
}
