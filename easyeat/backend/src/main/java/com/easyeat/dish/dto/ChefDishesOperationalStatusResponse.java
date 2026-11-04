package com.easyeat.dish.dto;

import java.util.List;
import java.util.UUID;

public record ChefDishesOperationalStatusResponse(
        List<ChefDishOperationalStatusItem> items
) {
    public record ChefDishOperationalStatusItem(
            UUID dishId,
            boolean pausedByChef,
            boolean hasStock,
            boolean isActiveAdmin,
            String status
    ) {
    }
}
