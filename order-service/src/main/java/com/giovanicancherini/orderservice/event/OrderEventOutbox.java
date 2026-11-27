package com.giovanicancherini.orderservice.event;

import com.giovanicancherini.orderservice.outbox.OutboxEvent;
import com.giovanicancherini.orderservice.outbox.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Grava o evento no outbox, dentro da transação de quem chama: pedido e evento
// são confirmados juntos ou desfeitos juntos. Quem publica no Kafka é o OutboxRelay.
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OrderEventOutbox {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    public OrderEventOutbox(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper,
                            Tracer tracer, Propagator propagator) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    // key = customerId: todos os eventos do mesmo cliente caem na mesma partição, em ordem.
    public void addCreated(OrderCreatedEvent event) {
        save(event.eventId(), "created", event.customerId(), event);
    }

    public void addUpdated(OrderUpdatedEvent event) {
        save(event.eventId(), "updated", event.customerId(), event);
    }

    public void addCancelled(OrderCancelledEvent event) {
        save(event.eventId(), "cancelled", event.customerId(), event);
    }

    private void save(UUID eventId, String type, String customerId, Object event) {
        outboxEventRepository.save(new OutboxEvent(eventId, type, customerId,
                jsonMapper.writeValueAsString(event), currentTraceHeaders()));
    }

    // Os mesmos headers que um cliente HTTP instrumentado mandaria: traceparent e os campos de baggage.
    private String currentTraceHeaders() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        propagator.inject(span.context(), headers, Map::put);
        return jsonMapper.writeValueAsString(headers);
    }
}
