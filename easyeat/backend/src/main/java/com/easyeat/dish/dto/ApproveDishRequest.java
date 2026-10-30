package com.easyeat.dish.dto;

/**
 * The body of {@code POST /admin/dishes/{id}/approval} (FR-017).
 *
 * <p>Both flags default to {@code false} when the field is absent, matching the contract's
 * defaults exactly: an empty body is a legitimate request asking the gate to decide on its own.
 *
 * @param acknowledgeUnpricedIngredients confirms the Administrator knowingly publishes a dish
 *     whose recipe carries an unpriced ingredient.
 * @param acknowledgeNegativeMargin confirms the Administrator knowingly publishes a dish priced
 *     below its Food Cost.
 */
public record ApproveDishRequest(
        boolean acknowledgeUnpricedIngredients,
        boolean acknowledgeNegativeMargin
) {
}
