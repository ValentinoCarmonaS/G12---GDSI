package com.easyeat.inventory.exception;

public class InvalidLifecycleTransitionException extends IllegalStateException {

    public InvalidLifecycleTransitionException(String message) {
        super(message);
    }
}
