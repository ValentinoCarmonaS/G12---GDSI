package com.easyeat.inventory.repository;

import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.entity.Ingredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    Optional<Ingredient> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Ingredient i WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<Ingredient> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<Ingredient> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("SELECT i FROM Ingredient i WHERE i.tenantId = :tenantId " +
            "AND (:category IS NULL OR i.category = :category) " +
            "AND (:status IS NULL OR i.status = :status) " +
            "ORDER BY i.name ASC")
    List<Ingredient> findAllByTenantIdAndFilters(
            @Param("tenantId") UUID tenantId,
            @Param("category") IngredientCategory category,
            @Param("status") IngredientStatus status);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Ingredient i " +
            "WHERE i.tenantId = :tenantId AND LOWER(i.name) = LOWER(:name) AND i.id <> :excludeId")
    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    @Query("SELECT i FROM Ingredient i WHERE i.tenantId = :tenantId " +
            "AND i.status = com.easyeat.inventory.dto.IngredientStatus.ACTIVE " +
            "AND (:category IS NULL OR i.category = :category) " +
            "AND (CAST(:search AS string) IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Ingredient> findChefCatalog(
            @Param("tenantId") UUID tenantId,
            @Param("category") IngredientCategory category,
            @Param("search") String search,
            Pageable pageable);
}
