package com.easyeat.dish.dto;

import java.util.List;

/**
 * A page of the review inbox, with the metadata FR-036 requires to stay accurate even when the
 * page itself is empty.
 *
 * @param pendingCount dishes awaiting a decision across the whole establishment, returned on
 *                     <b>every</b> tab so the badge of FR-003 never depends on which view the
 *                     Administrator happens to be looking at. On the {@code PENDING} tab it
 *                     coincides with {@code totalElements}; on the others it does not, and that
 *                     is the reason it is a separate field rather than derived by the client.
 */
public record DishReviewInboxPageResponse(
        List<DishReviewInboxItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        long pendingCount
) {
}
