package com.easyeat.dish.dto;

/**
 * The body of {@code POST /admin/dishes/{id}/rejection} (FR-021).
 *
 * @param comment mandatory feedback, 10 to 1000 characters after trimming, never blank.
 */
public record RejectDishRequest(String comment) {
}
