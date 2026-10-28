package com.easyeat.dish.exception;

import java.util.UUID;

public class EmptyRecipeException extends RuntimeException {

    private final UUID dishId;

    public EmptyRecipeException(UUID dishId) {
        super(String.format("El plato con ID %s no tiene una receta técnica definida ni ingredientes asociados.", dishId));
        this.dishId = dishId;
    }

    public UUID getDishId() {
        return dishId;
    }
}

