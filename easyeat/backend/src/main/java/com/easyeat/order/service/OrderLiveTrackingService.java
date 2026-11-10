package com.easyeat.order.service;

import com.easyeat.order.dto.OrderLiveTrackingResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for calculating dynamic order live tracking and KDS queue saturation.
 */
@Service
public class OrderLiveTrackingService {

    public OrderLiveTrackingResponse getLiveTracking(UUID orderId, UUID currentUserId) {
        throw new UnsupportedOperationException("T031: Not implemented yet");
    }
}
