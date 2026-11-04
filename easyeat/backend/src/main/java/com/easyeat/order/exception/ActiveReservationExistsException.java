package com.easyeat.order.exception;

public class ActiveReservationExistsException extends RuntimeException {
    public ActiveReservationExistsException(String message) {
        super(message);
    }
}
