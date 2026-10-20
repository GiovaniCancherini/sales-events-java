package com.giovanicancherini.orderservice.domain;

import java.util.UUID;

public class OrderAlreadyCancelledException extends RuntimeException {
    public OrderAlreadyCancelledException(UUID id) {
        super("Pedido já cancelado: " + id);
    }
}
