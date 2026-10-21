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
        String justification,
        Instant createdAt
) {}
