package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.dish.domain.DishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DishRepository extends JpaRepository<Dish, UUID> {

    List<Dish> findByTenantIdAndIsActiveTrue(UUID tenantId);

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
}
