package com.easyeat.operations.dto;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.operations.service.PaymentMethodService.View;

import java.util.UUID;

/** {@code id} and {@code version} are null for a method that has no row yet, which counts as enabled. */
public record PaymentMethodResponse(
        UUID id,
        PaymentMethod paymentMethod,
        String displayName,
        boolean isEnabled,
        Long version
) {
    public static PaymentMethodResponse from(View view) {
        return new PaymentMethodResponse(view.id(), view.paymentMethod(), view.displayName(), view.enabled(), view.version());
    }
}
