package com.easyeat.dish.exception;

import java.util.UUID;

/**
 * The requested action is illegal from the dish's current state (FR-019, FR-024).
 *
 * <p>Distinct from {@link DishAlreadyDecidedException} on purpose, and the difference is what the
 * Administrator is told. This one means the action was never available — approving a {@code DRAFT},
 * rejecting a {@code PUBLISHED} dish. The other means it <em>was</em> available and someone else
 * took it first. Collapsing them into one 409 would hide which of those happened.
 *
 * <p>Maps to 409 {@code dish-invalid-state}.
 */
public class DishStateConflictException extends RuntimeException {

    private final UUID dishId;
    private final String currentStatus;
    private final String requiredStatus;

    public DishStateConflictException(UUID dishId, String currentStatus, String requiredStatus) {
        super(String.format(
                "La acción solicitada no es válida para el plato con ID %s: su estado actual es "
                        + "'%s' y la operación requiere '%s'.",
                dishId, currentStatus, requiredStatus));
        this.dishId = dishId;
        this.currentStatus = currentStatus;
        this.requiredStatus = requiredStatus;
    }

    public UUID getDishId() {
        return dishId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getRequiredStatus() {
        return requiredStatus;
    }
}
