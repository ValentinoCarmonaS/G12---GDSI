package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.repository.KdsTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class KdsQueueServiceImpl implements KdsQueueService {

    private static final Logger log = LoggerFactory.getLogger(KdsQueueServiceImpl.class);

    private final KdsTicketRepository kdsTicketRepository;
    private final EstablishmentRepository establishmentRepository;

    public KdsQueueServiceImpl(KdsTicketRepository kdsTicketRepository, EstablishmentRepository establishmentRepository) {
        this.kdsTicketRepository = kdsTicketRepository;
        this.establishmentRepository = establishmentRepository;
    }

    @Override
    @Transactional
    public KdsTicket dispatchOrderToKitchen(Order order) {
        Objects.requireNonNull(order, "Order cannot be null for KDS dispatch");

        // Idempotency check: if ticket already exists for order, return it (INV-07, CB-12)
        Optional<KdsTicket> existingTicket = kdsTicketRepository.findByOrderId(order.getId());
        if (existingTicket.isPresent()) {
            log.info("KDS ticket already dispatched for order {}, returning existing ticket {}", order.getId(), existingTicket.get().getId());
            return existingTicket.get();
        }

        Establishment establishment = establishmentRepository.findById(order.getEstablishmentId())
                .orElseThrow(() -> new IllegalStateException("Establishment not found: " + order.getEstablishmentId()));

        Integer nextTicketNumber = kdsTicketRepository.findMaxTicketNumberByEstablishmentId(order.getEstablishmentId())
                .map(max -> max + 1)
                .orElse(1);

        KdsTicket ticket = new KdsTicket(
                null,
                order.getId(),
                establishment,
                nextTicketNumber,
                KdsTicketStatus.QUEUED,
                order.getFulfillmentMode(),
                order.getPickupDate(),
                order.getTimeSlotStart(),
                order.getTimeSlotEnd(),
                Instant.now()
        );

        KdsTicket savedTicket = kdsTicketRepository.save(ticket);
        log.info("Dispatched order {} to kitchen KDS with ticket number {} and id {}", order.getId(), nextTicketNumber, savedTicket.getId());
        return savedTicket;
    }
}
