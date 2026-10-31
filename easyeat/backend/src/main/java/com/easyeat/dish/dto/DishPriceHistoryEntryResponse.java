package com.easyeat.dish.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the price-change timeline (FR-010, SC-005, SC-008), embedded in {@link
 * DishAuditHistoryResponse}.
 *
 * @param previousPrice {@code null} on a dish's first pricing (FR-010): a fact, not an omission.
 * @param foodCostSnapshot the Food Cost prevailing when this price was decided — an audit fact,
 *     never read back to compute a displayed margin, which keeps FR-012 intact.
 */
public record DishPriceHistoryEntryResponse(
        UUID entryId,
        BigDecimal previousPrice,
        BigDecimal newPrice,
        BigDecimal foodCostSnapshot,
        String actorName,
        Instant createdAt
) {
}
