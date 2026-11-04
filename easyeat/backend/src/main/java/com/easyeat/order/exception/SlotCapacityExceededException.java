package com.easyeat.order.exception;

public class SlotCapacityExceededException extends RuntimeException {
    public SlotCapacityExceededException(String message) {
        super(message);
    }
}
