package com.easyeat.dish.repository;

import com.easyeat.common.http.BinaryMediaMetadataProjection;
import com.easyeat.dish.domain.DishPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DishPhotoRepository extends JpaRepository<DishPhoto, UUID> {

    Optional<DishPhoto> findByDishId(UUID dishId);

    boolean existsByDishId(UUID dishId);

    @Query("SELECT dp.dishId FROM DishPhoto dp WHERE dp.dishId IN :dishIds")
    Set<UUID> findDishIdsWithPhoto(@Param("dishIds") Collection<UUID> dishIds);

    @Query("SELECT dp.fileName AS fileName, dp.contentType AS contentType, dp.fileSize AS fileSize, dp.updatedAt AS updatedAt " +
            "FROM DishPhoto dp WHERE dp.dishId = :dishId")
    Optional<BinaryMediaMetadataProjection> findMetadataByDishId(@Param("dishId") UUID dishId);

    void deleteByDishId(UUID dishId);
}
