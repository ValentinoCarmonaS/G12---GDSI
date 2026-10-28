package com.easyeat.dish.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response returned after successfully updating dish allergens and confirmation state (CHF-2.2.3).
 *
 * @param dishId technical file identifier
 * @param restrictionCodes standardized dietary taxonomy codes currently associated with the dish
 * @param allergensConfirmed whether the chef has explicitly confirmed the allergen section
 */
public record AllergenResponse(
        UUID dishId,
        List<String> restrictionCodes,
        boolean allergensConfirmed
) {
}
