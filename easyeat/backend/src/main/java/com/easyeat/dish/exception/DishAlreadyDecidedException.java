package com.easyeat.dish.exception;

import java.time.Instant;
import java.util.UUID;

/**
 * Another Administrator decided this dish first (FR-025).
 *
 * <p>Raised when the conditional {@code UPDATE} carrying {@code AND status = 'PENDING_PRICE'}
 * affects zero rows: the dish was pending when it was read and is not any more. It covers both
 * races FR-025 describes — the sequential one, where the panel was simply stale, and the
 * overlapping one, where both transactions read the same state before either committed.
 *
 * <p>Carries {@code currentStatus} and {@code decidedAt} so the interface can say <em>"otro
 * administrador ya decidió este plato"</em> and reconcile its list, rather than reporting a bare
 * conflict the Administrator cannot act on.
 *
 * <p>Maps to 409 {@code dish-already-decided}.
 */
public class DishAlreadyDecidedException extends RuntimeException {

    private final UUID dishId;
    private final String currentStatus;
    private final Instant decidedAt;

    public DishAlreadyDecidedException(UUID dishId, String currentStatus, Instant decidedAt) {
        super(String.format(
                "Otro administrador ya decidió el plato con ID %s: su estado actual es '%s'.",
                dishId, currentStatus));
        this.dishId = dishId;
        this.currentStatus = currentStatus;
        this.decidedAt = decidedAt;
    }

    public UUID getDishId() {
        return dishId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
