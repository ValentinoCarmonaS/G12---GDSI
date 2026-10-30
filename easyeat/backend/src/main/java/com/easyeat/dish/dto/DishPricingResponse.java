package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishStatus;

import java.util.UUID;

/**
 * The response of {@code PUT /admin/dishes/{id}/retail-price} (FR-009, FR-012, SC-008).
 *
 * @param dishId the priced dish.
 * @param status unchanged by this call (FR-011) — echoed so a repricing of a PUBLISHED dish does
 *     not have to be followed by a second read to confirm nothing else moved.
 * @param margin recomputed from the Food Cost prevailing at this instant, never cached (FR-012).
 * @param priceHistoryEntryId the immutable audit row this call wrote (FR-010, SC-008).
 */
public record DishPricingResponse(
        UUID dishId,
        DishStatus status,
        MarginSummary margin,
        UUID priceHistoryEntryId
) {
}
