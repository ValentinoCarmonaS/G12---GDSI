package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Dynamic polling payload for real-time order tracking and KDS progress.
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
        Instant serverTime
) {
    public record KdsQueueSaturationDto(
            int activeOrdersAhead,
            String kitchenStatus,
            int globalDelayAppliedMinutes
    ) {}
}
