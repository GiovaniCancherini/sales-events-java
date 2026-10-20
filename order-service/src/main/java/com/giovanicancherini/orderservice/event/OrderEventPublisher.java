package com.giovanicancherini.orderservice.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${app.kafka.topic.orders}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishCreated(OrderCreatedEvent event) {
        // key = customerId: garante que todos os eventos do mesmo cliente
        // caem na mesma partição e chegam em ordem ao consumidor.
        kafkaTemplate.send(topic, event.customerId(), event);
    }

    public void publishUpdated(OrderUpdatedEvent event) {
        kafkaTemplate.send(topic, event.customerId(), event);
    }

    public void publishCancelled(OrderCancelledEvent event) {
        kafkaTemplate.send(topic, event.customerId(), event);
    }
}
