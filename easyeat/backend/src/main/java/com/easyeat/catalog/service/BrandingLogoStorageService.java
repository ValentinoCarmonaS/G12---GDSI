package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.domain.RestaurantLogo;
import com.easyeat.catalog.repository.RestaurantBrandingRepository;
import com.easyeat.catalog.repository.RestaurantLogoRepository;
import com.easyeat.common.exception.TenantViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for securely uploading and persisting restaurant logo files (PNG, JPG, WEBP, SVG)
 * in database binary format (BYTEA) matching UserPhoto and DishPhoto architecture (AS-9, INV-009).
 */
@Service
public class BrandingLogoStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/svg+xml"
    );

    private final RestaurantBrandingRepository brandingRepository;
    private final RestaurantLogoRepository logoRepository;

    public BrandingLogoStorageService(
            RestaurantBrandingRepository brandingRepository,
            RestaurantLogoRepository logoRepository
    ) {
        this.brandingRepository = brandingRepository;
        this.logoRepository = logoRepository;
    }

    @Transactional
    public String storeLogo(UUID establishmentId, MultipartFile file, User adminUser) {
        if (adminUser != null && adminUser.getTenantId() != null && !adminUser.getTenantId().equals(establishmentId)) {
            throw new TenantViolationException(adminUser.getTenantId(), establishmentId);
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de logotipo no puede estar vacío");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("El logotipo no puede superar el límite de 2MB");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean validExtension = originalFilename.endsWith(".png")
                || originalFilename.endsWith(".jpg")
                || originalFilename.endsWith(".jpeg")
                || originalFilename.endsWith(".webp")
                || originalFilename.endsWith(".svg");

        if ((contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) && !validExtension) {
            throw new IllegalArgumentException("Formato no soportado. Debe ser PNG, JPG, WEBP o SVG.");
        }

        String extension = ".png";
        if (originalFilename.endsWith(".jpg") || originalFilename.endsWith(".jpeg")) {
            extension = ".jpg";
        } else if (originalFilename.endsWith(".webp")) {
            extension = ".webp";
        } else if (originalFilename.endsWith(".svg")) {
            extension = ".svg";
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Error al leer los datos de la imagen: " + e.getMessage());
        }

        RestaurantLogo logo = logoRepository.findById(establishmentId)
                .orElseGet(() -> {
                    RestaurantLogo l = new RestaurantLogo();
                    l.setEstablishmentId(establishmentId);
                    return l;
                });
        logo.setFileName(originalFilename != null && !originalFilename.isBlank() ? originalFilename : "logo" + extension);
        logo.setContentType(contentType != null ? contentType : "image/png");
        logo.setFileSize(file.getSize());
        logo.setData(bytes);
        logoRepository.save(logo);

        String logoUrl = "/api/v1/establishments/" + establishmentId + "/logo";

        RestaurantBranding branding = brandingRepository.findById(establishmentId)
                .orElseGet(() -> {
                    RestaurantBranding b = new RestaurantBranding();
                    b.setEstablishmentId(establishmentId);
                    return b;
                });
        branding.setLogoUrl(logoUrl);
        branding.setUpdatedAt(Instant.now());
        brandingRepository.save(branding);

        return logoUrl;
    }

    @Transactional(readOnly = true)
    public Optional<RestaurantLogo> getLogo(UUID establishmentId) {
        return logoRepository.findById(establishmentId);
    }
}
