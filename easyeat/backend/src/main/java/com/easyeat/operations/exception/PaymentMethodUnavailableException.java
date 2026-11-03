package com.easyeat.operations.exception;

import com.easyeat.operations.domain.PaymentMethod;

/** INV-9 / EC-010: the method was disabled by the restaurant before the order was confirmed. */
public class PaymentMethodUnavailableException extends RuntimeException {

    public PaymentMethodUnavailableException(PaymentMethod paymentMethod) {
        super(String.format("El método de pago '%s' ha sido deshabilitado por el restaurante. Por favor seleccione otra opción.",
                paymentMethod.name()));
    }
}
