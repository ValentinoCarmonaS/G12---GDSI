package com.easyeat.auth.exception;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException() {
        super("This password reset link has expired. Please request a new one.");
    }
}
