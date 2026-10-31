package com.easyeat.dish.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * The body of {@code PUT /admin/dishes/{id}/retail-price} (FR-009, FR-013, FR-014, FR-025).
 *
 * <p>{@code expectedRetailPrice} is required and nullable at once, which {@code retailPrice} is
 * not: the key must appear in the JSON, but its value may legitimately be {@code null} (the
 * first-pricing assertion). Jackson's {@code @JsonProperty(required = true)} checks exactly
 * presence, not non-nullity, so a missing key fails deserialization — surfaced by {@code
 * GlobalExceptionHandler#handleHttpMessageNotReadable} as 400 {@code
 * expected-retail-price-required} — while an explicit {@code null} is accepted. {@code
 * retailPrice} carries no such annotation: a missing or null price reaches {@link
 * com.easyeat.dish.service.DishGovernanceService} as {@code null} and is rejected there by {@link
 * com.easyeat.dish.exception.InvalidRetailPriceException}, which already states 400 for that
 * case — a second mechanism for the same outcome would only duplicate it.
 *
 * @param retailPrice strictly positive, at most two decimal places (FR-013).
 * @param expectedRetailPrice the price read when the panel was opened, or {@code null} if the
 *     dish was unpriced then. A mismatch is FR-025's lost-update guard.
 * @param acknowledgeNegativeMargin explicit confirmation that a price below the Food Cost is
 *     intended (FR-014). Defaults to {@code false} when absent.
 */
public record SetRetailPriceRequest(
        BigDecimal retailPrice,
        @JsonProperty(required = true) BigDecimal expectedRetailPrice,
        boolean acknowledgeNegativeMargin
) {
}
