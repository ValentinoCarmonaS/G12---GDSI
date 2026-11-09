package com.easyeat.order.domain;

/**
 * Representa el estado de una transacción o intento de pago delegado a pasarela.
 */
public enum PaymentStatus {
    IN_PROCESS,
    APPROVED,
    REJECTED,
    CANCELLED,
    FAILED,
    EXPIRED
}

