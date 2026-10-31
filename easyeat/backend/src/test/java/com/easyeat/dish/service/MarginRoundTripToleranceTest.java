package com.easyeat.dish.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The round-trip tolerance of FR-008, tested against the bound <em>derived</em> in
 * `research.md §3.1` rather than against the requirement's original prose.
 *
 * <p>FR-008 once promised ±0.01 percentage points unconditionally. That promise is false, and
 * this suite is where the falsehood is pinned so it cannot be restored by accident. The two
 * invariants actually enforced are:
 *
 * <ul>
 *   <li><b>INV-1, unconditional</b>: the derived price is the exact quotient correctly rounded to
 *       two decimals — so it never deviates from the exact value by more than half a cent.</li>
 *   <li><b>INV-2, conditional</b>: re-deriving the margin from that 2-decimal price stays within
 *       ±0.01 pp <em>whenever</em> {@code retail_price >= 50 * (1 - margin_pct/100)}. Below that
 *       threshold the guarantee does not hold and the system must disclose the achievable
 *       precision instead.</li>
 * </ul>
 */
class MarginRoundTripToleranceTest {

    private static final MathContext EXACT = new MathContext(50);
    private static final BigDecimal HALF_CENT = new BigDecimal("0.005");
    private static final BigDecimal ONE_PP_HUNDREDTH = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RetailPricingCalculator calculator = new RetailPricingCalculator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** The exact, unrounded price, at a precision far beyond the 2 decimals under test. */
    private static BigDecimal exactPrice(BigDecimal foodCost, BigDecimal marginPct) {
        BigDecimal divisor = BigDecimal.ONE.subtract(marginPct.divide(HUNDRED, EXACT));
        return foodCost.divide(divisor, EXACT);
    }

    @Nested
    @DisplayName("INV-1: the derivation is correctly rounded, always")
    class Inv1 {

