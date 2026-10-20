package com.giovanicancherini.orderconsumer.listener;

import com.giovanicancherini.orderconsumer.event.OrderCreatedEvent;
import com.giovanicancherini.orderconsumer.event.OrderUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final JsonMapper jsonMapper;

    public OrderEventListener(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${app.kafka.topic.orders}")
    public void onMessage(String payload) {
        JsonNode node = jsonMapper.readTree(payload);

        // O tópico carrega dois tipos de evento. Sem schema registry aqui,
        // a presença do campo "product" distingue criação de atualização.
        if (node.has("product")) {
            OrderCreatedEvent event = jsonMapper.treeToValue(node, OrderCreatedEvent.class);
            log.info("Pedido criado: cliente={} produto={} valor={}",
                    event.customerId(), event.product(), event.totalAmount());
        } else {
            OrderUpdatedEvent event = jsonMapper.treeToValue(node, OrderUpdatedEvent.class);
            log.info("Pedido atualizado: cliente={} novoValor={}",
                    event.customerId(), event.totalAmount());
        }
    }
}
