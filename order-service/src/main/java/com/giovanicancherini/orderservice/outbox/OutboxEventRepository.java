package com.giovanicancherini.orderservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // SKIP LOCKED: com mais de uma instância do serviço, cada relay pega linhas diferentes
    // em vez de esperar o lock do outro ou publicar a mesma linha duas vezes.
    @Query(value = "SELECT * FROM outbox_events WHERE sent_at IS NULL ORDER BY created_at "
            + "LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockPending(int batchSize);
}
