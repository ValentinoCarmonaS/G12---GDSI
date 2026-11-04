package com.easyeat.inventory.entity;

import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientCriticalStockTest {

    private Ingredient createIngredient(String name, BigDecimal stock, Integer threshold, IngredientStatus status) {
        Ingredient ingredient = new Ingredient(
                UUID.randomUUID(),
                name,
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                BigDecimal.valueOf(10.00)
        );
        ingredient.setCurrentStock(stock);
        ingredient.setCriticalStockThreshold(threshold);
        ingredient.setStatus(status);
        return ingredient;
    }

    @Test
    @DisplayName("INV-1: testIndividualThreshold - verifies individual threshold configuration per ingredient")
    void testIndividualThreshold() {
        Ingredient tomato = createIngredient("Tomato", BigDecimal.valueOf(5), 10, IngredientStatus.ACTIVE);
        Ingredient lettuce = createIngredient("Lettuce", BigDecimal.valueOf(5), 3, IngredientStatus.ACTIVE);

        assertTrue(tomato.isCritical(), "Tomato stock 5 < threshold 10 should be critical");
        assertFalse(lettuce.isCritical(), "Lettuce stock 5 >= threshold 3 should not be critical");
    }

    @Test
    @DisplayName("INV-3: testStrictlyLessThanThreshold - stock == threshold must NOT be critical")
    void testStrictlyLessThanThreshold() {
        Ingredient onion = createIngredient("Onion", BigDecimal.valueOf(10), 10, IngredientStatus.ACTIVE);
        assertFalse(onion.isCritical(), "Stock strictly equal to threshold must be false (safety margin)");
    }

    @Test
    @DisplayName("INV-7: testNullThresholdEmitsNoAlert - null threshold never emits critical alert")
    void testNullThresholdEmitsNoAlert() {
        Ingredient salt = createIngredient("Salt", BigDecimal.ZERO, null, IngredientStatus.ACTIVE);
        assertFalse(salt.isCritical(), "Null threshold must evaluate to false regardless of stock");
    }

    @Test
    @DisplayName("EC-001: testZeroThreshold - threshold 0 is critical only if stock strictly negative (< 0)")
    void testZeroThreshold() {
        Ingredient pepper = createIngredient("Pepper", BigDecimal.ZERO, 0, IngredientStatus.ACTIVE);
        assertFalse(pepper.isCritical(), "Stock 0 == threshold 0 must not be critical");

        Ingredient negativeStock = createIngredient("Pepper Negative", BigDecimal.valueOf(-1), 0, IngredientStatus.ACTIVE);
        assertTrue(negativeStock.isCritical(), "Stock -1 < threshold 0 must be critical");
    }

    @Test
    @DisplayName("EC-004: testZeroStockWithPositiveThreshold - zero stock with positive threshold is critical")
    void testZeroStockWithPositiveThreshold() {
        Ingredient garlic = createIngredient("Garlic", BigDecimal.ZERO, 5, IngredientStatus.ACTIVE);
        assertTrue(garlic.isCritical(), "Stock 0 < threshold 5 must be critical");
    }

    @Test
    @DisplayName("INV-17: testDiscontinuedSuspendsAlert - discontinued status suspends critical alert")
    void testDiscontinuedSuspendsAlert() {
        Ingredient discontinuedCheese = createIngredient("Old Cheese", BigDecimal.valueOf(1), 10, IngredientStatus.DISCONTINUED);
        assertFalse(discontinuedCheese.isCritical(), "Discontinued ingredient must never evaluate to critical");
    }

    @Test
    @DisplayName("EC-009: testIngredientWithoutRecipesAlerts - ingredient without recipes evaluates critical state independently")
    void testIngredientWithoutRecipesAlerts() {
        Ingredient rawItem = createIngredient("Raw Item", BigDecimal.valueOf(2), 8, IngredientStatus.ACTIVE);
        assertTrue(rawItem.isCritical(), "Criticality evaluation does not depend on recipes");
    }

    @Test
    @DisplayName("EC-022: testArbitrarilyHighThresholdAccepted - high threshold is supported without limitation")
    void testArbitrarilyHighThresholdAccepted() {
        Ingredient flour = createIngredient("Flour", BigDecimal.valueOf(500), 100_000, IngredientStatus.ACTIVE);
        assertTrue(flour.isCritical(), "Arbitrarily high threshold is supported and evaluated accurately");
    }
}
