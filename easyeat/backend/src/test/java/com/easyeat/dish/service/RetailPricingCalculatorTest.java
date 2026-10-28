package com.easyeat.dish.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-007 (bidirectional derivation and its three boundaries) and FR-008 (exact decimal
 * arithmetic, rounding point, margin scale, direction asymmetry).
 *
 * <p>Values are not invented: every expected figure is either carried by `spec.md` or verified
 * in `research.md §3.1` with exact decimal arithmetic. Scales are asserted alongside values,
 * because FR-008 makes scale 2 for money and scale 4 for margins a requirement rather than a
 * formatting detail.
 */
class RetailPricingCalculatorTest {

    private final RetailPricingCalculator calculator = new RetailPricingCalculator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Nested
    @DisplayName("margin -> price (FR-007)")
    class MarginToPrice {

        @ParameterizedTest
        @CsvSource({
                // foodCost, marginPct, expected PVP
                "4.00,     60.00,  10.00",   // spec.md's own example: exact division
                "3.50,     75.00,  14.00",   // spec.md's second example
                "1.00,     20.00,  1.25",
                "9500.00,  75.00,  38000.00"
        })
        @DisplayName("derives the retail price with HALF_UP at two decimals")
        void derivesPrice(String foodCost, String marginPct, String expected) {
            assertThat(calculator.retailPriceFrom(bd(foodCost), bd(marginPct)))
                    .isEqualByComparingTo(bd(expected));
        }

        @Test
        @DisplayName("rounds the quotient only once, at the end, never an intermediate")
        void roundsOnlyTheFinalValue() {
            // 10.00 / 0.3 is non-terminating: 33.3333...  A calculator that rounded the
            // intermediate quotient to 2 decimals (0.30) would still land here, so the
            // companion case below is the one that discriminates.
            assertThat(calculator.retailPriceFrom(bd("10.00"), bd("70.00")))
                    .isEqualByComparingTo(bd("33.33"));
            assertThat(calculator.retailPriceFrom(bd("20.00"), bd("70.00")))
                    .isEqualByComparingTo(bd("66.67"));
        }

        @ParameterizedTest
        @CsvSource({
                "10.005,      0.00,   10.01",      // exactly half a cent, rounds away from zero
                "10.004,      0.00,   10.00",
                "10.006,      0.00,   10.01",
                "999999.99,  60.00,   2499999.98"  // 2499999.975 exactly -> HALF_UP
        })
        @DisplayName("applies HALF_UP at exactly half a cent, including at large amounts")
        void halfUpAtExactlyHalfACent(String foodCost, String marginPct, String expected) {
            assertThat(calculator.retailPriceFrom(bd(foodCost), bd(marginPct)))
                    .isEqualByComparingTo(bd(expected));
        }

