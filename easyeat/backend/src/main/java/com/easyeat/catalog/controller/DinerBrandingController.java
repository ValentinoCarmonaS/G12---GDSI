package com.easyeat.catalog.controller;

import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.service.RestaurantBrandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public controller serving establishment visual branding to diners with neutral fallback (INV-010, CB-011).
 */
@RestController
@RequestMapping("/api/v1/establishments")
public class DinerBrandingController {

    private final RestaurantBrandingService brandingService;

    public DinerBrandingController(RestaurantBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping("/{establishmentId}/branding")
    public ResponseEntity<RestaurantBrandingResponse> getBranding(@PathVariable UUID establishmentId) {
        RestaurantBrandingResponse response = brandingService.getDinerBranding(establishmentId);
        return ResponseEntity.ok(response);
    }
}
