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

    /** The "Publicados" view: most recently published first. */
    @Query("""
            SELECT d FROM Dish d
             WHERE d.tenantId = :tenantId
               AND d.status = com.easyeat.dish.domain.DishStatus.PUBLISHED
             ORDER BY d.publishedAt DESC
            """)
    Page<Dish> findPublishedInbox(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * The "Historial / Rechazados" view: dishes with at least one review entry, <b>whatever their
     * current status</b>.
     *
     * <p>Selects by history rather than by status, so it overlaps the other two views on purpose
     * (FR-002). A dish rejected once and later published appears here and in "Publicados" at the
     * same time, and a dish returned to {@code DRAFT} appears only here.
     */
    @Query("""
            SELECT d FROM Dish d
             WHERE d.tenantId = :tenantId
               AND EXISTS (SELECT 1 FROM DishReviewHistory h
                            WHERE h.dishId = d.id AND h.tenantId = :tenantId)
             ORDER BY (SELECT MAX(h2.createdAt) FROM DishReviewHistory h2
                        WHERE h2.dishId = d.id AND h2.tenantId = :tenantId) DESC
            """)
    Page<Dish> findReviewedInbox(@Param("tenantId") UUID tenantId, Pageable pageable);

    /** The badge of FR-003, returned on every tab so it never depends on the active view. */
    @Query("""
            SELECT COUNT(d) FROM Dish d
             WHERE d.tenantId = :tenantId
               AND d.status = com.easyeat.dish.domain.DishStatus.PENDING_PRICE
            """)
    long countPending(@Param("tenantId") UUID tenantId);
}
