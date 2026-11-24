package com.easyeat.dish.repository;

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

    void deleteByDishId(UUID dishId);
}
