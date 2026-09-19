package com.giovanicancherini.orderservice.repository;

import com.giovanicancherini.orderservice.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
