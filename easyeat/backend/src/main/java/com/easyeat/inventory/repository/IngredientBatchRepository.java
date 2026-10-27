package com.easyeat.inventory.repository;

import com.easyeat.inventory.entity.IngredientBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientBatchRepository extends JpaRepository<IngredientBatch, UUID> {

    Optional<IngredientBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM IngredientBatch b WHERE b.id = :id AND b.tenantId = :tenantId")
    Optional<IngredientBatch> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<IngredientBatch> findByTenantIdAndIngredientIdOrderByCreatedAtAsc(UUID tenantId, UUID ingredientId);

    List<IngredientBatch> findByTenantIdAndIngredientIdAndRemainingQuantityGreaterThanOrderByCreatedAtAsc(
            UUID tenantId, UUID ingredientId, BigDecimal minQuantity);
}

