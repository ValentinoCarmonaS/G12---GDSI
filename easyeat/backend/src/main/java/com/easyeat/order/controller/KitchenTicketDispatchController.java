package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.dto.KdsDelayAdjustmentRequest;
import com.easyeat.order.dto.KdsDelayAdjustmentResponse;
import com.easyeat.order.dto.KdsTicketResponse;
import com.easyeat.order.service.OrderLiveTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller exposing kitchen ticket dispatch and operational modifiers.
 * Conforms to Contracts 04 and 06 (FR-007, FR-009, AS-7, AS-8, INV-007, INV-008).
 */
@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenTicketDispatchController {

    private final OrderLiveTrackingService liveTrackingService;

    public KitchenTicketDispatchController(OrderLiveTrackingService liveTrackingService) {
        this.liveTrackingService = liveTrackingService;
    }

    /**
     * Updates global kitchen delay for the given establishment.
     */
    @PutMapping("/delay")
    @PreAuthorize("hasAnyRole('CHEF', 'ENCARGADO', 'ADMINISTRADOR')")
    public ResponseEntity<KdsDelayAdjustmentResponse> updateKitchenGlobalDelay(
            @Valid @RequestBody KdsDelayAdjustmentRequest request,
            @AuthenticationPrincipal User user
    ) {
        UUID userId = (user != null) ? user.getId() : null;
        KdsDelayAdjustmentResponse response = liveTrackingService.updateGlobalDelay(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Completes preparation and marks ticket/order as READY_FOR_PICKUP.
     */
    @PostMapping("/tickets/{ticketId}/ready-for-pickup")
    @PreAuthorize("hasAnyRole('CHEF', 'ENCARGADO')")
    public ResponseEntity<KdsTicketResponse> markTicketReadyForPickup(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal User user
    ) {
        UUID userId = (user != null) ? user.getId() : null;
        KdsTicketResponse response = liveTrackingService.markTicketReadyForPickup(ticketId, userId);
        return ResponseEntity.ok(response);
    }
}
