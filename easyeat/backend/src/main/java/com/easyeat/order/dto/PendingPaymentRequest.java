package com.easyeat.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Petición de reporte de pago pendiente devuelto por la pasarela de pagos.
 */
public record PendingPaymentRequest(
        @NotNull(message = "El identificador de la orden es obligatorio")
        UUID orderId,

        @NotBlank(message = "El identificador de pago de pasarela es obligatorio")
        String gatewayPaymentId,

        String statusDetail
) {
}
