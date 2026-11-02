package com.easyeat.inventory.entity;

import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingredients")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false, length = 50)
    private IngredientCategory category;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure;

    @Column(name = "unit_purchase_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPurchaseCost;

    /** Total cost of the purchase presentation (package), when the ingredient is acquired packaged. */
    @Column(name = "package_cost", precision = 12, scale = 4)
    private BigDecimal packageCost;

    /** Base units contained in one purchase presentation; defaults to 1. */
    @Column(name = "units_per_package", nullable = false)
    private Integer unitsPerPackage = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IngredientStatus status = IngredientStatus.ACTIVE;

    @Column(name = "current_stock", nullable = false, precision = 12, scale = 4)
    private BigDecimal currentStock = BigDecimal.ZERO.setScale(4);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Ingredient() {}

    public Ingredient(UUID tenantId, String name, IngredientCategory category,
                      UnitOfMeasure unitOfMeasure, BigDecimal unitPurchaseCost) {
        this.tenantId = tenantId;
        this.name = name;
        this.category = category;
        this.unitOfMeasure = unitOfMeasure;
        this.unitPurchaseCost = unitPurchaseCost != null ? unitPurchaseCost.setScale(4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.status = IngredientStatus.ACTIVE;
        this.currentStock = BigDecimal.ZERO.setScale(4);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = IngredientStatus.ACTIVE;
        }
        if (currentStock == null) {
            currentStock = BigDecimal.ZERO.setScale(4);
        } else {
            currentStock = currentStock.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (unitPurchaseCost == null) {
            unitPurchaseCost = BigDecimal.ZERO.setScale(4);
        } else {
            unitPurchaseCost = unitPurchaseCost.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (packageCost != null) {
            packageCost = packageCost.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (unitsPerPackage != null) {
            // No scale needed for integer
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        if (currentStock != null) {
            currentStock = currentStock.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (unitPurchaseCost != null) {
            unitPurchaseCost = unitPurchaseCost.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (packageCost != null) {
            packageCost = packageCost.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (unitsPerPackage == null) {
            unitsPerPackage = 1;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IngredientCategory getCategory() {
        return category;
    }

    public void setCategory(IngredientCategory category) {
        this.category = category;
    }

    public UnitOfMeasure getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(UnitOfMeasure unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public BigDecimal getUnitPurchaseCost() {
        return unitPurchaseCost;
    }

    public void setUnitPurchaseCost(BigDecimal unitPurchaseCost) {
        this.unitPurchaseCost = unitPurchaseCost != null ? unitPurchaseCost.setScale(4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    public BigDecimal getPackageCost() {
        return packageCost;
    }

    public void setPackageCost(BigDecimal packageCost) {
        this.packageCost = packageCost != null ? packageCost.setScale(4, java.math.RoundingMode.HALF_UP) : null;
    }

    public Integer getUnitsPerPackage() {
        return unitsPerPackage != null ? unitsPerPackage : 1;
    }

    public void setUnitsPerPackage(Integer unitsPerPackage) {
        this.unitsPerPackage = unitsPerPackage != null ? unitsPerPackage : 1;
    }

    public IngredientStatus getStatus() {
        return status;
    }

    public void setStatus(IngredientStatus status) {
        this.status = status;
    }

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(BigDecimal currentStock) {
        this.currentStock = currentStock != null ? currentStock.setScale(4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
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
