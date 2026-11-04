package com.easyeat.order.domain;

/**
 * Lifecycle status for diner orders.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
