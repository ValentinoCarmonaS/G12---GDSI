package com.easyeat.dish.domain;

/**
 * The advisories FR-017 and FR-014 require the Administrator to acknowledge explicitly before a
 * write proceeds.
 *
 * <p>An acknowledgement is an additional precondition, never an override (FR-017). These exist so
 * a deliberate decision is recorded as deliberate — not so a blocked dish can be forced through.
 */
public enum AcknowledgementKind {

    /** The dish's recipe contains at least one ingredient with no acquisition cost. */
    UNPRICED_INGREDIENTS,

    /** The retail price is below the Food Cost, so the margin is negative (FR-014). */
    NEGATIVE_MARGIN
}
