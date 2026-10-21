package com.easyeat.inventory.repository;

import com.easyeat.inventory.entity.StockAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockAuditEntryRepository extends JpaRepository<StockAuditEntry, UUID> {

    List<StockAuditEntry> findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(UUID tenantId, UUID ingredientId);
}
