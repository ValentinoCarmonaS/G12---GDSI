package com.easyeat.dish.exception;

import java.util.UUID;

public class InvalidIngredientException extends RuntimeException {

    private final UUID ingredientId;
    private final String reason;

    public InvalidIngredientException(UUID ingredientId, String reason) {
        super(String.format("Ingrediente inválido (ID: %s): %s", ingredientId, reason));
        this.ingredientId = ingredientId;
        this.reason = reason;
    }

    public InvalidIngredientException(String message) {
        super(message);
        this.ingredientId = null;
        this.reason = message;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

    public String getReason() {
        return reason;
    }
}
