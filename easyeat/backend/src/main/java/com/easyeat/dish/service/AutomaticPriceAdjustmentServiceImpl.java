package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishPriceHistory;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.repository.DishPriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of automatic retail price (PVP) adjustments upon ingredient cost variations (C-09).
 */
@Service
public class AutomaticPriceAdjustmentServiceImpl implements AutomaticPriceAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticPriceAdjustmentServiceImpl.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DishRepository dishRepository;
    private final FoodCostAuditService foodCostAuditService;
    private final DishPriceHistoryRepository priceHistoryRepository;

    public AutomaticPriceAdjustmentServiceImpl(
            DishRepository dishRepository,
            FoodCostAuditService foodCostAuditService,
            DishPriceHistoryRepository priceHistoryRepository) {
        this.dishRepository = dishRepository;
        this.foodCostAuditService = foodCostAuditService;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Override
    @Transactional
    public void processIngredientCostChange(
            UUID tenantId,
            UUID ingredientId,
            BigDecimal previousIngredientCost,
            BigDecimal newIngredientCost,
            UUID actorId) {

        if (previousIngredientCost == null || newIngredientCost == null) {
            return;
        }

        // Find all published dishes using this ingredient for the tenant
        List<Dish> publishedDishes = dishRepository.findPublishedDishesByIngredientId(tenantId, ingredientId);
        if (publishedDishes.isEmpty()) {
            return;
        }

        for (Dish dish : publishedDishes) {
            adjustDishPriceIfCostIncreased(dish, ingredientId, previousIngredientCost, newIngredientCost, actorId);
        }
    }

    private void adjustDishPriceIfCostIncreased(
            Dish dish,
            UUID ingredientId,
            BigDecimal previousIngredientCost,
            BigDecimal newIngredientCost,
            UUID actorId) {

        // 1. Audit current Food Cost with the updated ingredient prices
        FoodCostAuditResponse auditResponse = foodCostAuditService.auditDishCost(dish);
        BigDecimal newFoodCost = auditResponse.totalFoodCost();

        // 2. Determine previous Food Cost before this modification
        // We calculate previous Food Cost by using the current retail price and margin_percentage if set,
        // or by subtracting the delta of the ingredient in the recipe.
        BigDecimal marginPercentage = dish.getMarginPercentage();
        BigDecimal currentPvp = dish.getRetailPrice();

        if (currentPvp == null || currentPvp.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // If margin_percentage was not yet set, compute it from current PVP and newFoodCost or prior cost
        if (marginPercentage == null) {
            // fallback: compute margin from current price and audit cost
            marginPercentage = calculateMarginPercentage(currentPvp, newFoodCost);
            dish.setMarginPercentage(marginPercentage);
        }

        // Previous Food Cost deduced from previous price and margin:
        // previousFoodCost = currentPvp * (1 - margin / 100)
        BigDecimal marginMultiplier = BigDecimal.ONE.subtract(marginPercentage.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal previousFoodCost = currentPvp.multiply(marginMultiplier).setScale(2, RoundingMode.HALF_UP);

        // Check if Food Cost actually increased
        if (newFoodCost.compareTo(previousFoodCost) > 0) {
            // Food Cost increased: Recalculate PVP upward preserving original margin_percentage
            // PVP_nuevo = Food_Cost_nuevo / (1 - (margin_percentage / 100))
            if (marginMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid margin multiplier for dish {}: {}", dish.getId(), marginMultiplier);
                return;
            }

            BigDecimal newPvp = newFoodCost.divide(marginMultiplier, 2, RoundingMode.HALF_UP);

            // Update dish retail price
            dish.setRetailPrice(newPvp);
            dishRepository.save(dish);

            // Register audit event in dish_price_history
            DishPriceHistory history = DishPriceHistory.recordAutomaticAdjustment(
                    dish.getId(),
                    dish.getTenantId(),
                    actorId,
                    currentPvp,
                    newPvp,
                    previousFoodCost,
                    newFoodCost,
                    ingredientId,
                    previousIngredientCost,
                    newIngredientCost
            );
            priceHistoryRepository.save(history);

            log.info("Automatic PVP adjustment for dish {} (tenant {}): previous PVP={}, new PVP={}, previous FoodCost={}, new FoodCost={}",
                    dish.getId(), dish.getTenantId(), currentPvp, newPvp, previousFoodCost, newFoodCost);
        } else {
            // Food Cost decreased or unchanged: keep PVP_nuevo = PVP_anterior
            log.info("Ingredient cost decreased/unchanged for dish {} (tenant {}): FoodCost changed from {} to {}, PVP preserved at {}",
                    dish.getId(), dish.getTenantId(), previousFoodCost, newFoodCost, currentPvp);
        }
    }

    private BigDecimal calculateMarginPercentage(BigDecimal retailPrice, BigDecimal foodCost) {
        if (retailPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return retailPrice.subtract(foodCost)
                .divide(retailPrice, 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
