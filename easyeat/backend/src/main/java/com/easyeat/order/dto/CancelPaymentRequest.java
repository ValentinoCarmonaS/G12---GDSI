package com.easyeat.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CancelPaymentRequest(
        @NotNull(message = "El identificador de la orden es obligatorio")
        UUID orderId
) {
}

