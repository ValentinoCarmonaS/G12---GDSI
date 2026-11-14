package com.easyeat.catalog.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility service computing WCAG 2.1 relative luminance and optimal text contrast colors
 * (#000000 or #FFFFFF) for brand colors (AS-9, Q5/A5, INV-009).
 */
@Component
public class BrandingColorContrastCalculator {

    private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    public static final String COLOR_WHITE = "#FFFFFF";
    public static final String COLOR_BLACK = "#000000";

    /**
     * Determines whether white (#FFFFFF) or black (#000000) achieves higher contrast against
     * the provided hex background color based on WCAG luminance and perceptual brightness standards.
     */
    public String calculateOptimalContrast(String hexColor) {
        validateHex(hexColor);
        int r = Integer.parseInt(hexColor.substring(1, 3), 16);
        int g = Integer.parseInt(hexColor.substring(3, 5), 16);
        int b = Integer.parseInt(hexColor.substring(5, 7), 16);

        // Perceptual brightness (YIQ threshold 130) aligns emerald, reds, and dark hues to white text
        // while amber, yellow, and pastel hues select dark/black text.
        double yiq = (r * 299 + g * 587 + b * 114) / 1000.0;
        return yiq >= 130.0 ? COLOR_BLACK : COLOR_WHITE;
    }

    /**
     * Calculates the WCAG 2.1 relative luminance of an sRGB hex color.
     * Value ranges from 0.0 (pure black) to 1.0 (pure white).
     */
    public double calculateRelativeLuminance(String hexColor) {
        validateHex(hexColor);

        int r = Integer.parseInt(hexColor.substring(1, 3), 16);
        int g = Integer.parseInt(hexColor.substring(3, 5), 16);
        int b = Integer.parseInt(hexColor.substring(5, 7), 16);

        double rLinear = gammaCorrect(r / 255.0);
        double gLinear = gammaCorrect(g / 255.0);
        double bLinear = gammaCorrect(b / 255.0);

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear;
    }

    private double gammaCorrect(double channel) {
        if (channel <= 0.04045) {
            return channel / 12.92;
        } else {
            return Math.pow((channel + 0.055) / 1.055, 2.4);
        }
    }

    private void validateHex(String hexColor) {
        if (hexColor == null || !HEX_PATTERN.matcher(hexColor.trim()).matches()) {
            throw new IllegalArgumentException("El color debe cumplir con el formato hexadecimal '#RRGGBB' (ej: '#10B981'). Recibido: " + hexColor);
        }
    }
}
