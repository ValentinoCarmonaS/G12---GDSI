package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload dinámico de sondeo para seguimiento de pedido en vivo y progreso en cocina (Spec 016 y Spec 021).
 */
public record OrderLiveTrackingResponse(
        UUID orderId,
        Integer ticketNumber,
        OrderStatus status,
        FulfillmentMode fulfillmentMode,
        boolean isCountdownActive,
        Integer estimatedMinutesRemaining,
        Integer progressPercentage,
        boolean isReadyForPickup,
        KdsQueueSaturationDto kdsQueueSaturation,
        String message,
        Instant serverTime,
        // --- Nuevos campos Spec 021 ---
        Integer positionAhead,
        Integer estimatedTotalMinutes,
        Integer elapsedMinutes,
        Integer remainingMinutes,
        Integer progressPercent,
        Integer specificDelayMinutes,
        String securityCode,
        Instant startedAt,
        Instant readyAt,
        List<LiveTrackingItemDto> items
) {
    public record KdsQueueSaturationDto(
            int activeOrdersAhead,
            String kitchenStatus,
            int globalDelayAppliedMinutes
    ) {}

    public record LiveTrackingItemDto(
            String dishName,
            int quantity
    ) {}

    /**
     * Constructor de compatibilidad con llamadas existentes de 11 argumentos.
     */
    public OrderLiveTrackingResponse(
            UUID orderId,
            Integer ticketNumber,
            OrderStatus status,
            FulfillmentMode fulfillmentMode,
            boolean isCountdownActive,
            Integer estimatedMinutesRemaining,
            Integer progressPercentage,
            boolean isReadyForPickup,
            KdsQueueSaturationDto kdsQueueSaturation,
            String message,
            Instant serverTime
    ) {
        this(
                orderId,
                ticketNumber,
                status,
                fulfillmentMode,
                isCountdownActive,
                estimatedMinutesRemaining,
                progressPercentage,
                isReadyForPickup,
                kdsQueueSaturation,
                message,
                serverTime,
                null,
                null,
                null,
                estimatedMinutesRemaining,
                progressPercentage,
                null,
                null,
                null,
                null,
                null
        );
    }
}
