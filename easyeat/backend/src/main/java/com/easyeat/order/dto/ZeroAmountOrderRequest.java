package com.easyeat.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Petición para confirmar directamente un pedido con monto $0 por bonificación al 100%.
 */
public record ZeroAmountOrderRequest(
        @NotNull(message = "El identificador de la orden es obligatorio")
        UUID orderId
) {
}
