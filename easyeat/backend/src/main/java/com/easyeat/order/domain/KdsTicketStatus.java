package com.easyeat.order.domain;

/**
 * Operational status for tickets in the Kitchen Display System (KDS).
 */
public enum KdsTicketStatus {
    QUEUED,
    IN_PREPARATION,
    READY_FOR_PICKUP,
    COMPLETED
}
