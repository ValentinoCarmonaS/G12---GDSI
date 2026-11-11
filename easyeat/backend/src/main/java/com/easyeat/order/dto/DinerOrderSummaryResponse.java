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
 * Resumen de una orden del comensal para la sección "Mis pedidos" y detección de pagos pendientes.
 */
public record DinerOrderSummaryResponse(
        UUID orderId,
        Integer ticketNumber,
        UUID establishmentId,
        String establishmentName,
        OrderStatus status,
        FulfillmentMode fulfillmentMode,
        LocalDate pickupDate,
        LocalTime timeSlotStart,
        LocalTime timeSlotEnd,
        BigDecimal totalAmount,
        Instant expiresAt,
        Instant createdAt,
        String redirectUrl,
        String trackingUrl,
        List<DinerOrderItemResponse> items
) {
    public record DinerOrderItemResponse(
            UUID dishId,
            String dishName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}

