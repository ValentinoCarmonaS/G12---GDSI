package com.easyeat.auth.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Too many failed login attempts. Please try again in 15 minutes.");
    }

    public RateLimitExceededException(String message) {
        super(message);
    }
}
