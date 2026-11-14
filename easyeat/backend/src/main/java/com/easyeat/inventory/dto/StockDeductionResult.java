package com.easyeat.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockDeductionResult(
        UUID orderId,
        UUID tenantId,
        Integer totalIngredientsAffected,
        List<IngredientDeductionDetail> deductions,
        Instant executedAt,
        Boolean alreadyProcessed
) {
}
