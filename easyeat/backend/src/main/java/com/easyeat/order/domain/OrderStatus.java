package com.easyeat.order.domain;

/**
 * Lifecycle status for diner orders.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PENDING_ACCREDITATION,
    IN_PROGRESS,
    READY_FOR_PICKUP,
    COMPLETED,
    PAID,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
