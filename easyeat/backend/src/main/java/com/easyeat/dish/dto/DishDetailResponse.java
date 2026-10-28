package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DishDetailResponse(
        UUID id,
        UUID tenantId,
        String name,
        DishCategory category,
        String description,
        String photoUrl,
        DishStatus status,
        boolean allergensConfirmed,
        List<String> restrictionCodes,
        List<RecipeIngredientItem> recipe,
        boolean isCompleteForReview,
        List<String> missingConditions,
        UUID createdById,
        Instant createdAt,
        Instant updatedAt
) {
}
