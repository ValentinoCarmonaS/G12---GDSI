package com.easyeat.profile.exception;

public class CooldownRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public CooldownRateLimitException(long retryAfterSeconds) {
        super(String.format("Debe esperar %d segundos antes de solicitar un nuevo reenvío de email de verificación.", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public CooldownRateLimitException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
