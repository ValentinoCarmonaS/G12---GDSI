package com.easyeat.auth.exception;

public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException() {
        super("The account has been suspended or is not authorized to operate.");
    }

    public AccountSuspendedException(String message) {
        super(message);
    }
}
