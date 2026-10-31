package com.easyeat.dish.dto;

import java.math.BigDecimal;

/**
 * The commercial margin as read at the instant of consultation (FR-008, FR-012).
 *
 * <p>Never persisted and never cached. FR-012 is explicit that a stored retail price must not
 * freeze the margin: this record is recomputed from the live Food Cost on every call, so the same
 * price read twice across an inventory change legitimately yields two different margins. The
 * price is the authoritative figure; the margin is a reading of it.
 *
 * @param foodCost           the prevailing Food Cost, already at scale 2 from spec 006. {@code
 *                           null} when the dish has no recipe to cost (FR-037).
 * @param retailPrice        the assigned PVP, or {@code null} when the dish has never been priced.
 * @param marginPct          scale 4 per FR-008, formatted to 2 only for display. {@code null}
 *                           while there is no price or no cost — zero would claim the dish breaks
 *                           even, which is a different statement from "not yet priced".
 * @param marginTolerancePp  the worst-case deviation of a margin re-derived from the stored
 *                           2-decimal price. Present so the interface can disclose the precision
 *                           actually attainable instead of promising ±0.01 pp (FR-008).
 * @param toleranceThresholdPrice the price at or above which that ±0.01 pp bound does hold.
 * @param marginDerivable    false exactly when the Food Cost is zero or absent, which disables
 *                           the margin-to-price direction (FR-007's third boundary).
 */
public record MarginSummary(
        BigDecimal foodCost,
        BigDecimal retailPrice,
        BigDecimal marginPct,
        BigDecimal marginTolerancePp,
        BigDecimal toleranceThresholdPrice,
        boolean hasUnpricedIngredients,
        boolean marginDerivable
) {
}
