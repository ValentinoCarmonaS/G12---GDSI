package com.easyeat.dish.domain;

/**
 * The administrative decisions recorded in {@code dish_review_history}.
 *
 * <p>Closed by design: the database mirrors it with
 * {@code CHECK (event_type IN ('APPROVED', 'REJECTED'))}, and the column is persisted by name
 * through {@code @Enumerated(EnumType.STRING)}. A constant added here must therefore be added to
 * that constraint in the same change, and no constant may be renamed or reordered without a data
 * migration.
 *
 * <p>Note what is <em>not</em> here. There is no {@code REPRICED} event: a price change is not a
 * review decision and is recorded in {@code dish_price_history} instead (FR-010). And there is no
 * {@code PENDING} or {@code SUBMITTED}: this log records decisions the Administrator took, not
 * states the dish passed through.
 */
public enum ReviewEventType {

    /** The dish passed the publication gate and reached {@code PUBLISHED} (FR-016). */
    APPROVED,

    /** The dish was returned to {@code DRAFT} with mandatory feedback for the Chef (FR-020). */
    REJECTED
}
