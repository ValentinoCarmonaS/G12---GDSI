package com.easyeat.order.domain;

/**
 * Representa el motivo formal de restitución y liberación de recursos de una reserva temporal.
 */
public enum ReleaseReason {
    REJECTED,
    CANCELLED_BY_USER,
    EXPIRED,
    GATEWAY_ERROR
}

