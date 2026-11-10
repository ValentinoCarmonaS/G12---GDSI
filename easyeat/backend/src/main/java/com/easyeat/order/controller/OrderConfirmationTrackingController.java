package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import com.easyeat.order.service.OrderConfirmationTrackingService;
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
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderConfirmationTrackingController {

    private final OrderConfirmationTrackingService trackingService;

    public OrderConfirmationTrackingController(OrderConfirmationTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/{orderId}/confirmation-tracking")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<OrderConfirmationTrackingResponse> getConfirmationTracking(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        OrderConfirmationTrackingResponse response = trackingService.getConfirmationTracking(orderId, userId);
        return ResponseEntity.ok(response);
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
