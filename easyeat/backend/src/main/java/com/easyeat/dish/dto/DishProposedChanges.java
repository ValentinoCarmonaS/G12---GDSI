package com.easyeat.dish.dto;

import java.util.List;

/**
 * The Administrator's proposed edition of a dish, as sent with a rejection (FR-042) and stored in
 * the immutable review history as a JSON snapshot ({@code DishReviewHistory.proposedChangesJson}).
 *
 * <p>Culinary-only payload: identity and composition of the dish the Administrator asks the Chef
 * to cook — title, description, allergen codes, lifestyle codes and the proposed ingredient
 * quantities. Deliberately carries no cost, Food Cost, margin or retail price: those are commercial
 * figures the Chef must never receive, and the Chef may freely edit the proposal before resubmitting
 * (FR-026).
 */
public record DishProposedChanges(
    String title,
    String description,
    List<String> allergenCodes,
    List<String> dietaryLifestyleCodes,
    List<DishProposedIngredient> ingredients
) {
}
