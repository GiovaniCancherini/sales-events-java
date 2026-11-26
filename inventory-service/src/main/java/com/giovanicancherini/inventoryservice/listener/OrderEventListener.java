package com.giovanicancherini.inventoryservice.listener;

import com.giovanicancherini.inventoryservice.event.OrderCancelledEvent;
import com.giovanicancherini.inventoryservice.event.OrderCreatedEvent;
import com.giovanicancherini.inventoryservice.event.OrderUpdatedEvent;
import com.giovanicancherini.inventoryservice.service.ReservationService;
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

    private final ReservationService reservationService;

    public OrderEventListener(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @KafkaHandler
    public void onCreated(OrderCreatedEvent event) {
        log.info("Pedido criado: cliente={} produto={} valorEmCentavos={}",
                event.customerId(), event.product(), event.totalAmountInCents());
        reservationService.reserve(event);
    }

    // Mudança de valor não mexe em estoque.
    @KafkaHandler
    public void onUpdated(OrderUpdatedEvent event) {
        log.info("Pedido atualizado: cliente={} novoValorEmCentavos={}",
                event.customerId(), event.totalAmountInCents());
    }

    @KafkaHandler
    public void onCancelled(OrderCancelledEvent event) {
        log.info("Pedido cancelado: cliente={} pedido={}",
                event.customerId(), event.orderId());
        reservationService.release(event);
    }
}
