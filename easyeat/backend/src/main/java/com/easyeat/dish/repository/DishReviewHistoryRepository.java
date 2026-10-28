package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishReviewHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only access to the administrative review log (FR-034, FR-035).
 *
 * <p>Extends {@link org.springframework.data.repository.Repository}, the bare marker, and
 * <b>deliberately not</b> {@code JpaRepository} or {@code CrudRepository}. Both of those inherit
 * {@code delete}, {@code deleteById} and {@code deleteAll}, which would give this interface a
 * deletion surface no matter what it declared. FR-034 asks that a violation be impossible to
 * express rather than merely detectable, so the operations are never inherited in the first place.
 *
 * <p>The cost of that choice, stated rather than discovered later: every method needed must be
 * declared here by hand, including {@code save}. That is the intended trade — the interface says
 * exactly what it can do.
 */
@org.springframework.stereotype.Repository
public interface DishReviewHistoryRepository
        extends org.springframework.data.repository.Repository<DishReviewHistory, UUID> {

    /** The only write. Appending is all this log supports. */
    DishReviewHistory save(DishReviewHistory entry);

    /**
     * A dish's decision timeline, oldest first. SC-005 orders the audit history by creation
     * timestamp ascending, with {@code cycle_number} as the tie-break; the ordering is applied
     * here so no caller can present it differently by accident.
     */
    List<DishReviewHistory> findByDishIdAndTenantIdOrderByCreatedAtAsc(UUID dishId, UUID tenantId);

    /**
     * The highest cycle number recorded for a dish, for FR-022's {@code MAX + 1}. Returns empty
     * when the dish has never been decided, which is the first cycle's case rather than an error.
     *
     * <p>A read, not a {@code @Modifying} query: the increment is computed by the caller inside
     * the transaction that performs the status transition, so that {@code uq_drh_dish_cycle} can
     * serve as the proof of the invariant.
     */
    @Query("SELECT MAX(h.cycleNumber) FROM DishReviewHistory h WHERE h.dishId = :dishId")
    Optional<Integer> findMaxCycleNumberByDishId(@Param("dishId") UUID dishId);

    /** How many review cycles a dish has been through, for the detail view's cycle count. */
    long countByDishIdAndTenantId(UUID dishId, UUID tenantId);

    /**
     * Whether a dish has ever been decided. This is the predicate behind FR-002's third inbox
     * view, which lists dishes with at least one entry in the review history whatever their
     * current status.
     */
    boolean existsByDishIdAndTenantId(UUID dishId, UUID tenantId);
}
