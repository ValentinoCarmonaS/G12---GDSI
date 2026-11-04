package com.easyeat.order.controller;

import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.service.PickupSlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/establishments")
public class PickupSlotController {

    private final PickupSlotService pickupSlotService;

    public PickupSlotController(PickupSlotService pickupSlotService) {
        this.pickupSlotService = pickupSlotService;
    }

    @GetMapping("/{establishmentId}/pickup-slots")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<PickupSlotCatalogResponse> getPickupSlots(
            @PathVariable UUID establishmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        PickupSlotCatalogResponse response = pickupSlotService.getPickupSlots(establishmentId, date);
        return ResponseEntity.ok(response);
    }
}
