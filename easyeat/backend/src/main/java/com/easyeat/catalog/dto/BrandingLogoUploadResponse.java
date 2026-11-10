package com.easyeat.catalog.dto;

import java.time.Instant;

/**
 * Response returned after a successful restaurant logo upload.
 */
public record BrandingLogoUploadResponse(
        String logoUrl,
        Instant updatedAt
) {}
