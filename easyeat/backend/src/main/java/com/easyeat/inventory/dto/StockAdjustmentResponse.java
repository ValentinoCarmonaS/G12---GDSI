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
        UUID auditEntryId,
        Instant adjustedAt
) {}
