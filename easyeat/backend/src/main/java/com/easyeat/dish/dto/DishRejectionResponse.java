package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishStatus;

import java.util.UUID;

/**
 * The response of {@code POST /admin/dishes/{id}/rejection} (FR-020, FR-022).
 *
 * @param dishId the rejected dish.
 * @param status always {@code DRAFT} after a successful rejection.
 * @param cycleNumber the review cycle this rejection closed (FR-022's monotonic sequence).
 * @param reviewHistoryEntryId the immutable rejection audit row this call wrote.
 */
public record DishRejectionResponse(
        UUID dishId,
        DishStatus status,
        int cycleNumber,
        UUID reviewHistoryEntryId
) {
}
