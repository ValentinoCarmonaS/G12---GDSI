package com.easyeat.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record IngredientDeductionDetail(
        UUID ingredientId,
        String ingredientName,
        String unitOfMeasure,
        BigDecimal quantityDebited,
        BigDecimal previousStock,
        BigDecimal newStock,
        BigDecimal criticalThreshold,
        boolean isNowCritical
) {
}
