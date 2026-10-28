package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record DishSummaryResponse(
        UUID id,
        UUID tenantId,
        String name,
        DishCategory category,
        String description,
        String photoUrl,
        DishStatus status,
        boolean allergensConfirmed,
        @JsonProperty("recipeIngredientCount")
        int recipeIngredientCount,
        boolean isCompleteForReview,
        Instant updatedAt,
        Instant createdAt
) {
    @JsonProperty("recipeIngredientsCount")
    public int recipeIngredientsCount() {
        return recipeIngredientCount;
    }
}
