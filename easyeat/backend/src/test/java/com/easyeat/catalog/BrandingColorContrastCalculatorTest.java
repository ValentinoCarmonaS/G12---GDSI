package com.easyeat.catalog;

import com.easyeat.catalog.service.BrandingColorContrastCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T039: BrandingColorContrastCalculator - WCAG 2.1 Relative Luminance and Contrast Calculation")
class BrandingColorContrastCalculatorTest {

    private final BrandingColorContrastCalculator calculator = new BrandingColorContrastCalculator();

    @ParameterizedTest(name = "Color {0} should have optimal contrast color {1}")
    @CsvSource({
            "#000000, #FFFFFF", // Pure black -> white text
            "#FFFFFF, #000000", // Pure white -> black text
            "#059669, #FFFFFF", // Emerald green -> white text
            "#10B981, #FFFFFF", // Primary default green -> white text
            "#065F46, #FFFFFF", // Secondary dark green -> white text
            "#1E3A8A, #FFFFFF", // Dark navy blue -> white text
            "#F59E0B, #000000", // Amber yellow -> black text
            "#FEF3C7, #000000", // Light amber -> black text
            "#D97706, #000000", // Warm amber -> black text
            "#EF4444, #FFFFFF"  // Bright red -> white text
    })
    @DisplayName("Calculates optimal contrast color (#000000 or #FFFFFF) according to WCAG 2.1 contrast ratio")
    void shouldCalculateOptimalContrastColor(String hexColor, String expectedContrastColor) {
        String result = calculator.calculateOptimalContrast(hexColor);
        assertThat(result).isEqualTo(expectedContrastColor);
    }

    @Test
    @DisplayName("Computes relative luminance for pure black and pure white")
    void shouldComputeAccurateLuminance() {
        assertThat(calculator.calculateRelativeLuminance("#000000")).isEqualTo(0.0);
        assertThat(calculator.calculateRelativeLuminance("#FFFFFF")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Rejects null or blank hex colors with IllegalArgumentException")
    void shouldRejectInvalidInput() {
        assertThatThrownBy(() -> calculator.calculateOptimalContrast(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculateOptimalContrast(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
