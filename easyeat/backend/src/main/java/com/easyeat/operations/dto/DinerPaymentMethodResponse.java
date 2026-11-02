package com.easyeat.operations.dto;

import com.easyeat.operations.domain.PaymentMethod;

/** What the diner sees of a method: never the id, the version or the switch itself (INV-4). */
public record DinerPaymentMethodResponse(PaymentMethod paymentMethod, String displayName) {

    public static DinerPaymentMethodResponse from(PaymentMethod method) {
        return new DinerPaymentMethodResponse(method, method.displayName());
    }
}
