package com.easyeat.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAdjustmentRequest(
        @NotNull(message = "Adjustment mode is required")
        StockAdjustmentMode mode,

        @NotNull(message = "Adjustment value is required")
        BigDecimal value,

        @NotBlank(message = "Justification note is required")
        @Size(min = 5, max = 500, message = "Justification must be between 5 and 500 characters")
        String justification
) {}
