package com.easyeat.order.exception;

import java.util.UUID;

public class ActiveReservationExistsException extends RuntimeException {
    private final UUID orderId;

    public ActiveReservationExistsException(String message) {
        this(message, null);
    }

    public ActiveReservationExistsException(String message, UUID orderId) {
        super(message);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
