package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.dish.domain.DishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DishRepository extends JpaRepository<Dish, UUID> {

    /**
     * D4's diner-visibility predicate, spec 007 FR-033/SC-001: a dish reaches the diner catalog
     * only once it is both {@code is_active} and {@code PUBLISHED}. Replaces the pre-007
     * {@code findByTenantIdAndIsActiveTrue}, which predates {@code status} existing on this
     * entity and so let every active dish through regardless of review state. Consolidated
     * 2026-10-31 on integration into {@code develop/sprint-2}: spec 008's
     * {@code DinerCatalogService} and {@code DishAvailabilityEngine} already called this same
     * predicate under this name, so spec 007's independently-written
     * {@code findByTenantIdAndIsActiveTrueAndStatus} was retired in its favour rather than
     * keeping two near-identical derived queries.
     */
    List<Dish> findByTenantIdAndStatusAndIsActiveTrue(UUID tenantId, DishStatus status);

    List<Dish> findByIdIn(List<UUID> ids);

    Page<Dish> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Dish> findByTenantIdAndStatus(UUID tenantId, DishStatus status, Pageable pageable);

    Optional<Dish> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dish d WHERE d.tenantId = :tenantId AND LOWER(TRIM(d.name)) = LOWER(TRIM(:name))")
    boolean existsByTenantIdAndNameLowerTrimmed(@Param("tenantId") UUID tenantId, @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Dish d WHERE d.tenantId = :tenantId AND LOWER(TRIM(d.name)) = LOWER(TRIM(:name)) AND d.id != :id")
    boolean existsByTenantIdAndNameLowerTrimmedAndIdNot(@Param("tenantId") UUID tenantId, @Param("name") String name, @Param("id") UUID id);

    /**
     * The retail price (PVP) write of FR-025, spec 007: a single conditional {@code UPDATE} that
     * restates the price the Administrator read, accepted only when it affects exactly one row.
     *
     * <p>{@code IS NOT DISTINCT FROM} rather than {@code =} so the comparison works when the
     * dish has never been priced: {@code expectedPrice} is {@code null} on the first pricing, and
     * {@code NULL = NULL} is never true in SQL while {@code NULL IS NOT DISTINCT FROM NULL} is.
     * This is the mechanism R-005-4 withdrew an entity version column in favour of — the guard is
     * per operation, on the exact state the Administrator acted on, rather than a row-wide
     * version shared with specs 003 and 005.
     */
    @Modifying
    @Query("UPDATE Dish d SET d.retailPrice = :newPrice WHERE d.id = :dishId AND d.tenantId = :tenantId "
            + "AND d.retailPrice IS NOT DISTINCT FROM :expectedPrice")
    int updateRetailPriceIfUnchanged(@Param("dishId") UUID dishId, @Param("tenantId") UUID tenantId,
                                     @Param("expectedPrice") BigDecimal expectedPrice,
                                     @Param("newPrice") BigDecimal newPrice);

    /**
     * The approval transition of FR-016, FR-025, spec 007: a single conditional {@code UPDATE}
     * restating the source status the Administrator acted on, accepted only when it affects
     * exactly one row. Zero rows means another decision committed first (FR-025's race), not that
     * this one was ever illegal — the status predicate is what tells those two apart.
     *
     * <p>Sets {@code is_active = TRUE} in the same statement, per {@code research.md §3.9}: 005's
     * drafts default it {@code FALSE}, and D4's diner-visibility predicate needs both {@code
     * is_active} and {@code status = PUBLISHED} together, so a dish that only transitioned status
     * would stay invisible and SC-001 would be unprovable. This is the first and only path in
     * this feature that writes {@code is_active}; the Sprint 3 pause feature owns every other one.
     */
    @Modifying
    @Query("UPDATE Dish d SET d.status = com.easyeat.dish.domain.DishStatus.PUBLISHED, "
            + "d.publishedAt = :publishedAt, d.isActive = true "
            + "WHERE d.id = :dishId AND d.tenantId = :tenantId "
            + "AND d.status = com.easyeat.dish.domain.DishStatus.PENDING_PRICE")
    int updateApprovalIfPending(@Param("dishId") UUID dishId, @Param("tenantId") UUID tenantId,
                                @Param("publishedAt") Instant publishedAt);

    /**
     * The rejection transition of FR-020, FR-025, spec 007: the same conditional-`UPDATE` shape
     * as {@link #updateApprovalIfPending}, restating {@code PENDING_PRICE} as the source status.
     * Names only {@code status} — {@code retail_price} is deliberately absent from the `SET`
     * clause, which is how FR-020's "the assigned retail price MUST be preserved" is kept true:
     * there is nothing here that could touch it.
     */
    @Modifying
    @Query("UPDATE Dish d SET d.status = com.easyeat.dish.domain.DishStatus.DRAFT "
            + "WHERE d.id = :dishId AND d.tenantId = :tenantId "
            + "AND d.status = com.easyeat.dish.domain.DishStatus.PENDING_PRICE")
    int updateRejectionIfPending(@Param("dishId") UUID dishId, @Param("tenantId") UUID tenantId);

    @Modifying
    @Query("UPDATE Dish d SET d.status = com.easyeat.dish.domain.DishStatus.CANCELLED, d.isActive = false "
            + "WHERE d.id = :dishId AND d.tenantId = :tenantId "
            + "AND d.status = com.easyeat.dish.domain.DishStatus.PENDING_PRICE")
    int updateCancellationIfPending(@Param("dishId") UUID dishId, @Param("tenantId") UUID tenantId);
}
