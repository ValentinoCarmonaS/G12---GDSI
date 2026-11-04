package com.easyeat.dish.dto;

import java.time.Instant;
import java.util.UUID;

public record DishChefPauseResponse(
        UUID dishId,
        boolean pausedByChef,
        Instant updatedAt
) {
    public boolean isPausedByChef() {
        return pausedByChef;
    }
}
