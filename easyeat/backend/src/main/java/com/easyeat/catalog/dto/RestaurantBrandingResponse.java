package com.easyeat.catalog.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing establishment branding information for admin and diners.
 */
public record RestaurantBrandingResponse(
        UUID establishmentId,
        String fantasyName,
        String description,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String primaryContrastColor,
        String secondaryContrastColor,
        String accentContrastColor,
        Instant updatedAt,
        boolean isDefaultFallback
) {}
