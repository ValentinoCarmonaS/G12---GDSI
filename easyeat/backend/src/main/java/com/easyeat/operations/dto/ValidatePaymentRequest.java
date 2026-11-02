package com.easyeat.operations.dto;

import com.easyeat.operations.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ValidatePaymentRequest(
        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod
) {
}
