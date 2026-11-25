package com.giovanicancherini.orderservice.service;

import com.giovanicancherini.orderservice.api.OrderCreateRequest;
import com.giovanicancherini.orderservice.api.OrderUpdateRequest;
import com.giovanicancherini.orderservice.domain.Order;
import com.giovanicancherini.orderservice.event.OrderCancelledEvent;
import com.giovanicancherini.orderservice.event.OrderCreatedEvent;
import com.giovanicancherini.orderservice.event.OrderEventPublisher;
import com.giovanicancherini.orderservice.event.OrderUpdatedEvent;
import com.giovanicancherini.orderservice.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(OrderCreateRequest request) {
        Order order = new Order(request.customerId(), request.product(), request.totalAmount());
        // Flush antes de publicar: se o banco recusar a escrita, o evento não sai.
        order = orderRepository.saveAndFlush(order);

        eventPublisher.publishCreated(new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getProduct(),
                order.getTotalAmount(),
                Instant.now()
        ));

        return order;
    }

    @Transactional
    public Order update(UUID id, OrderUpdateRequest request) {
        Order order = getById(id);

        order.updateTotalAmount(request.totalAmount());
        // Se outra requisição alterou o pedido no meio, o @Version falha aqui, antes do evento.
        orderRepository.flush();

        eventPublisher.publishUpdated(new OrderUpdatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                Instant.now()
        ));

        return order;
    }

    @Transactional
    public Order cancel(UUID id) {
        Order order = getById(id);

        order.cancel();
        orderRepository.flush();

        eventPublisher.publishCancelled(new OrderCancelledEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                Instant.now()
        ));

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
