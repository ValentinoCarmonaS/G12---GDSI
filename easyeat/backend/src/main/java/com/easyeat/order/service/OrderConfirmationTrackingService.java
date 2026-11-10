package com.easyeat.order.service;

import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for assembling consolidated order confirmation and live tracking response.
 */
@Service
public class OrderConfirmationTrackingService {

    public OrderConfirmationTrackingResponse getConfirmationTracking(UUID orderId, UUID currentUserId) {
        throw new UnsupportedOperationException("T011: OrderConfirmationTrackingService.getConfirmationTracking not implemented yet");
    }
}
