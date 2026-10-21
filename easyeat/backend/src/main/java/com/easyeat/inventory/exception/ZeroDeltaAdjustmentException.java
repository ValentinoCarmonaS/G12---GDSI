package com.easyeat.inventory.exception;

import java.util.UUID;

public class ZeroDeltaAdjustmentException extends NegativeStockException {

    public ZeroDeltaAdjustmentException(String message) {
        super(message);
    }

    public ZeroDeltaAdjustmentException(UUID ingredientId) {
        super("Stock adjustment resulted in zero net change for ingredient: " + ingredientId);
    }
}
