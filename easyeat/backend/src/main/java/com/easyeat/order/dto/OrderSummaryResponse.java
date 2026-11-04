package com.easyeat.order.dto;

import com.easyeat.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        OrderStatus status,
        Instant updatedAt
) {
}
