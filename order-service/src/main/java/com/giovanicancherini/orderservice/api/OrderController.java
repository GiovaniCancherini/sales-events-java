package com.giovanicancherini.orderservice.api;

import com.giovanicancherini.orderservice.domain.Order;
import com.giovanicancherini.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.created(URI.create("/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @PutMapping("/{id}")
    public OrderResponse update(@PathVariable UUID id, @Valid @RequestBody OrderUpdateRequest request) {
        return OrderResponse.from(orderService.update(id, request));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getById(id));
    }

    @GetMapping
    public List<OrderResponse> listAll() {
        return orderService.listAll().stream().map(OrderResponse::from).toList();
    }

    @ExceptionHandler(com.giovanicancherini.orderservice.service.OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(com.giovanicancherini.orderservice.service.OrderNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