        @Test
        @DisplayName("the derived price never deviates from the exact quotient by over half a cent")
        void derivedPriceIsWithinHalfACentOfExact() {
            List<String> offenders = new ArrayList<>();

            for (BigDecimal margin : List.of(bd("0.00"), bd("55.00"), bd("60.00"), bd("70.00"),
                    bd("75.00"), bd("-50.00"), bd("99.00"))) {
                for (int cents = 1; cents <= 5000; cents += 7) {
                    BigDecimal foodCost = BigDecimal.valueOf(cents, 2);

                    BigDecimal derived = calculator.retailPriceFrom(foodCost, margin);
                    BigDecimal exact = exactPrice(foodCost, margin);

                    if (derived.subtract(exact).abs().compareTo(HALF_CENT) > 0) {
                        offenders.add("fc=" + foodCost + " m=" + margin
                                + " derived=" + derived + " exact=" + exact);
                    }
                    assertThat(derived.scale()).isEqualTo(2);
                }
            }

            assertThat(offenders)
                    .describedAs("INV-1 is unconditional: every derived price must be the exact "
                            + "quotient rounded HALF_UP to two decimals")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("INV-2: +/-0.01 pp holds at or above the threshold, and not below it")
    class Inv2 {

        @ParameterizedTest
        @CsvSource({
                // foodCost, marginPct, price, re-derived margin, deviation in pp — research.md §3.1
                "3.33, 60.00, 8.33, 60.0240, 0.0240",
                "1.25, 70.00, 4.17, 70.0240, 0.0240",
                "0.07, 60.00, 0.18, 61.1111, 1.1111"
        })
        @DisplayName("the three counter-examples that disprove the unconditional promise")
        void pinnedCounterExamples(String foodCost, String marginPct, String expectedPrice,
                                   String expectedRederived, String expectedDeviation) {
            BigDecimal fc = bd(foodCost);
            BigDecimal m = bd(marginPct);

            BigDecimal price = calculator.retailPriceFrom(fc, m);
            assertThat(price).isEqualByComparingTo(bd(expectedPrice));

            BigDecimal rederived = calculator.marginFrom(fc, price);
            assertThat(rederived).isEqualByComparingTo(bd(expectedRederived));

            BigDecimal deviation = rederived.subtract(m).abs();
            assertThat(deviation).isEqualByComparingTo(bd(expectedDeviation));

            // Each of the three sits BELOW its threshold, which is precisely why it deviates.
            assertThat(price).isLessThan(calculator.toleranceThresholdPrice(m));
            assertThat(deviation)
                    .describedAs("below the threshold the +/-0.01 pp promise is unattainable")
                    .isGreaterThan(ONE_PP_HUNDREDTH);
        }

        @Test
        @DisplayName("at or above the threshold the deviation never exceeds 0.01 pp")
        void withinToleranceAtOrAboveThreshold() {
            List<String> offenders = new ArrayList<>();
            int checked = 0;

            for (BigDecimal margin : List.of(bd("55.00"), bd("60.00"), bd("65.00"), bd("70.00"),
                    bd("75.00"), bd("0.00"), bd("-100.00"))) {
                BigDecimal threshold = calculator.toleranceThresholdPrice(margin);

                for (int cents = 1; cents <= 20000; cents += 13) {
                    BigDecimal foodCost = BigDecimal.valueOf(cents, 2);
                    BigDecimal price = calculator.retailPriceFrom(foodCost, margin);
                    if (price.compareTo(threshold) < 0) {
                        continue;
                    }

                    checked++;
                    BigDecimal deviation = calculator.marginFrom(foodCost, price)
                            .subtract(margin).abs();
                    if (deviation.compareTo(ONE_PP_HUNDREDTH) > 0) {
                        offenders.add("fc=" + foodCost + " m=" + margin + " p=" + price
                                + " deviation=" + deviation);
                    }
                }
            }

            assertThat(checked)
                    .describedAs("the sweep must actually reach the at-or-above-threshold region")
                    .isGreaterThan(1000);
            assertThat(offenders)
                    .describedAs("INV-2 holds at or above 50 * (1 - margin/100)")
                    .isEmpty();
        }

        @Test
        @DisplayName("below the threshold violations exist, so the condition is not decorative")
        void violationsExistBelowThreshold() {
            List<String> violations = new ArrayList<>();

            BigDecimal margin = bd("60.00");
            BigDecimal threshold = calculator.toleranceThresholdPrice(margin);

            for (int cents = 1; cents <= 800; cents++) {
                BigDecimal foodCost = BigDecimal.valueOf(cents, 2);
                BigDecimal price = calculator.retailPriceFrom(foodCost, margin);
                if (price.compareTo(threshold) >= 0) {
                    continue;
                }
                if (calculator.marginFrom(foodCost, price).subtract(margin).abs()
                        .compareTo(ONE_PP_HUNDREDTH) > 0) {
                    violations.add(foodCost.toPlainString());
                }
            }

            // Not every value below the threshold deviates — some quotients round exactly.
            // The claim being tested is that the region contains violations at all, which is
            // what makes the condition in FR-008 load-bearing rather than cosmetic.
            assertThat(violations)
                    .describedAs("the sub-threshold region must contain real violations")
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("the two values the contract exposes so a client can disclose the truth")
    class DisclosedValues {

        @ParameterizedTest
        @CsvSource({
                // marginPct, threshold — research.md §3.1's table for the 55-75% band
                "55.00, 22.50",
                "60.00, 20.00",
                "70.00, 15.00",
                "75.00, 12.50",
                "0.00,  50.00"
        })
        @DisplayName("toleranceThresholdPrice is 50 * (1 - margin/100)")
        void thresholdMatchesTheDerivedFormula(String marginPct, String expected) {
            assertThat(calculator.toleranceThresholdPrice(bd(marginPct)))
                    .isEqualByComparingTo(bd(expected));
        }

        @ParameterizedTest
        @CsvSource({
                // foodCost, price, expected worst case = 0.5*fc / (p * (p - 0.005))
                "3.33, 8.33, 0.0240",
                "1.25, 4.17, 0.0360",
                "0.07, 0.18, 1.1111"
        })
        @DisplayName("marginTolerancePp is the exact bound, not the first-order estimate")
        void tolerancePpMatchesTheExactBound(String foodCost, String price, String expected) {
            assertThat(calculator.marginTolerancePp(bd(foodCost), bd(price)))
                    .isEqualByComparingTo(bd(expected));
        }

        @Test
        @DisplayName("the exact bound really bounds the observed deviation, including at 0.07")
        void boundActuallyBoundsTheObservedDeviation() {
            // research.md §3.1 records that the FIRST-ORDER estimate understates the error here
            // (1.0802 pp estimated against 1.1111 pp observed). Only the exact bound holds, so
            // this is the case that discriminates between the two formulas.
            BigDecimal fc = bd("0.07");
            BigDecimal price = calculator.retailPriceFrom(fc, bd("60.00"));
            BigDecimal observed = calculator.marginFrom(fc, price).subtract(bd("60.00")).abs();

            assertThat(calculator.marginTolerancePp(fc, price))
                    .isGreaterThanOrEqualTo(observed);

            BigDecimal firstOrderEstimate = bd("0.5").multiply(fc)
                    .divide(price.multiply(price), MathContext.DECIMAL128)
                    .setScale(4, RoundingMode.HALF_UP);
            assertThat(firstOrderEstimate)
                    .describedAs("the first-order estimate understates the error, which is why "
                            + "the implementation must not use it")
                    .isLessThan(observed);
        }

        @Test
        @DisplayName("a price at or above the threshold yields a bound of at most 0.01 pp")
        void boundIsWithinAHundredthAtOrAboveThreshold() {
            BigDecimal margin = bd("60.00");
            BigDecimal threshold = calculator.toleranceThresholdPrice(margin);
            BigDecimal foodCost = bd("8.00");       // 8.00 / 0.4 = 20.00, exactly the threshold

            BigDecimal price = calculator.retailPriceFrom(foodCost, margin);
            assertThat(price).isEqualByComparingTo(threshold);
            assertThat(calculator.marginTolerancePp(foodCost, price))
                    .isLessThanOrEqualTo(ONE_PP_HUNDREDTH);
        }
    }
}
