package com.easyeat.dish.dto;

import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /admin/dishes/{id}/rejection} (FR-020, FR-021, FR-042, FR-043).
 *
 * @param comment mandatory feedback, 10 to 1000 characters after trimming, never blank; stored
 *     trimmed and verbatim, markup never interpreted (FR-021).
 * @param proposedChanges the Administrator's proposed edition of the dish (FR-042); culinary-only
 *     payload (title, description, allergen codes, lifestyle codes, proposed ingredient
 *     quantities), serialized to the immutable audit row. Absent for a first rejection that carries
 *     no proposal, in which case nothing is stored.
 */
public record RejectDishRequest(
    @Size(min = 10, max = 1000)
    String comment,
    DishProposedChanges proposedChanges
) {
}
