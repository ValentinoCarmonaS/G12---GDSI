package com.easyeat.order.controller;

import com.easyeat.order.dto.KdsDelayAdjustmentRequest;
import com.easyeat.order.dto.KdsDelayAdjustmentResponse;
import com.easyeat.order.dto.KdsTicketResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenTicketDispatchController {

    @PutMapping("/delay")
    @PreAuthorize("hasAnyRole('CHEF', 'ENCARGADO', 'ADMINISTRADOR')")
    public ResponseEntity<KdsDelayAdjustmentResponse> updateKitchenGlobalDelay(
            @Valid @RequestBody KdsDelayAdjustmentRequest request
    ) {
        throw new UnsupportedOperationException("T033: Not implemented yet");
    }

    @PostMapping("/tickets/{ticketId}/ready-for-pickup")
    @PreAuthorize("hasAnyRole('CHEF', 'ENCARGADO')")
    public ResponseEntity<KdsTicketResponse> markTicketReadyForPickup(
            @PathVariable UUID ticketId
    ) {
        throw new UnsupportedOperationException("T033: Not implemented yet");
    }
}
