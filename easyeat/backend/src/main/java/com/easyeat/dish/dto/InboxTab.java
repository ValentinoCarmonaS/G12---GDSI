package com.easyeat.dish.dto;

/**
 * The three views of the administrative review inbox (FR-002).
 *
 * <p>Not a partition of the catalogue, and the names say so: the first two select by current
 * status, while {@link #HISTORY} selects by review history and therefore overlaps both. A dish
 * rejected once and later published belongs to {@code PUBLISHED} and {@code HISTORY} at the same
 * time; a dish returned to {@code DRAFT} belongs only to {@code HISTORY}.
 */
public enum InboxTab {

    /** Dishes in {@code PENDING_PRICE}, oldest submission first. */
    PENDING,

    /** Dishes in {@code PUBLISHED}, most recently published first. */
    PUBLISHED,

    /** Dishes with at least one review entry, whatever their current status. */
    HISTORY
}
