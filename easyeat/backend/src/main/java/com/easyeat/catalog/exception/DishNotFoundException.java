package com.easyeat.catalog.exception;

import java.util.UUID;

public class DishNotFoundException extends RuntimeException {

    public DishNotFoundException(UUID dishId) {
        super(String.format("Plato no encontrado con ID: %s", dishId));
    }

    public DishNotFoundException(String message) {
        super(message);
    }
}
