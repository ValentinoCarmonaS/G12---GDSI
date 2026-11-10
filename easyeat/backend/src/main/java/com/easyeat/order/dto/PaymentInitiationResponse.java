package com.easyeat.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentInitiationResponse(
        UUID orderId,
        UUID reservationId,
        UUID paymentId,
        String gatewayPreferenceId,
        String redirectUrl,
        boolean sandbox,
        Instant expiresAt,
        BigDecimal totalAmount,
        String currency
) {
}

