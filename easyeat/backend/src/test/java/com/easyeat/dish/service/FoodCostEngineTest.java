package com.easyeat.dish.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FoodCostEngine Unit Tests - Exact Arithmetic & Edge Cases")
class FoodCostEngineTest {

    private final FoodCostEngine engine = new FoodCostEngine();

    @Test
    @DisplayName("Calculates exact total Food Cost rounded HALF_UP to 2 decimal places")
    void shouldCalculateExactFoodCost_withHalfUpRounding() {
        FoodCostEngine.IngredientCostItem item1 = new FoodCostEngine.IngredientCostItem(
                new BigDecimal("0.2500"),
                new BigDecimal("18.9000") // 0.25 * 18.9 = 4.7250 -> 4.73
        );
        FoodCostEngine.IngredientCostItem item2 = new FoodCostEngine.IngredientCostItem(
                new BigDecimal("2.0000"),
                new BigDecimal("1.5000") // 3.00
        );

        FoodCostEngine.CalculationResult result = engine.calculate(List.of(item1, item2));

        assertThat(result.lineSubtotals()).hasSize(2);
        assertThat(result.lineSubtotals().get(0)).isEqualByComparingTo(new BigDecimal("4.73"));
        assertThat(result.lineSubtotals().get(1)).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(result.totalCost()).isEqualByComparingTo(new BigDecimal("7.73"));
        assertThat(result.hasUnpricedIngredients()).isFalse();
        assertThat(result.unpricedIndices()).isEmpty();
    }

    @Test
    @DisplayName("Flags unpriced ingredients when unitPurchaseCost is null, zero, or negative")
    void shouldFlagUnpricedIngredients_whenCostIsZeroOrNull() {
        FoodCostEngine.IngredientCostItem pricedItem = new FoodCostEngine.IngredientCostItem(
                new BigDecimal("1.0000"),
                new BigDecimal("10.0000")
        );
        FoodCostEngine.IngredientCostItem zeroItem = new FoodCostEngine.IngredientCostItem(
                new BigDecimal("0.5000"),
                BigDecimal.ZERO
        );
        FoodCostEngine.IngredientCostItem nullCostItem = new FoodCostEngine.IngredientCostItem(
                new BigDecimal("2.0000"),
                null
        );

        FoodCostEngine.CalculationResult result = engine.calculate(List.of(pricedItem, zeroItem, nullCostItem));

        assertThat(result.lineSubtotals().get(0)).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.lineSubtotals().get(1)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.lineSubtotals().get(2)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totalCost()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.hasUnpricedIngredients()).isTrue();
        assertThat(result.unpricedIndices()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("Handles zero or null quantity by treating line cost as zero")
    void shouldHandleZeroOrNullQuantity() {
        FoodCostEngine.IngredientCostItem nullQty = new FoodCostEngine.IngredientCostItem(
                null,
                new BigDecimal("10.0000")
        );
        FoodCostEngine.IngredientCostItem zeroQty = new FoodCostEngine.IngredientCostItem(
                BigDecimal.ZERO,
                new BigDecimal("10.0000")
        );

        FoodCostEngine.CalculationResult result = engine.calculate(List.of(nullQty, zeroQty));

        assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.hasUnpricedIngredients()).isFalse();
    }

    @Test
    @DisplayName("Throws IllegalArgumentException if item list is empty or null")
    void shouldThrowException_whenItemListIsEmptyOrNull() {
        assertThatThrownBy(() -> engine.calculate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.calculate(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
