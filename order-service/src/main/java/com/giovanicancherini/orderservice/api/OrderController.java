package com.giovanicancherini.orderservice.api;

import com.giovanicancherini.orderservice.domain.Order;
import com.giovanicancherini.orderservice.domain.OrderAlreadyCancelledException;
import com.giovanicancherini.orderservice.service.OrderNotFoundException;
import com.giovanicancherini.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancel(id));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getById(id));
    }

    @GetMapping
    public PagedModel<OrderResponse> list(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(orderService.list(pageable).map(OrderResponse::from));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(OrderNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAlreadyCancelled(OrderAlreadyCancelledException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return new ErrorResponse("O pedido foi alterado por outra requisição. Consulte de novo e repita a alteração.");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidSort(PropertyReferenceException ex) {
        return new ErrorResponse("Campo de ordenação inválido: " + ex.getPropertyName());
    }

    public record ErrorResponse(String message) {
    }
}
