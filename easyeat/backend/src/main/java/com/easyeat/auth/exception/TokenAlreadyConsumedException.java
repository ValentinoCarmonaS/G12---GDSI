package com.easyeat.auth.exception;

public class TokenAlreadyConsumedException extends RuntimeException {
    public TokenAlreadyConsumedException(String message) {
        super(message);
    }

    public TokenAlreadyConsumedException() {
        super("This password reset link has already been used.");
    }
}
