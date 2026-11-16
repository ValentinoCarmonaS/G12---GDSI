package com.easyeat.order.exception;

public class InvalidOrderStateException extends RuntimeException {

    private final String code;

    public InvalidOrderStateException(String message) {
        this(message, "INVALID_ORDER_STATE");
    }

    public InvalidOrderStateException(String message, String code) {
        super(message);
        this.code = (code != null && !code.isBlank()) ? code : "INVALID_ORDER_STATE";
    }

    public String getCode() {
        return code;
    }
}
