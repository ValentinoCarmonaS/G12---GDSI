package com.easyeat.order.dto;

import java.util.UUID;

/**
 * Respuesta a la notificación de pago en estado pendiente de acreditación.
 */
public record PendingPaymentResponse(
        UUID orderId,
        String orderStatus,
        String paymentStatus,
        Boolean stockDebited,
        Boolean kdsQueued,
        Boolean reservationFrozen,
        String message
) {
}
