package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishRecipeIngredient;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * The one question the publication gate asks that no existing payload can answer (FR-015,
 * condition 2).
 *
 * <p><b>Why this cannot be read off spec 006's audit.</b> {@code RecipeItemAuditResponse} carries
 * the ingredient's id, name, quantity, unit, cost, subtotal and unpriced flag — but not its
 * {@code IngredientStatus} — and {@code FoodCostAuditService} deliberately includes discontinued
 * lines in its total. The two artifacts answer different questions and both are right: the Food
 * Cost projects what the recipe costs <em>as written</em>, while the gate asks whether the dish can
 * be produced <em>today</em>. Deriving one from the other would corrupt whichever lost.
 *
 * <p>A 007-owned interface rather than a method added to {@code DishRecipeIngredientRepository},
 * which belongs to specs 005 and 006. One cohesive question, in the package of the feature that
 * asks it.
 */
@org.springframework.stereotype.Repository
public interface DishPublicationGateRepository
        extends org.springframework.data.repository.Repository<DishRecipeIngredient, UUID> {

    /**
     * How many of a dish's recipe lines reference an ingredient that is currently {@code ACTIVE}.
     *
     * <p>Zero means condition 2 is unmet, and it covers both shapes of that failure with one
     * number: a dish with no recipe at all, and a dish whose every ingredient has since been
     * discontinued. FR-015 treats them the same — neither can be produced — so the gate does not
     * need to tell them apart. {@code recipeAbsent} on the detail payload is what distinguishes
     * them for the reader.
     */
    @Query("SELECT COUNT(line) FROM DishRecipeIngredient line, Ingredient ing "
            + "WHERE line.id.dishId = :dishId "
            + "AND ing.id = line.id.ingredientId "
            + "AND ing.tenantId = :tenantId "
            + "AND ing.status = com.easyeat.inventory.dto.IngredientStatus.ACTIVE")
    long countActiveRecipeLines(@Param("dishId") UUID dishId, @Param("tenantId") UUID tenantId);
}
