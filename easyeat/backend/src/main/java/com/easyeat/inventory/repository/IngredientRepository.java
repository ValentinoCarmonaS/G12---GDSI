package com.easyeat.inventory.repository;

import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    Optional<Ingredient> findByIdAndTenantId(UUID id, UUID tenantId);

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
}
