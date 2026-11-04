package com.easyeat.dish.dto;

import java.time.Instant;

/**
 * One rejection cycle's feedback for the Chef (FR-023, SC-003).
 *
 * <p>Deliberately carries only text, time, reviewer identity and — for a rejection that carried
 * one — the Administrator's proposed edition of the dish (FR-042). No cost, margin or price
 * property exists on this type at all: there is no field for a caller to choose not to render, and
 * no serialization setting can leak one. The proposal holds culinary fields only (title,
 * description, allergen codes, lifestyle codes, proposed ingredient quantities); a cost, Food Cost
 * or margin is computed, never part of a proposal.
 */
public record ChefReviewFeedbackEntry(
        int cycleNumber,
        String reviewerName,
        String comment,
        DishProposedChanges proposedChanges,
        Instant createdAt
) {
}
