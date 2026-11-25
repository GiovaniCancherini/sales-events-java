package com.giovanicancherini.inventoryservice.service;

import com.giovanicancherini.inventoryservice.domain.Reservation;
import com.giovanicancherini.inventoryservice.event.OrderCancelledEvent;
import com.giovanicancherini.inventoryservice.event.OrderCreatedEvent;
import com.giovanicancherini.inventoryservice.repository.ProcessedEventRepository;
import com.giovanicancherini.inventoryservice.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Marcar o evento como processado e aplicar o efeito acontecem na mesma transação:
// ou os dois ficam, ou nenhum, e a redelivery do Kafka tenta de novo.
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              ProcessedEventRepository processedEventRepository) {
        this.reservationRepository = reservationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void reserve(OrderCreatedEvent event) {
        if (isDuplicate(event.eventId())) {
            return;
        }
        reservationRepository.save(new Reservation(event.orderId(), event.customerId(), event.product()));
        log.info("Estoque reservado: pedido={} produto={}", event.orderId(), event.product());
    }

    @Transactional
    public void release(OrderCancelledEvent event) {
        if (isDuplicate(event.eventId())) {
            return;
        }
        // Mesma key (customerId), mesma partição: o created sempre chega antes do cancelled.
        reservationRepository.findById(event.orderId()).ifPresentOrElse(
                reservation -> {
                    reservation.release();
                    log.info("Reserva liberada: pedido={}", event.orderId());
                },
                () -> log.warn("Cancelamento sem reserva: pedido={}", event.orderId()));
    }

    private boolean isDuplicate(UUID eventId) {
        boolean duplicate = processedEventRepository.markProcessed(eventId) == 0;
        if (duplicate) {
            log.info("Evento duplicado ignorado: eventId={}", eventId);
        }
        return duplicate;
    }
}
