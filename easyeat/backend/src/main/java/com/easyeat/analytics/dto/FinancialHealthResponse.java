package com.easyeat.analytics.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinancialHealthResponse(
        String granularity,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        BigDecimal revenue,
        long orderCount
) {
}
