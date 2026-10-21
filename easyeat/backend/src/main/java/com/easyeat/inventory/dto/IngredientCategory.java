package com.easyeat.inventory.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IngredientCategory {
    PRODUCE("Produce"),
    MEAT("Meat"),
    DAIRY("Dairy"),
    DRY_GOODS("Dry Goods"),
    SPICES("Spices");

    private final String value;

    IngredientCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static IngredientCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (IngredientCategory category : values()) {
            if (category.value.equalsIgnoreCase(value) || category.name().equalsIgnoreCase(value.replace(" ", "_"))) {
                return category;
            }
        }
        throw new IllegalArgumentException("Invalid ingredient category: " + value);
    }
}
