package com.easyeat.dish.domain;

/**
 * The three conditions FR-015 requires for publication, named so a failure can say which one is
 * unmet rather than that "the gate failed".
 *
 * <p>Closed and exhaustive: FR-015 declares these three and no others, and all three are
 * evaluated at the instant the approval action is received — no earlier evaluation binds.
 */
public enum PublicationGateCondition {

    /** No valid retail price is set ($PVP > 0$). */
    RETAIL_PRICE_MISSING,

    /**
     * No recipe line references an {@code ACTIVE} ingredient. A line pointing at a
     * {@code DISCONTINUED} ingredient does not count, so a recipe composed entirely of
     * discontinued ingredients fails this condition even though it is not empty.
     */
    EMPTY_RECIPE,

    /** The allergen confirmation flag is not set. The flag is the whole observable. */
    ALLERGENS_UNCONFIRMED
}
