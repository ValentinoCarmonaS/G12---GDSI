package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.RestaurantBranding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RestaurantBrandingRepository extends JpaRepository<RestaurantBranding, UUID> {
}
