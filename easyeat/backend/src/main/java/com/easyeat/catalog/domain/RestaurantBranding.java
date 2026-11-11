package com.easyeat.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Visual identity and branding configurations per establishment.
 */
@Entity
@Table(name = "restaurant_brandings")
public class RestaurantBranding {

    public static final String DEFAULT_PRIMARY_COLOR = "#10B981";
    public static final String DEFAULT_SECONDARY_COLOR = "#065F46";
    public static final String DEFAULT_ACCENT_COLOR = "#F59E0B";
    public static final String DEFAULT_PRIMARY_CONTRAST = "#FFFFFF";
    public static final String DEFAULT_SECONDARY_CONTRAST = "#FFFFFF";
    public static final String DEFAULT_ACCENT_CONTRAST = "#000000";

    @Id
    @Column(name = "establishment_id", nullable = false)
    private UUID establishmentId;

    @Column(name = "fantasy_name", length = 150)
    private String fantasyName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = DEFAULT_PRIMARY_COLOR;

    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor = DEFAULT_SECONDARY_COLOR;

    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor = DEFAULT_ACCENT_COLOR;

    @Column(name = "primary_contrast_color", nullable = false, length = 7)
    private String primaryContrastColor = DEFAULT_PRIMARY_CONTRAST;

    @Column(name = "secondary_contrast_color", nullable = false, length = 7)
    private String secondaryContrastColor = DEFAULT_SECONDARY_CONTRAST;

    @Column(name = "accent_contrast_color", nullable = false, length = 7)
    private String accentContrastColor = DEFAULT_ACCENT_CONTRAST;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RestaurantBranding() {
    }

    public RestaurantBranding(UUID establishmentId, String fantasyName, String description, String logoUrl,
                              String primaryColor, String secondaryColor, String accentColor,
                              String primaryContrastColor, String secondaryContrastColor, String accentContrastColor) {
        this.establishmentId = establishmentId;
        this.fantasyName = fantasyName;
        this.description = description;
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor != null ? primaryColor : DEFAULT_PRIMARY_COLOR;
        this.secondaryColor = secondaryColor != null ? secondaryColor : DEFAULT_SECONDARY_COLOR;
        this.accentColor = accentColor != null ? accentColor : DEFAULT_ACCENT_COLOR;
        this.primaryContrastColor = primaryContrastColor != null ? primaryContrastColor : DEFAULT_PRIMARY_CONTRAST;
        this.secondaryContrastColor = secondaryContrastColor != null ? secondaryContrastColor : DEFAULT_SECONDARY_CONTRAST;
        this.accentContrastColor = accentContrastColor != null ? accentContrastColor : DEFAULT_ACCENT_CONTRAST;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public String getFantasyName() {
        return fantasyName;
    }

    public void setFantasyName(String fantasyName) {
        this.fantasyName = fantasyName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public String getPrimaryContrastColor() {
        return primaryContrastColor;
    }

    public void setPrimaryContrastColor(String primaryContrastColor) {
        this.primaryContrastColor = primaryContrastColor;
    }

    public String getSecondaryContrastColor() {
        return secondaryContrastColor;
    }

    public void setSecondaryContrastColor(String secondaryContrastColor) {
        this.secondaryContrastColor = secondaryContrastColor;
    }

    public String getAccentContrastColor() {
        return accentContrastColor;
    }

    public void setAccentContrastColor(String accentContrastColor) {
        this.accentContrastColor = accentContrastColor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
