package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Consolidated response for green confirmation screen and order summary.
 */
public record OrderConfirmationTrackingResponse(
        UUID orderId,
        Integer ticketNumber,
        OrderStatus status,
        String approvalStatus,
        UUID establishmentId,
        String establishmentName,
        FulfillmentMode fulfillmentMode,
        LocalDate pickupDate,
        LocalTime timeSlotStart,
        LocalTime timeSlotEnd,
        BigDecimal totalAmount,
        List<OrderTrackingItemDto> items,
        String trackingUrl,
        Instant serverTime
) {
    public record OrderTrackingItemDto(
            UUID dishId,
            String dishName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
