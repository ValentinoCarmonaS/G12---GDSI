package com.easyeat.dish.exception;

import java.math.BigDecimal;

public class InvalidQuantityException extends RuntimeException {

    private final BigDecimal quantity;
    private final String unitOfMeasure;
    private final String reason;

    public InvalidQuantityException(BigDecimal quantity, String unitOfMeasure, String reason) {
        super(String.format("Cantidad inválida '%s' para la unidad de medida '%s': %s", quantity, unitOfMeasure, reason));
        this.quantity = quantity;
        this.unitOfMeasure = unitOfMeasure;
        this.reason = reason;
    }

    public InvalidQuantityException(String message) {
        super(message);
        this.quantity = null;
        this.unitOfMeasure = null;
        this.reason = message;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public String getReason() {
        return reason;
    }
}
