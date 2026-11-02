package com.easyeat.operations.dto;

import com.easyeat.operations.domain.PaymentMethod;

import java.util.Objects;

/** A missing or unknown method is a 400, see {@link TogglePaymentMethodRequest}. */
public record ValidatePaymentRequest(PaymentMethod paymentMethod) {

    public ValidatePaymentRequest {
        Objects.requireNonNull(paymentMethod, "paymentMethod is required");
    }
}
