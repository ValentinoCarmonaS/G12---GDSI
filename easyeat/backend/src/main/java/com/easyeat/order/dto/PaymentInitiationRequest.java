package com.easyeat.order.dto;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.FulfillmentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record PaymentInitiationRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        UUID establishmentId,

        @NotNull(message = "La modalidad de retiro es obligatoria")
        FulfillmentMode fulfillmentMode,

        PaymentMethod paymentMethod,

        LocalDate pickupDate,

        LocalTime timeSlotStart,

        LocalTime timeSlotEnd,

        @NotEmpty(message = "El pedido debe contener al menos un plato")
        @Valid
        List<OrderItemRequest> items
) {
    public PaymentInitiationRequest {
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.MERCADO_PAGO;
        }
    }

    public record OrderItemRequest(
            @NotNull(message = "El plato es obligatorio")
            UUID dishId,

            @Min(value = 1, message = "La cantidad debe ser al menos 1")
            int quantity
    ) {}
}

