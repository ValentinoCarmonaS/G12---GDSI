package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DishRecipeIngredientRepository extends JpaRepository<DishRecipeIngredient, DishRecipeIngredientId> {

    List<DishRecipeIngredient> findByIdDishId(UUID dishId);

    List<DishRecipeIngredient> findByIdDishIdIn(Collection<UUID> dishIds);

    @Query("""
        SELECT dri.id.dishId AS dishId,
               dri.id.ingredientId AS ingredientId,
               dri.quantity AS quantity,
               i.name AS ingredientName,
               i.status AS ingredientStatus,
               i.currentStock AS currentStock
        FROM DishRecipeIngredient dri
        LEFT JOIN Ingredient i ON i.id = dri.id.ingredientId AND i.tenantId = :tenantId
        WHERE dri.id.dishId IN :dishIds
        ORDER BY dri.id.dishId, dri.id.ingredientId
        """)
    List<RecipeLineWithIngredientProjection> findRecipeLinesWithIngredientsByDishIdsAndTenantId(
            @Param("dishIds") Collection<UUID> dishIds,
            @Param("tenantId") UUID tenantId);

    List<DishRecipeIngredient> findByIdIngredientId(UUID ingredientId);

    void deleteByIdDishId(UUID dishId);

    void deleteByIdDishIdAndIdIngredientId(UUID dishId, UUID ingredientId);

    Optional<DishRecipeIngredient> findByIdDishIdAndIdIngredientId(UUID dishId, UUID ingredientId);

    boolean existsByIdDishIdAndIdIngredientId(UUID dishId, UUID ingredientId);

    long countByIdDishId(UUID dishId);
}
