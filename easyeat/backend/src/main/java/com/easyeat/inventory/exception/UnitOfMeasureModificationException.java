package com.easyeat.inventory.exception;

public class UnitOfMeasureModificationException extends IllegalStateException {

    public UnitOfMeasureModificationException() {
        super("Cannot modify unit of measure when physical stock is greater than zero.");
    }
}
