package com.easyeat.inventory.exception;

public class ZeroDeltaAdjustmentException extends NegativeStockException {

    public ZeroDeltaAdjustmentException(String message) {
        super(message);
    }
}
