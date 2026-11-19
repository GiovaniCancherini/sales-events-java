package com.giovanicancherini.inventoryservice.repository;

import com.giovanicancherini.inventoryservice.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
}
