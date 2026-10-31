package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the review inbox: every field FR-004 enumerates, and nothing beyond it.
 *
 * <p>What is deliberately absent is as much the point as what is present. There is no Food Cost
 * and no margin here: FR-012 recomputes those on consultation and a list of twenty dishes would
 * otherwise cost twenty cost audits. The detail view carries them.
 *
 * @param authorName       {@code null} when the dish records no author, or when the recorded
 *                         identifier no longer resolves to a user. The interface renders that as
 *                         "Autor no registrado" (FR-027); choosing the words is its business, not
 *                         this payload's.
 * @param authorDeactivated {@code true} when the authoring Chef's account is suspended.
 *                         Informational only — FR-026 is explicit that it never blocks any
 *                         administrative action on the dish.
 * @param submittedAt      the instant of the {@code DRAFT -> PENDING_PRICE} transition, written by
 *                         spec 005 on every transition including resubmissions. The pending tab is
 *                         ordered by it ascending and by nothing else.
 * @param isResubmission   {@code true} when the dish carries at least one past rejection (FR-004).
 * @param reviewCycleCount past rejections. The cycle the dish is currently in is this plus one,
 *                         which is why the two are not the same number.
 */
public record DishReviewInboxItemResponse(
        UUID dishId,
        String name,
        DishCategory category,
        DishStatus status,
        String authorName,
        boolean authorDeactivated,
        Instant submittedAt,
        Instant publishedAt,
        BigDecimal retailPrice,
        boolean isResubmission,
        int reviewCycleCount
) {
}
