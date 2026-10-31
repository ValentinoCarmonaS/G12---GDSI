package com.easyeat.dish.dto;

import com.easyeat.dish.domain.ReviewEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the administrative review timeline (FR-022, SC-005), embedded in {@link
 * DishAuditHistoryResponse}.
 *
 * @param comment {@code null} for an {@code APPROVED} entry, which carries no comment by
 *     construction ({@code DishReviewHistory.approved} takes none).
 */
public record DishReviewHistoryEntryResponse(
        UUID entryId,
        int cycleNumber,
        ReviewEventType eventType,
        String reviewerName,
        String comment,
        Instant createdAt
) {
}
