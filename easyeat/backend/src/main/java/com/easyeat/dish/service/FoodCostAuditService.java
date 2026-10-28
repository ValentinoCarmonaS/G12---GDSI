package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.dto.RecipeItemAuditResponse;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class FoodCostAuditService {

    private final DishRepository dishRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final FoodCostEngine foodCostEngine;

    public FoodCostAuditService(
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            FoodCostEngine foodCostEngine
    ) {
        this.dishRepository = dishRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.foodCostEngine = foodCostEngine;
    }

    @Transactional(readOnly = true)
    public FoodCostAuditResponse auditDishCost(UUID dishId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));
        return auditDishCost(dish);
    }

    @Transactional(readOnly = true)
    public FoodCostAuditResponse auditDishCost(Dish dish) {
        UUID dishId = dish.getId();
        List<DishRecipeIngredient> recipeLines = dishRecipeIngredientRepository.findByIdDishId(dishId);
        if (recipeLines.isEmpty()) {
            throw new EmptyRecipeException(dishId);
        }

        List<UUID> ingredientIds = recipeLines.stream()
                .map(DishRecipeIngredient::getIngredientId)
                .toList();

        List<Ingredient> ingredients = ingredientRepository.findAllById(ingredientIds);
        Map<UUID, Ingredient> ingredientMap = new HashMap<>();
        for (Ingredient ing : ingredients) {
            ingredientMap.put(ing.getId(), ing);
        }

        List<FoodCostEngine.IngredientCostItem> calculationItems = new ArrayList<>(recipeLines.size());
        for (DishRecipeIngredient line : recipeLines) {
            Ingredient ing = ingredientMap.get(line.getIngredientId());
            BigDecimal unitPurchaseCost = (ing != null) ? ing.getUnitPurchaseCost() : null;
            calculationItems.add(new FoodCostEngine.IngredientCostItem(line.getQuantity(), unitPurchaseCost));
        }

        FoodCostEngine.CalculationResult calcResult = foodCostEngine.calculate(calculationItems);

        List<RecipeItemAuditResponse> items = new ArrayList<>(recipeLines.size());
        List<String> warningNotes = new ArrayList<>();

        for (int i = 0; i < recipeLines.size(); i++) {
            DishRecipeIngredient line = recipeLines.get(i);
            Ingredient ing = ingredientMap.get(line.getIngredientId());
            boolean isUnpriced = calcResult.unpricedIndices().contains(i);

            String ingredientName = (ing != null && ing.getName() != null) ? ing.getName() : "Ingrediente desconocido";
            com.easyeat.inventory.dto.UnitOfMeasure uom = (ing != null) ? ing.getUnitOfMeasure() : null;
            BigDecimal unitPurchaseCost = (ing != null && ing.getUnitPurchaseCost() != null)
                    ? ing.getUnitPurchaseCost()
                    : BigDecimal.ZERO;
            BigDecimal lineSubtotal = calcResult.lineSubtotals().get(i);

            if (isUnpriced) {
                warningNotes.add("Insumo sin precio de adquisición: " + ingredientName);
            }

            items.add(new RecipeItemAuditResponse(
                    line.getIngredientId(),
                    ingredientName,
                    line.getQuantity(),
                    uom,
                    unitPurchaseCost,
                    lineSubtotal,
                    isUnpriced
            ));
        }

        List<String> dietaryRestrictions = (dish.getRestrictionCodes() != null)
                ? new ArrayList<>(dish.getRestrictionCodes())
                : Collections.emptyList();

        return new FoodCostAuditResponse(
                dish.getId(),
                dish.getName(),
                calcResult.totalCost(),
                calcResult.hasUnpricedIngredients(),
                warningNotes,
                dietaryRestrictions,
                items
        );
    }
}
