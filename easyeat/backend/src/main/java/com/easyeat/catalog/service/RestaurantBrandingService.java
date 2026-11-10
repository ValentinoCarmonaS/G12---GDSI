package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.catalog.repository.RestaurantBrandingRepository;
import com.easyeat.common.exception.TenantViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service managing establishment visual branding, contrast calculations, and neutral fallback
 * for admin and diner views (AS-9, INV-009, INV-010, CB-009, CB-011).
 */
@Service
public class RestaurantBrandingService {

    public static final String FALLBACK_FANTASY_NAME = "Restaurante EasyEat";
    public static final String FALLBACK_DESCRIPTION = "Restaurante asociado a EasyEat";
    public static final String FALLBACK_LOGO_URL = "/assets/brand/easyeat-logo-neutral.svg";
    public static final String DEFAULT_PRIMARY_COLOR = "#10B981";
    public static final String DEFAULT_SECONDARY_COLOR = "#065F46";
    public static final String DEFAULT_ACCENT_COLOR = "#F59E0B";

    private final RestaurantBrandingRepository brandingRepository;
    private final BrandingColorContrastCalculator contrastCalculator;
    private final EstablishmentRepository establishmentRepository;

    public RestaurantBrandingService(
            RestaurantBrandingRepository brandingRepository,
            BrandingColorContrastCalculator contrastCalculator
    ) {
        this(brandingRepository, contrastCalculator, null);
    }

    @Autowired
    public RestaurantBrandingService(
            RestaurantBrandingRepository brandingRepository,
            BrandingColorContrastCalculator contrastCalculator,
            @Autowired(required = false) EstablishmentRepository establishmentRepository
    ) {
        this.brandingRepository = brandingRepository;
        this.contrastCalculator = contrastCalculator;
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional(readOnly = true)
    public RestaurantBrandingResponse getAdminBranding(UUID establishmentId, User adminUser) {
        validateTenantAccess(establishmentId, adminUser);

        return brandingRepository.findById(establishmentId)
                .map(branding -> toResponse(branding, false))
                .orElseGet(() -> createDefaultAdminResponse(establishmentId));
    }

    @Transactional
    public RestaurantBrandingResponse updateBranding(UUID establishmentId, RestaurantBrandingUpdateRequest request, User adminUser) {
        validateTenantAccess(establishmentId, adminUser);

        String primaryContrast = contrastCalculator.calculateOptimalContrast(request.primaryColor());
        String secondaryContrast = contrastCalculator.calculateOptimalContrast(request.secondaryColor());
        String accentContrast = contrastCalculator.calculateOptimalContrast(request.accentColor());

        RestaurantBranding branding = brandingRepository.findById(establishmentId)
                .orElseGet(() -> {
                    RestaurantBranding b = new RestaurantBranding();
                    b.setEstablishmentId(establishmentId);
                    return b;
                });

        branding.setFantasyName(request.fantasyName());
        branding.setDescription(request.description());
        branding.setPrimaryColor(request.primaryColor());
        branding.setSecondaryColor(request.secondaryColor());
        branding.setAccentColor(request.accentColor());
        branding.setPrimaryContrastColor(primaryContrast);
        branding.setSecondaryContrastColor(secondaryContrast);
        branding.setAccentContrastColor(accentContrast);
        branding.setUpdatedAt(Instant.now());

        RestaurantBranding saved = brandingRepository.save(branding);

        if (establishmentRepository != null && request.fantasyName() != null && !request.fantasyName().isBlank()) {
            establishmentRepository.findById(establishmentId).ifPresent(establishment -> {
                establishment.setName(request.fantasyName().trim());
                establishment.setUpdatedAt(Instant.now());
                establishmentRepository.save(establishment);
            });
        }

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public RestaurantBrandingResponse getDinerBranding(UUID establishmentId) {
        return brandingRepository.findById(establishmentId)
                .map(branding -> toResponse(branding, false))
                .orElseGet(() -> createNeutralFallbackResponse(establishmentId));
    }

    private void validateTenantAccess(UUID establishmentId, User adminUser) {
        if (adminUser != null && adminUser.getTenantId() != null && !adminUser.getTenantId().equals(establishmentId)) {
            throw new TenantViolationException(adminUser.getTenantId(), establishmentId);
        }
    }

    private RestaurantBrandingResponse toResponse(RestaurantBranding b, boolean isFallback) {
        return new RestaurantBrandingResponse(
                b.getEstablishmentId(),
                b.getFantasyName(),
                b.getDescription(),
                b.getLogoUrl(),
                b.getPrimaryColor(),
                b.getSecondaryColor(),
                b.getAccentColor(),
                b.getPrimaryContrastColor(),
                b.getSecondaryContrastColor(),
                b.getAccentContrastColor(),
                b.getUpdatedAt() != null ? b.getUpdatedAt() : Instant.now(),
                isFallback
        );
    }

    private RestaurantBrandingResponse createDefaultAdminResponse(UUID establishmentId) {
        String initialName = "";
        if (establishmentRepository != null) {
            initialName = establishmentRepository.findById(establishmentId)
                    .map(est -> est.getName())
                    .orElse("");
        }

        return new RestaurantBrandingResponse(
                establishmentId,
                initialName,
                "",
                null,
                DEFAULT_PRIMARY_COLOR,
                DEFAULT_SECONDARY_COLOR,
                DEFAULT_ACCENT_COLOR,
                contrastCalculator.calculateOptimalContrast(DEFAULT_PRIMARY_COLOR),
                contrastCalculator.calculateOptimalContrast(DEFAULT_SECONDARY_COLOR),
                contrastCalculator.calculateOptimalContrast(DEFAULT_ACCENT_COLOR),
                Instant.now(),
                false
        );
    }

    private RestaurantBrandingResponse createNeutralFallbackResponse(UUID establishmentId) {
        return new RestaurantBrandingResponse(
                establishmentId,
                FALLBACK_FANTASY_NAME,
                FALLBACK_DESCRIPTION,
                FALLBACK_LOGO_URL,
                DEFAULT_PRIMARY_COLOR,
                DEFAULT_SECONDARY_COLOR,
                DEFAULT_ACCENT_COLOR,
                "#FFFFFF",
                "#FFFFFF",
                "#000000",
                Instant.now(),
                true
        );
    }
}
