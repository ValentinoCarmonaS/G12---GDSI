package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO para el transporte de comandas en la cola activa del KDS (estados QUEUED e IN_PREPARATION).
 */
public record KdsActiveQueueTicketResponse(
        UUID ticketId,
        Integer ticketNumber,
        UUID orderId,
        KdsTicketStatus status,
        FulfillmentMode fulfillmentMode,
        LocalDate pickupDate,
        LocalTime timeSlotStart,
        LocalTime timeSlotEnd,
        Instant queuedAt,
        Instant startedAt,
        Integer specificDelayMinutes,
        List<KdsOrderItemResponse> items
) {
    /**
     * Constructor sobrecargado para retrocompatibilidad con suites existentes de pruebas.
     */
    public KdsActiveQueueTicketResponse(
            UUID ticketId,
            Integer ticketNumber,
            UUID orderId,
            KdsTicketStatus status,
            FulfillmentMode fulfillmentMode,
            LocalDate pickupDate,
            LocalTime timeSlotStart,
            LocalTime timeSlotEnd,
            Instant queuedAt,
            Instant startedAt,
            List<KdsOrderItemResponse> items
    ) {
        this(ticketId, ticketNumber, orderId, status, fulfillmentMode, pickupDate, timeSlotStart, timeSlotEnd, queuedAt, startedAt, 0, items);
    }
}
