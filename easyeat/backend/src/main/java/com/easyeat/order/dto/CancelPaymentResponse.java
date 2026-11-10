package com.easyeat.order.dto;

import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReservationStatus;
import java.util.UUID;

public record CancelPaymentResponse(
        UUID orderId,
        ReservationStatus reservationStatus,
        PaymentStatus paymentStatus,
        String message
) {
}

