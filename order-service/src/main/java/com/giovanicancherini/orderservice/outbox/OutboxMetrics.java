package com.giovanicancherini.orderservice.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;

// O equivalente do lag do consumidor, do lado do produtor: quanto o relay está atrasado.
// A idade da mais antiga pega o relay travado mesmo quando há poucas linhas pendentes.
// ponytail: COUNT a cada exportação sem índice; índice parcial (WHERE sent_at IS NULL) se a tabela crescer.
@Component
class OutboxMetrics {

    OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        Gauge.builder("outbox.events.pending", outboxEventRepository, OutboxEventRepository::countBySentAtIsNull)
                .description("Eventos gravados no outbox e ainda não publicados no Kafka")
                .register(meterRegistry);
        Gauge.builder("outbox.events.oldest.age", outboxEventRepository,
                        repository -> Optional.ofNullable(repository.oldestPendingAgeSeconds()).orElse(0.0))
                .description("Idade do evento pendente mais antigo do outbox")
                .baseUnit("seconds")
                .register(meterRegistry);
    }
}
