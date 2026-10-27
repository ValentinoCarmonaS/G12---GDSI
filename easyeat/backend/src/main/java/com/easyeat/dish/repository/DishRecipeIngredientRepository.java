package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DishRecipeIngredientRepository extends JpaRepository<DishRecipeIngredient, DishRecipeIngredientId> {

    List<DishRecipeIngredient> findByIdDishId(UUID dishId);

    void deleteByIdDishId(UUID dishId);

    void deleteByIdDishIdAndIdIngredientId(UUID dishId, UUID ingredientId);

    Optional<DishRecipeIngredient> findByIdDishIdAndIdIngredientId(UUID dishId, UUID ingredientId);

    boolean existsByIdDishIdAndIdIngredientId(UUID dishId, UUID ingredientId);

    long countByIdDishId(UUID dishId);
}
