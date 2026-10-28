package com.easyeat.dish.exception;

import java.util.UUID;

public class DishLockedException extends RuntimeException {

    private final UUID dishId;
    private final String currentStatus;

    public DishLockedException(UUID dishId, String currentStatus) {
        super(String.format("El plato con ID %s se encuentra bloqueado en estado '%s' y no puede ser modificado ni eliminado.", dishId, currentStatus));
        this.dishId = dishId;
        this.currentStatus = currentStatus;
    }

    public DishLockedException(String message) {
        super(message);
        this.dishId = null;
        this.currentStatus = null;
    }

    public UUID getDishId() {
        return dishId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }
}
