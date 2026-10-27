package com.easyeat.inventory.entity;

import com.easyeat.inventory.dto.StockAdjustmentMode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_audit_entries")
public class StockAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_mode", nullable = false, length = 20)
    private StockAdjustmentMode adjustmentMode;

    @Column(name = "previous_stock", nullable = false, precision = 12, scale = 4)
    private BigDecimal previousStock;

    @Column(name = "new_stock", nullable = false, precision = 12, scale = 4)
    private BigDecimal newStock;

    @Column(name = "net_delta", nullable = false, precision = 12, scale = 4)
    private BigDecimal netDelta;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "unit_cost", precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "financial_loss", precision = 12, scale = 4)
    private BigDecimal financialLoss;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockAuditEntry() {}

    public StockAuditEntry(UUID tenantId, UUID ingredientId, UUID userId, StockAdjustmentMode adjustmentMode,
                           BigDecimal previousStock, BigDecimal newStock, BigDecimal netDelta, String justification) {
        this.tenantId = tenantId;
        this.ingredientId = ingredientId;
        this.userId = userId;
        this.adjustmentMode = adjustmentMode;
        this.previousStock = previousStock != null ? previousStock.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.newStock = newStock != null ? newStock.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.netDelta = netDelta != null ? netDelta.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
        this.justification = justification;
        this.createdAt = Instant.now();
    }

    public StockAuditEntry(UUID tenantId, UUID ingredientId, UUID userId, StockAdjustmentMode adjustmentMode,
                           BigDecimal previousStock, BigDecimal newStock, BigDecimal netDelta, String justification,
                           UUID batchId, BigDecimal unitCost, BigDecimal financialLoss) {
        this(tenantId, ingredientId, userId, adjustmentMode, previousStock, newStock, netDelta, justification);
        this.batchId = batchId;
        this.unitCost = unitCost != null ? unitCost.setScale(4, RoundingMode.HALF_UP) : null;
        this.financialLoss = financialLoss != null ? financialLoss.setScale(4, RoundingMode.HALF_UP) : null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (previousStock != null) {
            previousStock = previousStock.setScale(4, RoundingMode.HALF_UP);
        }
        if (newStock != null) {
            newStock = newStock.setScale(4, RoundingMode.HALF_UP);
        }
        if (netDelta != null) {
            netDelta = netDelta.setScale(4, RoundingMode.HALF_UP);
        }
        if (unitCost != null) {
            unitCost = unitCost.setScale(4, RoundingMode.HALF_UP);
        }
        if (financialLoss != null) {
            financialLoss = financialLoss.setScale(4, RoundingMode.HALF_UP);
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public StockAdjustmentMode getAdjustmentMode() {
        return adjustmentMode;
    }

    public void setAdjustmentMode(StockAdjustmentMode adjustmentMode) {
        this.adjustmentMode = adjustmentMode;
    }

    public BigDecimal getPreviousStock() {
        return previousStock;
    }

    public void setPreviousStock(BigDecimal previousStock) {
        this.previousStock = previousStock != null ? previousStock.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    public BigDecimal getNewStock() {
        return newStock;
    }

    public void setNewStock(BigDecimal newStock) {
        this.newStock = newStock != null ? newStock.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    public BigDecimal getNetDelta() {
        return netDelta;
    }

    public void setNetDelta(BigDecimal netDelta) {
        this.netDelta = netDelta != null ? netDelta.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost != null ? unitCost.setScale(4, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getFinancialLoss() {
        return financialLoss;
    }

    public void setFinancialLoss(BigDecimal financialLoss) {
        this.financialLoss = financialLoss != null ? financialLoss.setScale(4, RoundingMode.HALF_UP) : null;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
