package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record OrderPaymentStatusResponse(
        UUID orderId,
        OrderStatus orderStatus,
        ReservationSummary reservation,
        PaymentSummary payment,
        FulfillmentSummary fulfillment,
        BigDecimal totalAmount,
        String currency,
        Instant serverTime
) {
    public record ReservationSummary(
            UUID id,
            ReservationStatus status,
            Instant expiresAt,
            Long remainingSeconds,
            ReleaseReason releaseReason
    ) {}

    public record PaymentSummary(
            UUID id,
            PaymentStatus status,
            String statusDetail,
            String redirectUrl,
            boolean sandbox
    ) {}

    public record FulfillmentSummary(
            FulfillmentMode mode,
            LocalDate pickupDate,
            LocalTime timeSlotStart,
            LocalTime timeSlotEnd
    ) {}
}

