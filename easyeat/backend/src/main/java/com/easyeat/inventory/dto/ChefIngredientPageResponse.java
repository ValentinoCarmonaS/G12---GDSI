package com.easyeat.inventory.dto;

import java.util.List;

public record ChefIngredientPageResponse(
        List<ChefIngredientItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
