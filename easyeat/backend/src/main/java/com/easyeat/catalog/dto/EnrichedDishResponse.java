package com.easyeat.catalog.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnrichedDishResponse {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean isActive;
    private Boolean isCompliant;
    private List<String> conflictingRestrictions = new ArrayList<>();

    public EnrichedDishResponse() {
    }

    public EnrichedDishResponse(UUID id, UUID tenantId, String name, String description, BigDecimal price,
                                Boolean isActive, Boolean isCompliant, List<String> conflictingRestrictions) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.isActive = isActive;
        this.isCompliant = isCompliant;
        this.conflictingRestrictions = conflictingRestrictions != null ? conflictingRestrictions : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Boolean getIsCompliant() {
        return isCompliant;
    }

    public void setIsCompliant(Boolean compliant) {
        isCompliant = compliant;
    }

    public List<String> getConflictingRestrictions() {
        return conflictingRestrictions;
    }

    public void setConflictingRestrictions(List<String> conflictingRestrictions) {
        this.conflictingRestrictions = conflictingRestrictions != null ? conflictingRestrictions : new ArrayList<>();
    }
}
