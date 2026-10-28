package com.easyeat.dish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One retail price change, with the Food Cost that prevailed when it was made (FR-010, FR-034,
 * FR-035).
 *
 * <p>Immutable by the same three mechanisms as {@link DishReviewHistory}: {@code @Immutable} on
 * the mapping, no setters, and {@code updatable = false} on every column. The snapshot is the
 * point of the row — it is what lets the margin of a past decision be reconstructed even after
 * the inventory has moved, which a recomputed margin cannot do (FR-012).
 */
@Entity
@Immutable
@Table(name = "dish_price_history")
public class DishPriceHistory {

    /** FR-008: money is carried at two decimals. */
    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "dish_id", nullable = false, updatable = false)
    private UUID dishId;

    /** Denormalised from the dish, as on the review log, so no audit read can cross tenants. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    /** {@code null} on the first pricing of a dish (FR-010): a fact, not missing data. */
    @Column(name = "previous_price", precision = 10, scale = 2, updatable = false)
    private BigDecimal previousPrice;

    @Column(name = "new_price", precision = 10, scale = 2, nullable = false, updatable = false)
    private BigDecimal newPrice;

    @Column(name = "food_cost_snapshot", precision = 10, scale = 2, nullable = false,
            updatable = false)
    private BigDecimal foodCostSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA requires a no-argument constructor; nothing else may use it. */
    protected DishPriceHistory() {
    }

    private DishPriceHistory(UUID dishId, UUID tenantId, UUID adminId, BigDecimal previousPrice,
                             BigDecimal newPrice, BigDecimal foodCostSnapshot) {
        this.dishId = Objects.requireNonNull(dishId, "dishId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.adminId = Objects.requireNonNull(adminId, "adminId");
        this.previousPrice = previousPrice == null ? null : atMoneyScale(previousPrice);
        this.newPrice = requirePositivePrice(newPrice);
        this.foodCostSnapshot = requireNonNegativeFoodCost(foodCostSnapshot);
        this.createdAt = Instant.now();
    }

    /**
     * Records a price change. {@code previousPrice} is {@code null} when the dish had no price,
     * which is the first-pricing case of FR-010 rather than an omission.
     */
    public static DishPriceHistory record(UUID dishId, UUID tenantId, UUID adminId,
                                          BigDecimal previousPrice, BigDecimal newPrice,
                                          BigDecimal foodCostSnapshot) {
        return new DishPriceHistory(dishId, tenantId, adminId, previousPrice, newPrice,
                foodCostSnapshot);
    }

    private static BigDecimal requirePositivePrice(BigDecimal newPrice) {
        Objects.requireNonNull(newPrice, "newPrice");
        if (newPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A recorded retail price must be strictly positive (FR-013). Received "
                            + newPrice);
        }
        return atMoneyScale(newPrice);
    }

    private static BigDecimal requireNonNegativeFoodCost(BigDecimal foodCostSnapshot) {
        Objects.requireNonNull(foodCostSnapshot, "foodCostSnapshot");
        if (foodCostSnapshot.signum() < 0) {
            throw new IllegalArgumentException(
                    "A Food Cost cannot be negative. 0.00 is legitimate — it is what a recipe "
                            + "whose every ingredient is unpriced costs. Received "
                            + foodCostSnapshot);
        }
        return atMoneyScale(foodCostSnapshot);
    }

    /**
     * Normalises scale without changing value. {@code HALF_UP} appears here only to satisfy the
     * API: every value reaching this entity is already at scale 2 or below — spec 006 rounds each
     * line subtotal, and the calculator produces prices at scale 2 — so no rounding occurs in
     * practice. A value with more than two decimals would be a defect upstream, and normalising
     * it here keeps the stored column honest rather than letting the driver truncate silently.
     */
    private static BigDecimal atMoneyScale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDishId() {
        return dishId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public BigDecimal getFoodCostSnapshot() {
        return foodCostSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DishPriceHistory that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "DishPriceHistory{dishId=" + dishId + ", previousPrice=" + previousPrice
                + ", newPrice=" + newPrice + ", foodCostSnapshot=" + foodCostSnapshot
                + ", createdAt=" + createdAt + '}';
    }
}
