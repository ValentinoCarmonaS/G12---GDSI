package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishPriceHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only access to the retail price audit trail (FR-010, FR-034, FR-035).
 *
 * <p>Extends the bare {@link org.springframework.data.repository.Repository} marker for the same
 * reason as {@link DishReviewHistoryRepository}: {@code JpaRepository} and {@code CrudRepository}
 * inherit deletion, and FR-034 requires that no modifying operation exist at any surface.
 */
@org.springframework.stereotype.Repository
public interface DishPriceHistoryRepository
        extends org.springframework.data.repository.Repository<DishPriceHistory, UUID> {

    /** The only write. */
    DishPriceHistory save(DishPriceHistory entry);

    /**
     * A dish's full pricing timeline, oldest first (SC-005). The order lives in the query so no
     * caller can present the trail in a different sequence by accident.
     */
    List<DishPriceHistory> findByDishIdAndTenantIdOrderByCreatedAtAsc(UUID dishId, UUID tenantId);

    /**
     * The most recent entry, used to read the price a repricing is replacing. Empty when the dish
     * has never been priced, which is FR-010's first-pricing case and the reason
     * {@code previous_price} is nullable.
     *
     * <p>Note this reads the <em>audit trail</em>, not the dish. The authoritative current price
     * is {@code dishes.retail_price} (FR-012); this answers the narrower question of what the last
     * recorded change said.
     */
    Optional<DishPriceHistory> findFirstByDishIdAndTenantIdOrderByCreatedAtDesc(
            UUID dishId, UUID tenantId);

    /** How many times a dish has been repriced, for the detail view. */
    long countByDishIdAndTenantId(UUID dishId, UUID tenantId);
}
