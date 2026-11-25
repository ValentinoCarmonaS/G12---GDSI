package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.RestaurantLogo;
import com.easyeat.common.http.BinaryMediaMetadataProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantLogoRepository extends JpaRepository<RestaurantLogo, UUID> {

    @Query("SELECT rl.fileName AS fileName, rl.contentType AS contentType, rl.fileSize AS fileSize, rl.updatedAt AS updatedAt " +
            "FROM RestaurantLogo rl WHERE rl.establishmentId = :establishmentId")
    Optional<BinaryMediaMetadataProjection> findMetadataByEstablishmentId(@Param("establishmentId") UUID establishmentId);
}
