package com.easyeat.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingredient_batches")
public class IngredientBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "initial_quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal initialQuantity;

    @Column(name = "remaining_quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal remainingQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IngredientBatch() {}

    public IngredientBatch(UUID tenantId, UUID ingredientId, BigDecimal initialQuantity, BigDecimal unitCost) {
        this.tenantId = tenantId;
        this.ingredientId = ingredientId;
        this.initialQuantity = initialQuantity != null ? initialQuantity.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.remainingQuantity = this.initialQuantity;
        this.unitCost = unitCost != null ? unitCost.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (initialQuantity != null) {
            initialQuantity = initialQuantity.setScale(4, RoundingMode.HALF_UP);
        }
        if (remainingQuantity != null) {
            remainingQuantity = remainingQuantity.setScale(4, RoundingMode.HALF_UP);
        }
        if (unitCost != null) {
            unitCost = unitCost.setScale(4, RoundingMode.HALF_UP);
        }
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

    public UUID getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(UUID ingredientId) {
        this.ingredientId = ingredientId;
    }

    public BigDecimal getInitialQuantity() {
        return initialQuantity;
    }

    public void setInitialQuantity(BigDecimal initialQuantity) {
        this.initialQuantity = initialQuantity != null ? initialQuantity.setScale(4, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(BigDecimal remainingQuantity) {
        this.remainingQuantity = remainingQuantity != null ? remainingQuantity.setScale(4, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost != null ? unitCost.setScale(4, RoundingMode.HALF_UP) : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
