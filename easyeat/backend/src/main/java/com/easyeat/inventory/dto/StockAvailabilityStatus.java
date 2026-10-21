package com.easyeat.inventory.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StockAvailabilityStatus {
    IN_STOCK("In Stock"),
    OUT_OF_STOCK("Out of Stock");

    private final String displayValue;

    StockAvailabilityStatus(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }
}
