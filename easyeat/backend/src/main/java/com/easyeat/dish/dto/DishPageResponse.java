package com.easyeat.dish.dto;

import java.util.List;

public record DishPageResponse(
        List<DishSummaryResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {
}
