package com.easyeat.order.exception;

public class LeadTimeViolationException extends RuntimeException {
    public LeadTimeViolationException(String message) {
        super(message);
    }
}
