package com.easyeat.analytics.dto;

import java.util.UUID;

public record DishRankingItemResponse(
        UUID dishId,
        String dishName,
        long unitsSold
) {
}
