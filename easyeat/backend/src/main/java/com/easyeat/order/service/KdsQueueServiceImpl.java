package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsStartPreparationResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.exception.InvalidOrderStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class KdsQueueServiceImpl implements KdsQueueService {

    private static final Logger log = LoggerFactory.getLogger(KdsQueueServiceImpl.class);

    private final KdsTicketRepository kdsTicketRepository;
    private final EstablishmentRepository establishmentRepository;
    private final OrderRepository orderRepository;

    public KdsQueueServiceImpl(
            KdsTicketRepository kdsTicketRepository,
            EstablishmentRepository establishmentRepository,
            OrderRepository orderRepository
    ) {
        this.kdsTicketRepository = kdsTicketRepository;
        this.establishmentRepository = establishmentRepository;
        this.orderRepository = orderRepository;
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

        java.time.LocalTime slotStart = order.getTimeSlotStart();
        java.time.LocalTime slotEnd = order.getTimeSlotEnd();
        if (order.getFulfillmentMode() == com.easyeat.order.domain.FulfillmentMode.IMMEDIATE && slotStart != null) {
            int normalizedMinute = (slotStart.getMinute() / 15) * 15;
            slotStart = java.time.LocalTime.of(slotStart.getHour(), normalizedMinute, 0);
            slotEnd = slotStart.plusMinutes(15);
        }

        KdsTicket ticket = new KdsTicket(
                null,
                order.getId(),
                establishment,
                nextTicketNumber,
                KdsTicketStatus.QUEUED,
                order.getFulfillmentMode(),
                order.getPickupDate(),
                slotStart,
                slotEnd,
                Instant.now()
        );

        KdsTicket savedTicket = kdsTicketRepository.save(ticket);
        log.info("Dispatched order {} to kitchen KDS with ticket number {} and id {}", order.getId(), nextTicketNumber, savedTicket.getId());
        return savedTicket;
    }

    @Override
    @Transactional
    public KdsStartPreparationResponse startTicketPreparation(UUID ticketId, UUID establishmentId) {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(establishmentId, "establishmentId cannot be null");

        // Bloqueo pesimista para concurrencia atómica (RF-003, INV-03)
        KdsTicket ticket = kdsTicketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el ticket de cocina con el identificador proporcionado."));

        if (!ticket.getEstablishment().getId().equals(establishmentId)) {
            throw new TenantViolationException("El ticket no pertenece al establecimiento del usuario autenticado.");
        }

        // Validación de estado terminal (Contrato 01, RF-003)
        if (ticket.getStatus() == KdsTicketStatus.READY_FOR_PICKUP || ticket.getStatus() == KdsTicketStatus.COMPLETED) {
            throw new InvalidOrderStateException("La comanda ya ha finalizado su etapa de preparación activa.");
        }

        // Si ya está en IN_PREPARATION, idempotencia pura: retornar sin alterar startedAt
        if (ticket.getStatus() == KdsTicketStatus.IN_PREPARATION) {
            log.info("Ticket {} already IN_PREPARATION, returning existing state idempotently", ticketId);
            ensureOrderInProgress(ticket.getOrderId());
            return new KdsStartPreparationResponse(
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getOrderId(),
                    ticket.getStatus(),
                    ticket.getStartedAt()
            );
        }

        // Transición de QUEUED a IN_PREPARATION
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        ticket.setStatus(KdsTicketStatus.IN_PREPARATION);
        ticket.setStartedAt(now);
        KdsTicket updatedTicket = kdsTicketRepository.save(ticket);

        ensureOrderInProgress(ticket.getOrderId());

        log.info("Ticket {} transitioned to IN_PREPARATION at {}", ticketId, now);
        return new KdsStartPreparationResponse(
                updatedTicket.getId(),
                updatedTicket.getTicketNumber(),
                updatedTicket.getOrderId(),
                updatedTicket.getStatus(),
                updatedTicket.getStartedAt()
        );
    }

    @Override
    @Transactional
    public com.easyeat.order.dto.KdsDelayIncrementResponse addTicketDelay(UUID ticketId, UUID establishmentId) {
        throw new UnsupportedOperationException("T022: addTicketDelay not implemented yet");
    }

    private void ensureOrderInProgress(UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.IN_PROGRESS) {
                order.setStatus(OrderStatus.IN_PROGRESS);
                orderRepository.save(order);
                log.info("Order {} status ensured to IN_PROGRESS", orderId);
            }
        });
    }
}
