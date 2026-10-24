package com.easyeat.inventory.exception;

public class DuplicateIngredientNameException extends IllegalStateException {

    private final String name;

    public DuplicateIngredientNameException(String name) {
        super("An ingredient with name '" + name + "' already exists in this establishment.");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
