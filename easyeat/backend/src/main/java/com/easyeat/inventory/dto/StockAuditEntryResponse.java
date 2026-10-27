package com.easyeat.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockAuditEntryResponse(
        UUID id,
        UUID ingredientId,
        UUID userId,
        String adminName,
        StockAdjustmentMode adjustmentMode,
        BigDecimal previousStock,
        BigDecimal newStock,
        BigDecimal netDelta,
        UUID batchId,
        BigDecimal unitCost,
        BigDecimal financialLoss,
        String justification,
        Instant createdAt
) {
    public StockAuditEntryResponse(
            UUID id,
            UUID ingredientId,
            UUID userId,
            String adminName,
            StockAdjustmentMode adjustmentMode,
            BigDecimal previousStock,
            BigDecimal newStock,
            BigDecimal netDelta,
            String justification,
            Instant createdAt
    ) {
        this(id, ingredientId, userId, adminName, adjustmentMode, previousStock, newStock, netDelta, null, null, null, justification, createdAt);
    }
}
