package com.easyeat.order.dto;

import java.util.UUID;

/**
 * Respuesta exitosa a la confirmación de pago aprobado.
 */
public record PaymentConfirmationResponse(
        UUID orderId,
        String orderStatus,
        String paymentStatus,
        Boolean stockDebited,
        KdsTicketSummaryDto kdsTicket,
        Boolean idempotentReplay
) {
}
