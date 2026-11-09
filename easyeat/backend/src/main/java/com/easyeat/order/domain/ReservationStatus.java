package com.easyeat.order.domain;

/**
 * Representa el estado del ciclo de vida de una reserva temporal conjunta de cupo y stock.
 */
public enum ReservationStatus {
    ACTIVE,
    CONFIRMED,
    RELEASED,
    EXPIRED
}

