package com.easyeat.order.dto;

import com.easyeat.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code POST /api/v1/counter/orders/{orderId}/complete-delivery} (spec 022, contract 01), identical in
 * shape for the first delivery and for idempotent retries. {@code ticketNumber} is {@code null} for a ready order
 * that has no KDS ticket.
 */
public record CompleteDeliveryResponse(
        UUID orderId,
        Integer ticketNumber,
        OrderStatus status,
        Instant completedAt,
        String message
) {
}
