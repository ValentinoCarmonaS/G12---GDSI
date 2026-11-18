package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CompleteDeliveryResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Counter delivery completion (spec 022, US2, ENC-3.3.2; research D-03, contract 01).
 */
@Service
public class CounterOrderService {

    static final String DELIVERED_MESSAGE = "Entrega completada exitosamente.";
    static final String IDEMPOTENT_MESSAGE = "La orden ya se encontraba completada. Respuesta idempotente.";

    private final OrderRepository orderRepository;
    private final KdsTicketRepository kdsTicketRepository;
    private final Clock clock;

    public CounterOrderService(OrderRepository orderRepository, KdsTicketRepository kdsTicketRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.kdsTicketRepository = kdsTicketRepository;
        this.clock = clock;
    }

    /**
     * Moves a READY_FOR_PICKUP order and its KDS ticket to COMPLETED with one shared instant, under a pessimistic
     * row lock so that simultaneous terminals serialize. A COMPLETED order answers idempotently without writing.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CompleteDeliveryResponse completeDelivery(UUID orderId, User actor) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        validateTenant(actor, order);

        Optional<KdsTicket> ticket = kdsTicketRepository.findByOrderId(orderId);
        Integer ticketNumber = ticket.map(KdsTicket::getTicketNumber).orElse(null);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            return response(order, ticketNumber, IDEMPOTENT_MESSAGE);
        }
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new InvalidOrderStateException("No se puede entregar una orden que se encuentra en estado "
                    + order.getStatus() + ". Debe estar en estado READY_FOR_PICKUP.");
        }

        // timestamptz keeps microseconds: truncating keeps the first answer equal to what retries read back.
        Instant deliveredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        order.markDelivered(deliveredAt);
        ticket.ifPresent(t -> {
            t.setStatus(KdsTicketStatus.COMPLETED);
            t.setCompletedAt(deliveredAt);
            kdsTicketRepository.save(t);
        });
        orderRepository.save(order);
        return response(order, ticketNumber, DELIVERED_MESSAGE);
    }

    private void validateTenant(User actor, Order order) {
        if (actor == null || actor.getRole() == UserRole.ADMINISTRADOR) {
            return;
        }
        if (actor.getTenantId() == null || !actor.getTenantId().equals(order.getEstablishmentId())) {
            throw new TenantViolationException("La orden no pertenece al establecimiento del usuario autenticado.");
        }
    }

    private static CompleteDeliveryResponse response(Order order, Integer ticketNumber, String message) {
        return new CompleteDeliveryResponse(order.getId(), ticketNumber, order.getStatus(), order.getCompletedAt(), message);
    }
}
