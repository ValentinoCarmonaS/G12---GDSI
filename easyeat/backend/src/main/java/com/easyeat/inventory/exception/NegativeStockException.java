package com.easyeat.inventory.exception;

import java.math.BigDecimal;

public class NegativeStockException extends RuntimeException {

    private final BigDecimal currentStock;
    private final BigDecimal attemptedDelta;
    private final BigDecimal resultingStock;

    public NegativeStockException(String message) {
        super(message);
        this.currentStock = null;
        this.attemptedDelta = null;
        this.resultingStock = null;
    }

    public NegativeStockException(BigDecimal currentStock, BigDecimal attemptedDelta, BigDecimal resultingStock) {
        super(String.format("Stock adjustment rejected: current stock %s with delta %s would result in negative stock %s",
                currentStock, attemptedDelta, resultingStock));
        this.currentStock = currentStock;
        this.attemptedDelta = attemptedDelta;
        this.resultingStock = resultingStock;
    }

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getAttemptedDelta() {
        return attemptedDelta;
    }

    public BigDecimal getResultingStock() {
        return resultingStock;
    }
}
