package com.easyeat.catalog.controller;

import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.service.BrandingLogoStorageService;
import com.easyeat.catalog.service.RestaurantBrandingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public controller serving establishment visual branding and binary logo to diners (INV-010, CB-011).
 */
@RestController
@RequestMapping("/api/v1/establishments")
public class DinerBrandingController {

    private final RestaurantBrandingService brandingService;
    private final BrandingLogoStorageService logoStorageService;

    public DinerBrandingController(RestaurantBrandingService brandingService) {
        this(brandingService, null);
    }

    @Autowired
    public DinerBrandingController(
            RestaurantBrandingService brandingService,
            @Autowired(required = false) BrandingLogoStorageService logoStorageService
    ) {
        this.brandingService = brandingService;
        this.logoStorageService = logoStorageService;
    }

    @GetMapping("/{establishmentId}/branding")
    public ResponseEntity<RestaurantBrandingResponse> getBranding(@PathVariable UUID establishmentId) {
        RestaurantBrandingResponse response = brandingService.getDinerBranding(establishmentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{establishmentId}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable UUID establishmentId) {
        if (logoStorageService == null) {
            return ResponseEntity.notFound().build();
        }

        return logoStorageService.getLogo(establishmentId)
                .map(logo -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, logo.getContentType() != null ? logo.getContentType() : "image/png")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(logo.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
