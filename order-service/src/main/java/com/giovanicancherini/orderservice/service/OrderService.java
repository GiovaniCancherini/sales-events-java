package com.giovanicancherini.orderservice.service;

import com.giovanicancherini.orderservice.Cents;
import com.giovanicancherini.orderservice.api.OrderCreateRequest;
import com.giovanicancherini.orderservice.api.OrderUpdateRequest;
import com.giovanicancherini.orderservice.domain.Order;
import com.giovanicancherini.orderservice.event.OrderCancelledEvent;
import com.giovanicancherini.orderservice.event.OrderCreatedEvent;
import com.giovanicancherini.orderservice.event.OrderEventOutbox;
import com.giovanicancherini.orderservice.event.OrderUpdatedEvent;
import com.giovanicancherini.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// O pedido e o evento no outbox são gravados na mesma transação: se o commit falhar
// (banco fora, conflito de versão), nenhum dos dois fica, e nenhum evento sai.
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderEventOutbox eventOutbox;

    public OrderService(OrderRepository orderRepository, OrderEventOutbox eventOutbox) {
        this.orderRepository = orderRepository;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public Order create(OrderCreateRequest request) {
        Order order = orderRepository.save(
                new Order(request.customerId(), request.product(), Cents.toAmount(request.totalAmountInCents())));

        eventOutbox.addCreated(new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getProduct(),
                Cents.fromAmount(order.getTotalAmount()),
                Instant.now()
        ));

        log.info("Pedido criado: pedido={} cliente={}", order.getId(), order.getCustomerId());
        return order;
    }

    @Transactional
    public Order update(UUID id, OrderUpdateRequest request) {
        Order order = getById(id);

        order.updateTotalAmount(Cents.toAmount(request.totalAmountInCents()));

        eventOutbox.addUpdated(new OrderUpdatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                Cents.fromAmount(order.getTotalAmount()),
                Instant.now()
        ));

        log.info("Pedido atualizado: pedido={} cliente={}", order.getId(), order.getCustomerId());
        return order;
    }

    @Transactional
    public Order cancel(UUID id) {
        Order order = getById(id);

        order.cancel();

        eventOutbox.addCancelled(new OrderCancelledEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                Instant.now()
        ));

        log.info("Pedido cancelado: pedido={} cliente={}", order.getId(), order.getCustomerId());
        return order;
    }

    public Order getById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Page<Order> list(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
