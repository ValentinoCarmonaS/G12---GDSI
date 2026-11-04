package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID id,
        UUID establishmentId,
        FulfillmentMode fulfillmentMode,
        LocalDate pickupDate,
        LocalTime timeSlotStart,
        LocalTime timeSlotEnd,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant expiresAt,
        Long remainingSeconds,
        Instant serverTime,
        List<OrderItemDetail> items
) {
}
