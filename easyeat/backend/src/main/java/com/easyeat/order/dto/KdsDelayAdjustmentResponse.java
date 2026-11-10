package com.easyeat.order.dto;

import java.time.Instant;
import java.util.UUID;

public record KdsDelayAdjustmentResponse(
        UUID establishmentId,
        int globalDelayMinutes,
        Instant updatedAt
) {}
