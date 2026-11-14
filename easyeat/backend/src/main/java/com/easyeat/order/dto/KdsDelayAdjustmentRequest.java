package com.easyeat.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record KdsDelayAdjustmentRequest(
        @NotNull UUID establishmentId,
        @Min(0) int globalDelayMinutes
) {}
