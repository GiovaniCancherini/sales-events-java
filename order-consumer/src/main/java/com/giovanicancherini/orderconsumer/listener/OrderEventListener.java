package com.giovanicancherini.orderconsumer.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.giovanicancherini.orderconsumer.event.OrderCreatedEvent;
import com.giovanicancherini.orderconsumer.event.OrderUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    // ObjectMapper próprio, não injetado pelo Spring: evita depender de um bean
    // auto-configurado que, nesta versão do Spring Boot, não é criado por padrão.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @KafkaListener(topics = "${app.kafka.topic.orders}", groupId = "order-consumer")
    public void onMessage(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);

        // O tópico carrega dois tipos de evento. Sem schema registry aqui,
        // a presença do campo "product" distingue criação de atualização.
        if (node.has("product")) {
            OrderCreatedEvent event = objectMapper.treeToValue(node, OrderCreatedEvent.class);
            log.info("Pedido criado: cliente={} produto={} valor={}",
                    event.customerId(), event.product(), event.totalAmount());
        } else {
            OrderUpdatedEvent event = objectMapper.treeToValue(node, OrderUpdatedEvent.class);
            log.info("Pedido atualizado: cliente={} novoValor={}",
                    event.customerId(), event.totalAmount());
        }
    }
}
