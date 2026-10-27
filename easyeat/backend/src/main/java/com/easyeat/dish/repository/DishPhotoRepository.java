package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DishPhotoRepository extends JpaRepository<DishPhoto, UUID> {

    Optional<DishPhoto> findByDishId(UUID dishId);

    boolean existsByDishId(UUID dishId);

    void deleteByDishId(UUID dishId);
}
