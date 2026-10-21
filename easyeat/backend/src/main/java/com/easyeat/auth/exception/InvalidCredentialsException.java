package com.easyeat.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("The provided email or password is incorrect.");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
