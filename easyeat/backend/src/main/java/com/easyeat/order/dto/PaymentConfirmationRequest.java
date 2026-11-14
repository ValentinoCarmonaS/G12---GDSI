package com.easyeat.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Petición de confirmación de pago aprobado tras retorno o notificación.
 */
public record PaymentConfirmationRequest(
        @NotNull(message = "El identificador de la orden es obligatorio")
        UUID orderId,

        @NotBlank(message = "El identificador de pago de pasarela es obligatorio")
        String gatewayPaymentId,

        @NotBlank(message = "El estado del pago es obligatorio")
        String status
) {
}
