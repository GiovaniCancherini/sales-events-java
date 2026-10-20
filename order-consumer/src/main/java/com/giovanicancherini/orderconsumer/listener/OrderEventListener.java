package com.giovanicancherini.orderconsumer.listener;

import com.giovanicancherini.orderconsumer.event.OrderCancelledEvent;
import com.giovanicancherini.orderconsumer.event.OrderCreatedEvent;
import com.giovanicancherini.orderconsumer.event.OrderUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// O tipo do evento vem num header (token mapeado no application.properties),
// e cada tipo cai no seu @KafkaHandler.
@Component
@KafkaListener(topics = "${app.kafka.topic.orders}")
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @KafkaHandler
    public void onCreated(OrderCreatedEvent event) {
        log.info("Pedido criado: cliente={} produto={} valor={}",
                event.customerId(), event.product(), event.totalAmount());
    }

    @KafkaHandler
    public void onUpdated(OrderUpdatedEvent event) {
        log.info("Pedido atualizado: cliente={} novoValor={}",
                event.customerId(), event.totalAmount());
    }

    @KafkaHandler
    public void onCancelled(OrderCancelledEvent event) {
        log.info("Pedido cancelado: cliente={} pedido={}",
                event.customerId(), event.orderId());
    }
}
