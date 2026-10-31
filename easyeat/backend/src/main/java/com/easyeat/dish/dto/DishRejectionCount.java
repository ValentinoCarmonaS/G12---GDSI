package com.easyeat.dish.dto;

import java.util.UUID;

/**
 * How many times one dish has been rejected, as returned by a single grouped query over
 * {@code dish_review_history}.
 *
 * <p>A projection rather than an {@code Object[]} so the two values cannot be read in the wrong
 * order: a positional array compiles just as happily with the count and the identifier swapped.
 */
public record DishRejectionCount(UUID dishId, long rejections) {
}
