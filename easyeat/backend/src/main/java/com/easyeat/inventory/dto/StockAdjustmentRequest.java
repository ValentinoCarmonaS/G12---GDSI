package com.easyeat.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record StockAdjustmentRequest(
        StockAdjustmentAction action,
        StockAdjustmentMode mode,
        BigDecimal quantity,
        BigDecimal value,
        BigDecimal unitCost,
        UUID batchId,

        @NotBlank(message = "Justification note is required")
        @Size(min = 5, max = 500, message = "Justification must be between 5 and 500 characters")
        String justification
) {
    public StockAdjustmentRequest(StockAdjustmentMode mode, BigDecimal value, String justification) {
        this(null, mode, null, value, null, null, justification);
    }

    public StockAdjustmentAction resolveAction() {
        if (action != null) {
            return action;
        }
        if (mode != null) {
            if (mode == StockAdjustmentMode.ADD) return StockAdjustmentAction.ADD;
            if (mode == StockAdjustmentMode.SUBTRACT) return StockAdjustmentAction.SUBTRACT;
            if (mode == StockAdjustmentMode.RELATIVE) {
                BigDecimal qty = resolveQuantity();
                return (qty != null && qty.compareTo(BigDecimal.ZERO) < 0)
                        ? StockAdjustmentAction.SUBTRACT
                        : StockAdjustmentAction.ADD;
            }
            if (mode == StockAdjustmentMode.ABSOLUTE) {
                return StockAdjustmentAction.ADD;
            }
        }
        return StockAdjustmentAction.ADD;
    }

    public BigDecimal resolveQuantity() {
        return quantity != null ? quantity : value;
    }
}
