package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishStatus;

import java.util.List;
import java.util.UUID;

/**
 * The response of {@code GET /dishes/{id}/review-feedback} (FR-023, SC-003).
 *
 * <p>Financial confidentiality is structural, not cosmetic: neither this type nor {@link
 * ChefReviewFeedbackEntry} declares a cost, margin or price property, so there is nothing for a
 * future change to accidentally populate.
 *
 * @param entries rejection feedback, newest cycle first. Empty when the dish has never been
 *     rejected.
 */
public record ChefReviewFeedbackResponse(
        UUID dishId,
        DishStatus status,
        List<ChefReviewFeedbackEntry> entries
) {
}
