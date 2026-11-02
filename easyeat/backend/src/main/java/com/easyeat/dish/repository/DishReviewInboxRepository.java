package com.easyeat.dish.repository;

import com.easyeat.catalog.domain.Dish;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * The three queries behind the segmented review inbox (FR-002, FR-003).
 *
 * <p>A repository owned by spec 007, not an extension of
 * {@code catalog/repository/DishRepository}. Two Spring Data interfaces over one entity are
 * ordinary, and D1's concern is two owners of the <b>entity</b> rather than of a query. The
 * decisive reason is narrower: {@code data-model.md §1.2} rests its review-enforced read-only
 * invariant on that catalog interface gaining no new paths, so that is precisely the file not to
 * widen. Its existing {@code findByTenantIdAndStatus} is left alone rather than reused, because
 * the pending tab needs an ordering it does not impose.
 *
 * <p>Every query is tenant-scoped in its own {@code WHERE}, so a caller that forgets to filter
 * cannot read across establishments. Ordering lives in the query rather than in a caller-supplied
 * {@link Pageable} sort, because FR-003's FIFO is a requirement and not a preference.
 */
public interface DishReviewInboxRepository
        extends org.springframework.data.repository.Repository<Dish, UUID> {

    /**
     * The "Pendientes" view: dishes awaiting a decision, oldest submission first.
     *
     * <p>FIFO is by {@code submittedAt} and by nothing else. The creation-time fallback that
     * earlier drafts offered is withdrawn and not authorised (plan.md R-005-1), because a list
     * ordered by creation looks ordered while no longer being FIFO by submission.
     */
    @Query("""
            SELECT d FROM Dish d
             WHERE d.tenantId = :tenantId
               AND d.status = com.easyeat.dish.domain.DishStatus.PENDING_PRICE
             ORDER BY d.submittedAt ASC
            """)
    Page<Dish> findPendingInbox(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * The "Finalizados" view: dishes that have received a decision.
     */
    @Query("""
            SELECT d FROM Dish d
             WHERE d.tenantId = :tenantId
               AND (
                   d.status = com.easyeat.dish.domain.DishStatus.PUBLISHED
                   OR d.status = com.easyeat.dish.domain.DishStatus.CANCELLED
                   OR (d.status = com.easyeat.dish.domain.DishStatus.DRAFT AND EXISTS (
                       SELECT 1 FROM DishReviewHistory h WHERE h.dishId = d.id AND h.tenantId = :tenantId
                   ))
               )
             ORDER BY d.id DESC
            """)
    Page<Dish> findFinalizedInbox(@Param("tenantId") UUID tenantId, Pageable pageable);

    /** The badge of FR-003, returned on every tab so it never depends on the active view. */
    @Query("""
            SELECT COUNT(d) FROM Dish d
             WHERE d.tenantId = :tenantId
               AND d.status = com.easyeat.dish.domain.DishStatus.PENDING_PRICE
            """)
    long countPending(@Param("tenantId") UUID tenantId);
}
