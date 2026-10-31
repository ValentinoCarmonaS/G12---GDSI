package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The complete culinary file an Administrator reads before deciding (FR-006).
 *
 * @param retailPrice   echo this back as {@code expectedRetailPrice} when repricing. There is no
 *                      version token: concurrency is controlled per operation (FR-025).
 * @param photoUrl      the path of spec 005's existing photo endpoint when a photo exists, and
 *                      {@code null} otherwise. This feature serves no image bytes of its own, and
 *                      a path that would 404 is worse than an honest null.
 * @param recipeAbsent  true when the dish holds no recipe lines at all (FR-037). {@code
 *                      foodCostAudit} is then null and the margin carries no Food Cost; this flag
 *                      is what separates that legitimate state from a failed lookup, which a null
 *                      audit alone could not.
 * @param foodCostAudit spec 006's payload, embedded verbatim rather than reshaped, so a change to
 *                      its itemized shape needs no change here. Null when {@code recipeAbsent}.
 */
public record DishReviewDetailResponse(
        UUID dishId,
        String name,
        String description,
        DishCategory category,
        DishStatus status,
        BigDecimal retailPrice,
        String photoUrl,
        String authorName,
        boolean authorDeactivated,
        boolean allergensConfirmed,
        Instant submittedAt,
        Instant publishedAt,
        int reviewCycleCount,
        boolean isResubmission,
        MarginSummary margin,
        PublicationGateStatus publicationGate,
        boolean recipeAbsent,
        FoodCostAuditResponse foodCostAudit
) {
}
