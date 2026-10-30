package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The response of {@code POST /admin/dishes/{id}/approval} (FR-016, FR-018).
 *
 * @param dishId the approved dish.
 * @param status {@code PUBLISHED}, on both a fresh approval and an idempotent no-op.
 * @param alreadyPublished {@code true} when the dish was already {@code PUBLISHED} and this call
 *     changed nothing: no status change and no audit row (FR-018). The interface must report
 *     "ya estaba publicado", not a fresh publication.
 * @param publishedAt the original publication instant, unchanged on an idempotent call.
 * @param reviewHistoryEntryId the immutable approval audit row this call wrote, or {@code null}
 *     on an idempotent call, since no audit row is created then.
 */
public record DishApprovalResponse(
        UUID dishId,
        DishStatus status,
        boolean alreadyPublished,
        Instant publishedAt,
        UUID reviewHistoryEntryId
) {
}
