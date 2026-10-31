package com.easyeat.dish.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The dish's retail price changed between the Administrator reading it and saving a new one
 * (FR-025).
 *
 * <p>Raised when the repricing {@code UPDATE} carrying
 * {@code AND retail_price IS NOT DISTINCT FROM :expectedRetailPrice} affects zero rows. A status
 * predicate cannot detect this: a price save is legal from the dish's current state no matter how
 * stale the panel is, so without the compare-and-set two concurrent saves would produce a classic
 * lost update.
 *
 * <p>Both prices may legitimately be {@code null}: {@code expected} is {@code null} when the
 * Administrator read an unpriced dish, and {@code current} is {@code null} if it is somehow
 * unpriced now.
 *
 * <p>Maps to 409 {@code dish-stale-price}.
 */
public class StaleDishPriceException extends RuntimeException {

    private final UUID dishId;
    private final BigDecimal expectedRetailPrice;
    private final BigDecimal currentRetailPrice;

    public StaleDishPriceException(UUID dishId, BigDecimal expectedRetailPrice,
                                   BigDecimal currentRetailPrice) {
        super(String.format(
                "El precio del plato con ID %s cambió desde que fue leído: se esperaba %s y el "
                        + "valor vigente es %s. La operación no se aplicó.",
                dishId, describe(expectedRetailPrice), describe(currentRetailPrice)));
        this.dishId = dishId;
        this.expectedRetailPrice = expectedRetailPrice;
        this.currentRetailPrice = currentRetailPrice;
    }

    private static String describe(BigDecimal price) {
        return price == null ? "sin precio" : price.toPlainString();
    }

    public UUID getDishId() {
        return dishId;
    }

    public BigDecimal getExpectedRetailPrice() {
        return expectedRetailPrice;
    }

    public BigDecimal getCurrentRetailPrice() {
        return currentRetailPrice;
    }
}
