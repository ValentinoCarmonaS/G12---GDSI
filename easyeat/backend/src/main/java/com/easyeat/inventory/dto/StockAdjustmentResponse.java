package com.easyeat.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockAdjustmentResponse(
        UUID ingredientId,
        String ingredientName,
        UnitOfMeasure unitOfMeasure,
        BigDecimal previousStock,
        BigDecimal newStock,
        BigDecimal netDelta,
        UUID batchId,
        BigDecimal unitCost,
        BigDecimal financialLoss,
        UUID auditEntryId,
        Instant adjustedAt
) {
    public StockAdjustmentResponse(
            UUID ingredientId,
            String ingredientName,
            UnitOfMeasure unitOfMeasure,
            BigDecimal previousStock,
            BigDecimal newStock,
            BigDecimal netDelta,
            UUID auditEntryId,
            Instant adjustedAt
    ) {
        this(ingredientId, ingredientName, unitOfMeasure, previousStock, newStock, netDelta, null, null, null, auditEntryId, adjustedAt);
    }
}
