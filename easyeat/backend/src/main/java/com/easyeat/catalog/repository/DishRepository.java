package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DishRepository extends JpaRepository<Dish, UUID> {

    List<Dish> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<Dish> findByIdIn(List<UUID> ids);
}
