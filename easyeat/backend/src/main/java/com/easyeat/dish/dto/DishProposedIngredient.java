package com.easyeat.dish.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One proposed ingredient line of an Administrator's proposed edition (FR-042).
 *
 * <p>Culinary-only: the master-catalog identifier the proposal refers to and the quantity the
 * Administrator proposes. No unit purchase cost, Food Cost or margin travels on the proposal; those
 * are derived figures the Chef must never hold.
 */
public record DishProposedIngredient(
    UUID ingredientId,
    BigDecimal quantity
) {
}
