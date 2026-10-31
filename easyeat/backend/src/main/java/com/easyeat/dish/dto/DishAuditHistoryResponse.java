package com.easyeat.dish.dto;

import java.util.List;
import java.util.UUID;

/**
 * The response of {@code GET /admin/dishes/{id}/review-history} (FR-010, FR-022, SC-005): both
 * audit trails, each ordered by creation timestamp ascending.
 *
 * <p>The two lists are not interleaved into one here; SC-005's "a combined view MUST interleave
 * them" describes what the reader does with the two ordered sets, not a third merged list this
 * record would otherwise have to keep consistent with the two it already carries.
 */
public record DishAuditHistoryResponse(
        UUID dishId,
        List<DishReviewHistoryEntryResponse> reviewEvents,
        List<DishPriceHistoryEntryResponse> priceChanges
) {
}
