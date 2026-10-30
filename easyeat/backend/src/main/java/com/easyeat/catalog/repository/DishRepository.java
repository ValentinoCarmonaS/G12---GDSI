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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DishRepository extends JpaRepository<Dish, UUID> {

    List<Dish> findByTenantIdAndIsActiveTrue(UUID tenantId);

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
}
