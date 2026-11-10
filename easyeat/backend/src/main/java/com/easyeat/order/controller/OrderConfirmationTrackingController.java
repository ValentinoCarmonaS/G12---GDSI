package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.service.OrderConfirmationTrackingService;
import com.easyeat.order.service.OrderLiveTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing payment confirmation and tracking endpoints.
 * Conforms to Contract 01 (AS-1, AS-3, FR-001, FR-003, INV-001, INV-004)
 * and Contracts 04/05/06 (FR-005, FR-006, AS-5, AS-6, INV-004).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderConfirmationTrackingController {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationTrackingController.class);

    private final OrderConfirmationTrackingService trackingService;
    private final OrderLiveTrackingService liveTrackingService;

    public OrderConfirmationTrackingController(
            OrderConfirmationTrackingService trackingService,
            OrderLiveTrackingService liveTrackingService
    ) {
        this.trackingService = trackingService;
        this.liveTrackingService = liveTrackingService;
    }

    /**
     * Retrieves consolidated payment confirmation and tracking details.
     */
    @GetMapping("/{orderId}/confirmation-tracking")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<OrderConfirmationTrackingResponse> getConfirmationTracking(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        log.info("Fetching confirmation tracking for orderId={} by userId={}", orderId, userId);
        OrderConfirmationTrackingResponse response = trackingService.getConfirmationTracking(orderId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves dynamic live tracking info for real-time progress, dynamic minutes and KDS queue saturation.
     * Conforms to Contracts 04, 05, and 06 with no-cache headers.
     */
    @GetMapping("/{orderId}/live-tracking")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<OrderLiveTrackingResponse> getLiveTracking(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        log.info("Fetching live tracking for orderId={} by userId={}", orderId, userId);
        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    private UUID resolveUserId(User user, Authentication authentication) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User u) {
                return u.getId();
            }
            if (principal instanceof UUID uid) {
                return uid;
            }
            if (principal instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new IllegalStateException("Usuario no autenticado");
    }
}