        @Test
        @DisplayName("always returns scale 2, because money is produced at scale 2 (FR-008)")
        void priceCarriesScaleTwo() {
            assertThat(calculator.retailPriceFrom(bd("4.00"), bd("60.00")).scale()).isEqualTo(2);
            assertThat(calculator.retailPriceFrom(bd("4500"), bd("0")).scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("a margin of exactly 0% derives PVP = food cost (FR-007, first boundary)")
        void marginZeroYieldsFoodCost() {
            assertThat(calculator.retailPriceFrom(bd("4500.00"), BigDecimal.ZERO))
                    .isEqualByComparingTo(bd("4500.00"));
            assertThat(calculator.retailPriceFrom(bd("0.01"), bd("0.0000")))
                    .isEqualByComparingTo(bd("0.01"));
        }

        @ParameterizedTest
        @CsvSource({
                "15.00,  -50.00,   10.00",    // fc / 1.5
                "600.00, -500.00,  100.00"    // fc / 6 — the floor of the swept range
        })
        @DisplayName("a negative target margin is legal and derives a price below the food cost")
        void negativeMarginIsLegal(String foodCost, String marginPct, String expected) {
            assertThat(calculator.retailPriceFrom(bd(foodCost), bd(marginPct)))
                    .isEqualByComparingTo(bd(expected));
        }

        @Test
        @DisplayName("margins far below -500% stay legal: nothing in FR-007 bounds them below")
        void veryNegativeMarginIsNotBlocked() {
            // -1000% -> fc / 11
            assertThat(calculator.retailPriceFrom(bd("1100.00"), bd("-1000.00")))
                    .isEqualByComparingTo(bd("100.00"));
            assertThat(calculator.marginToPriceAvailable(bd("1100.00"), bd("-1000.00"))).isTrue();
        }
    }

    @Nested
    @DisplayName("the two blocked directions (FR-007, second and third boundaries)")
    class BlockedDerivations {

        @ParameterizedTest
        @ValueSource(strings = {"100.00", "100.0001", "150.00", "1000.00"})
        @DisplayName("a margin of 100% or above blocks margin->price and says why")
        void marginAtOrAbove100IsBlocked(String marginPct) {
            assertThat(calculator.marginToPriceAvailable(bd("4.00"), bd(marginPct))).isFalse();

            assertThatThrownBy(() -> calculator.retailPriceFrom(bd("4.00"), bd(marginPct)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("a margin just under 100% is still derivable, so the boundary is not off by one")
        void marginJustUnder100IsAllowed() {
            assertThat(calculator.marginToPriceAvailable(bd("4.00"), bd("99.9999"))).isTrue();
            assertThat(calculator.retailPriceFrom(bd("4.00"), bd("99.9999")))
                    .isGreaterThan(bd("0.00"));
        }

        @Test
        @DisplayName("a food cost of 0.00 disables margin->price entirely (FR-007, third boundary)")
        void zeroFoodCostDisablesDerivation() {
            assertThat(calculator.marginDerivable(bd("0.00"))).isFalse();
            assertThat(calculator.marginToPriceAvailable(bd("0.00"), bd("60.00"))).isFalse();

            assertThatThrownBy(() -> calculator.retailPriceFrom(bd("0.00"), bd("60.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Food Cost");
        }

        @Test
        @DisplayName("a non-zero food cost keeps the derivation available")
        void nonZeroFoodCostIsDerivable() {
            assertThat(calculator.marginDerivable(bd("0.01"))).isTrue();
            assertThat(calculator.marginDerivable(bd("4500.00"))).isTrue();
        }

        @Test
        @DisplayName("the price->margin direction survives both blocks (FR-007 says so explicitly)")
        void priceToMarginRemainsAvailable() {
            // Zero food cost: every price is 100% margin, which is a legitimate reading.
            assertThat(calculator.marginFrom(bd("0.00"), bd("10.00")))
                    .isEqualByComparingTo(bd("100.0000"));
        }
    }

    @Nested
    @DisplayName("price -> margin (FR-007, FR-008 margin scale)")
    class PriceToMargin {

        @ParameterizedTest
        @CsvSource({
                // foodCost, retailPrice, expected margin at scale 4
                "3.50,  14.00,  75.0000",   // spec.md's example
                "4.00,  10.00,  60.0000",
                "3.33,   8.33,  60.0240",   // research.md §3.1, verified with exact Decimal
                "1.25,   4.17,  70.0240",   // research.md §3.1
                "0.07,   0.18,  61.1111"    // research.md §3.1, the pathological case
        })
        @DisplayName("derives the margin at scale 4, HALF_UP")
        void derivesMargin(String foodCost, String retailPrice, String expected) {
            assertThat(calculator.marginFrom(bd(foodCost), bd(retailPrice)))
                    .isEqualByComparingTo(bd(expected));
        }

        @Test
        @DisplayName("carries the margin at scale 4, never 2 (FR-008 margin scale)")
        void marginCarriesScaleFour() {
            BigDecimal margin = calculator.marginFrom(bd("3.33"), bd("8.33"));
            assertThat(margin.scale()).isEqualTo(4);
            // The requirement is explicit that the scale-2 reading is NOT equivalent.
            assertThat(margin).isNotEqualTo(bd("60.02"));
        }

        @Test
        @DisplayName("a price below the food cost yields a negative margin rather than an error")
        void priceBelowCostYieldsNegativeMargin() {
            assertThat(calculator.marginFrom(bd("10.00"), bd("5.00")))
                    .isEqualByComparingTo(bd("-100.0000"));
            assertThat(calculator.marginFrom(bd("600.00"), bd("100.00")))
                    .isEqualByComparingTo(bd("-500.0000"));
        }

        @Test
        @DisplayName("a price equal to the food cost yields exactly 0%")
        void priceEqualToCostYieldsZero() {
            assertThat(calculator.marginFrom(bd("4500.00"), bd("4500.00")))
                    .isEqualByComparingTo(bd("0.0000"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.00", "-0.01", "-10.00"})
        @DisplayName("a retail price of zero or below has no margin to derive (FR-013)")
        void nonPositivePriceIsRejected(String retailPrice) {
            assertThatThrownBy(() -> calculator.marginFrom(bd("4.00"), bd(retailPrice)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts a food cost carrying more than two decimals without rounding it first")
        void acceptsHigherScaleFoodCost() {
            // 0.11 / 0.18 * 100 at full precision, not after truncating the cost to 2 decimals.
            assertThat(calculator.marginFrom(bd("0.0700"), bd("0.18")))
                    .isEqualByComparingTo(bd("61.1111"));
        }
    }

    @Nested
    @DisplayName("null handling: the calculator takes no request types, so it guards its inputs")
    class NullInputs {

        @Test
        @DisplayName("rejects null arguments rather than returning a misleading zero")
        void rejectsNulls() {
            assertThatThrownBy(() -> calculator.retailPriceFrom(null, bd("60.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculator.retailPriceFrom(bd("4.00"), null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculator.marginFrom(null, bd("10.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculator.marginFrom(bd("4.00"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
