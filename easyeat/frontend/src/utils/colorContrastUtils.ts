/**
 * Color contrast and luminance calculation utilities following WCAG 2.1 standards.
 */

const HEX_REGEX = /^#[0-9A-Fa-f]{6}$/;

/**
 * Validates whether a given string is a valid 6-character hexadecimal color prefixed with '#'.
 */
export function isValidHexColor(hexColor: string): boolean {
  if (!hexColor || typeof hexColor !== 'string') {
    return false;
  }
  return HEX_REGEX.test(hexColor.trim());
}

/**
 * Calculates WCAG 2.1 relative luminance for an sRGB hex color in range [0, 1].
 */
export function calculateRelativeLuminance(hexColor: string): number {
  if (!isValidHexColor(hexColor)) {
    return 0;
  }

  const cleanHex = hexColor.trim().replace('#', '');
  const r = parseInt(cleanHex.substring(0, 2), 16) / 255;
  const g = parseInt(cleanHex.substring(2, 4), 16) / 255;
  const b = parseInt(cleanHex.substring(4, 6), 16) / 255;

  const gammaCorrect = (channel: number): number => {
    return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
  };

  const rLinear = gammaCorrect(r);
  const gLinear = gammaCorrect(g);
  const bLinear = gammaCorrect(b);

  return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear;
}

/**
 * Determines whether text should be dark (#000000) or light (#FFFFFF)
 * using perceptual brightness (YIQ threshold 130) matching backend calculation.
 */
export function getOptimalContrastColor(hexColor: string): '#000000' | '#FFFFFF' {
  if (!isValidHexColor(hexColor)) {
    return '#000000';
  }

  const cleanHex = hexColor.trim().replace('#', '');
  const r = parseInt(cleanHex.substring(0, 2), 16);
  const g = parseInt(cleanHex.substring(2, 4), 16);
  const b = parseInt(cleanHex.substring(4, 6), 16);

  const yiq = (r * 299 + g * 587 + b * 114) / 1000;
  return yiq >= 130 ? '#000000' : '#FFFFFF';
}

/**
 * Calculates WCAG contrast ratio between two hex colors, ranging from 1:1 to 21:1.
 */
export function getContrastRatio(hexColor1: string, hexColor2: string): number {
  const lum1 = calculateRelativeLuminance(hexColor1);
  const lum2 = calculateRelativeLuminance(hexColor2);

  const lighter = Math.max(lum1, lum2);
  const darker = Math.min(lum1, lum2);

  return (lighter + 0.05) / (darker + 0.05);
}
