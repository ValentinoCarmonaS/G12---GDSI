package com.easyeat.dish.exception;

import java.util.UUID;

public class DishNotFoundException extends com.easyeat.catalog.exception.DishNotFoundException {

    private final UUID dishId;

    public DishNotFoundException(UUID dishId) {
        super(dishId);
        this.dishId = dishId;
    }

    public DishNotFoundException(String message) {
        super(message);
        this.dishId = null;
    }

    public UUID getDishId() {
        return dishId;
    }
}
