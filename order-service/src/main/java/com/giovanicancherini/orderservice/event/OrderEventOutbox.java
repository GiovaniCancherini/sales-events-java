package com.giovanicancherini.orderservice.event;

import com.giovanicancherini.orderservice.outbox.OutboxEvent;
import com.giovanicancherini.orderservice.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// Grava o evento no outbox, dentro da transação de quem chama: pedido e evento
// são confirmados juntos ou desfeitos juntos. Quem publica no Kafka é o OutboxRelay.
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OrderEventOutbox {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public OrderEventOutbox(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
    }

    // key = customerId: todos os eventos do mesmo cliente caem na mesma partição, em ordem.
    public void addCreated(OrderCreatedEvent event) {
        outboxEventRepository.save(new OutboxEvent(event.eventId(), "created", event.customerId(),
                jsonMapper.writeValueAsString(event)));
    }

    public void addUpdated(OrderUpdatedEvent event) {
        outboxEventRepository.save(new OutboxEvent(event.eventId(), "updated", event.customerId(),
                jsonMapper.writeValueAsString(event)));
    }

    public void addCancelled(OrderCancelledEvent event) {
        outboxEventRepository.save(new OutboxEvent(event.eventId(), "cancelled", event.customerId(),
                jsonMapper.writeValueAsString(event)));
    }
}
