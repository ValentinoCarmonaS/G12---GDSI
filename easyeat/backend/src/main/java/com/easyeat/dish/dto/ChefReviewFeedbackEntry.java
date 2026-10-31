package com.easyeat.dish.dto;

import java.time.Instant;

/**
 * One rejection cycle's feedback for the Chef (FR-023, SC-003).
 *
 * <p>Deliberately carries only text, time and reviewer identity. No cost, margin or price
 * property exists on this type at all — there is no field for a caller to choose not to render,
 * and no serialization setting can leak one.
 */
public record ChefReviewFeedbackEntry(
        int cycleNumber,
        String reviewerName,
        String comment,
        Instant createdAt
) {
}
