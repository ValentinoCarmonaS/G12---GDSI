package com.easyeat.profile.exception;

public class EmailVerificationException extends RuntimeException {

    public EmailVerificationException() {
        super("El token de verificación de email es inválido, ha expirado o ya fue utilizado.");
    }

    public EmailVerificationException(String message) {
        super(message);
    }
}
