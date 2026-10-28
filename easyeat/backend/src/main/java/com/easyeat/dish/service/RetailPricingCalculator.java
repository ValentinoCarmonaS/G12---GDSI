package com.easyeat.dish.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Bidirectional derivation between a commercial margin and a retail price (PVP), per FR-007
 * and FR-008.
 *
 * <p>Deliberately free of Spring and of any request or response type: it takes
 * {@link BigDecimal} and returns {@link BigDecimal}, so its behaviour can be exhausted by pure
 * unit tests rather than inferred through a web slice.
 *
 * <p>FR-008 forbids rounding intermediate quotients. Both derivations divide, and division can
 * be non-terminating, so each quotient is taken at {@link MathContext#DECIMAL128} — 34
 * significant digits — and rounded exactly once, when the value is produced. Money is produced
 * at scale 2; a margin is carried at scale 4 and formatted to 2 only for presentation, which
 * FR-008 makes a requirement rather than a convention.
 */
public class RetailPricingCalculator {

    /** Money scale: FR-008. */
    public static final int MONEY_SCALE = 2;

    /** Margin scale: FR-008 carries margins at four decimals. */
    public static final int MARGIN_SCALE = 4;

    private static final MathContext QUOTIENT = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Derives the retail price from a target margin: {@code food_cost / (1 - margin_pct/100)}.
     *
     * @throws IllegalArgumentException when the derivation is blocked by either of FR-007's two
     *     boundaries — a margin of 100% or above, or a Food Cost of zero. Callers that need to
     *     ask rather than catch should use {@link #marginToPriceAvailable(BigDecimal, BigDecimal)}.
     */
    public BigDecimal retailPriceFrom(BigDecimal foodCost, BigDecimal marginPct) {
        requireNonNull(foodCost, "foodCost");
        requireNonNull(marginPct, "marginPct");

        if (foodCost.signum() == 0) {
            throw new IllegalArgumentException(
                    "The Food Cost is 0.00, so every margin maps to the same price: the retail "
                            + "price must be entered directly.");
        }
        if (marginPct.compareTo(HUNDRED) >= 0) {
            throw new IllegalArgumentException(
                    "A target margin of 100% or above cannot derive a retail price: the formula "
                            + "divides by zero or yields a negative price. Received " + marginPct);
        }

        BigDecimal divisor = BigDecimal.ONE.subtract(marginPct.divide(HUNDRED, QUOTIENT));
        return foodCost.divide(divisor, QUOTIENT).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Derives the prevailing margin from a stored retail price:
     * {@code (retail_price - food_cost) / retail_price * 100}.
     *
     * <p>Multiplication precedes division so that only one rounding occurs.
     *
     * @throws IllegalArgumentException when the retail price is not strictly positive; FR-013
     *     admits no such price, and zero would divide by zero.
     */
    public BigDecimal marginFrom(BigDecimal foodCost, BigDecimal retailPrice) {
        requireNonNull(foodCost, "foodCost");
        requireNonNull(retailPrice, "retailPrice");

        if (retailPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A margin cannot be derived from a non-positive retail price. Received "
                            + retailPrice);
        }

        return retailPrice.subtract(foodCost)
                .multiply(HUNDRED)
                .divide(retailPrice, QUOTIENT)
                .setScale(MARGIN_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Whether a margin can drive a price at all, which is false exactly when the Food Cost is
     * zero (FR-007's third boundary). This is the {@code marginDerivable} flag the contract's
     * MarginSummary carries.
     */
    public boolean marginDerivable(BigDecimal foodCost) {
        requireNonNull(foodCost, "foodCost");
        return foodCost.signum() != 0;
    }

    /**
     * Whether {@link #retailPriceFrom} will succeed for these inputs: both of FR-007's blocking
     * boundaries in one predicate, so the caller can disable the input instead of catching.
     */
    public boolean marginToPriceAvailable(BigDecimal foodCost, BigDecimal marginPct) {
        requireNonNull(foodCost, "foodCost");
        requireNonNull(marginPct, "marginPct");
        return marginDerivable(foodCost) && marginPct.compareTo(HUNDRED) < 0;
    }

    private static void requireNonNull(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
