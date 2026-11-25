package com.giovanicancherini.inventoryservice.repository;

import com.giovanicancherini.inventoryservice.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    // 1 = primeira entrega, 0 = o eventId já estava na tabela. Quem decide é a chave primária,
    // então duas entregas simultâneas do mesmo evento não passam as duas.
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now()) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int markProcessed(UUID eventId);
}
