package com.easyeat.inventory.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UnitOfMeasure {
    G("g"),
    KG("kg"),
    ML("ml"),
    L("l"),
    U("u");

    private final String value;

    UnitOfMeasure(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UnitOfMeasure fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (UnitOfMeasure unit : values()) {
            if (unit.value.equalsIgnoreCase(value) || unit.name().equalsIgnoreCase(value)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Invalid unit of measure: " + value);
    }
}
