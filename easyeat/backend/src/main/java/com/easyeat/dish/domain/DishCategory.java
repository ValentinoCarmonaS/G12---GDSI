package com.easyeat.dish.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DishCategory {
    ENTRADAS("Entradas"),
    PLATOS_PRINCIPALES("Platos principales"),
    POSTRES("Postres"),
    BEBIDAS("Bebidas"),
    ENSALADAS("Ensaladas");

    private final String value;

    DishCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DishCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        String cleanValue = value.trim();
        for (DishCategory category : values()) {
            if (category.value.equalsIgnoreCase(cleanValue) || category.name().equalsIgnoreCase(cleanValue.replace(" ", "_"))) {
                return category;
            }
        }
        throw new IllegalArgumentException("Invalid dish category: " + value);
    }
}
