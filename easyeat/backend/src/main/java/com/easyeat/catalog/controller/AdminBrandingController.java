package com.easyeat.catalog.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.BrandingLogoUploadResponse;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.catalog.service.BrandingLogoStorageService;
import com.easyeat.catalog.service.RestaurantBrandingService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

/**
 * Controller exposing administrative branding endpoints (AS-9, INV-009, CB-009).
 */
@RestController
@RequestMapping("/api/v1/admin/establishments")
public class AdminBrandingController {

    private final RestaurantBrandingService brandingService;
    private final BrandingLogoStorageService logoStorageService;

    public AdminBrandingController(
            RestaurantBrandingService brandingService,
            BrandingLogoStorageService logoStorageService
    ) {
        this.brandingService = brandingService;
        this.logoStorageService = logoStorageService;
    }

    @GetMapping("/{establishmentId}/branding")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<RestaurantBrandingResponse> getBranding(
            @PathVariable UUID establishmentId,
            Authentication authentication
    ) {
        User currentUser = resolveUser(authentication);
        RestaurantBrandingResponse response = brandingService.getAdminBranding(establishmentId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{establishmentId}/branding")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<RestaurantBrandingResponse> updateBranding(
            @PathVariable UUID establishmentId,
            @Valid @RequestBody RestaurantBrandingUpdateRequest request,
            Authentication authentication
    ) {
        User currentUser = resolveUser(authentication);
        RestaurantBrandingResponse response = brandingService.updateBranding(establishmentId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{establishmentId}/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<BrandingLogoUploadResponse> uploadLogo(
            @PathVariable UUID establishmentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User currentUser = resolveUser(authentication);
        String savedLogoUrl = logoStorageService.storeLogo(establishmentId, file, currentUser);
        return ResponseEntity.ok(new BrandingLogoUploadResponse(savedLogoUrl, Instant.now()));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }
}
