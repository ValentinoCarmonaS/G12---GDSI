package com.easyeat.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when an order payment is approved.
 * Triggers asynchronous receipt email dispatch and KDS tracking initiation.
 */
public record OrderPaymentApprovedEvent(
        UUID orderId,
        UUID establishmentId,
        UUID userId,
        String recipientEmail,
        BigDecimal totalAmount,
        String trackingUrl,
        Instant occurredAt
) {
    public OrderPaymentApprovedEvent(
            UUID orderId,
            UUID establishmentId,
            UUID userId,
            String recipientEmail,
            BigDecimal totalAmount,
            String trackingUrl
    ) {
        this(orderId, establishmentId, userId, recipientEmail, totalAmount, trackingUrl, Instant.now());
    }
}
