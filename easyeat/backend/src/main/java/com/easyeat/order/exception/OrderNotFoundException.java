package com.easyeat.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown when an order is not found or does not belong to the current authenticated diner.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("No existe la orden indicada o no pertenece al usuario autenticado: " + orderId);
        this.orderId = orderId;
    }

    public OrderNotFoundException(String message) {
        super(message);
        this.orderId = null;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
