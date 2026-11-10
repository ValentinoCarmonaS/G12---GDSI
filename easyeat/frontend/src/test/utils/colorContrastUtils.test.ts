import { describe, it, expect } from 'vitest';
import {
  calculateRelativeLuminance,
  getOptimalContrastColor,
  isValidHexColor,
  getContrastRatio,
} from '../../utils/colorContrastUtils';

describe('T043 [US3]: colorContrastUtils - Client-side WCAG 2.1 Contrast & Luminance Utilities', () => {
  describe('isValidHexColor', () => {
    it('validates 6-character hex colors prefixed with hash', () => {
      expect(isValidHexColor('#10B981')).toBe(true);
      expect(isValidHexColor('#ffffff')).toBe(true);
      expect(isValidHexColor('#000000')).toBe(true);
      expect(isValidHexColor('#F59E0B')).toBe(true);
      expect(isValidHexColor('#abcdef')).toBe(true);
    });

    it('rejects invalid or malformed hex colors', () => {
      expect(isValidHexColor('10B981')).toBe(false);
      expect(isValidHexColor('#123')).toBe(false);
      expect(isValidHexColor('#10B9811')).toBe(false);
      expect(isValidHexColor('#GGGGGG')).toBe(false);
      expect(isValidHexColor('blue')).toBe(false);
      expect(isValidHexColor('')).toBe(false);
    });
  });

  describe('calculateRelativeLuminance', () => {
    it('returns 0 for pure black #000000 and 1 for pure white #FFFFFF', () => {
      expect(calculateRelativeLuminance('#000000')).toBeCloseTo(0, 4);
      expect(calculateRelativeLuminance('#FFFFFF')).toBeCloseTo(1, 4);
    });

    it('returns intermediate luminance values according to sRGB gamma correction', () => {
      const emeraldLuminance = calculateRelativeLuminance('#10B981');
      expect(emeraldLuminance).toBeGreaterThan(0.2);
      expect(emeraldLuminance).toBeLessThan(0.6);
    });
  });

  describe('getOptimalContrastColor', () => {
    it('returns #FFFFFF for dark colors', () => {
      expect(getOptimalContrastColor('#000000')).toBe('#FFFFFF');
      expect(getOptimalContrastColor('#065F46')).toBe('#FFFFFF');
      expect(getOptimalContrastColor('#1E3A8A')).toBe('#FFFFFF');
      expect(getOptimalContrastColor('#059669')).toBe('#FFFFFF');
      expect(getOptimalContrastColor('#10B981')).toBe('#FFFFFF');
    });

    it('returns #000000 for bright/light colors', () => {
      expect(getOptimalContrastColor('#FFFFFF')).toBe('#000000');
      expect(getOptimalContrastColor('#F59E0B')).toBe('#000000');
      expect(getOptimalContrastColor('#FEF3C7')).toBe('#000000');
      expect(getOptimalContrastColor('#FCD34D')).toBe('#000000');
    });
  });

  describe('getContrastRatio', () => {
    it('computes 21:1 for black and white', () => {
      expect(getContrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 1);
    });

    it('computes 1:1 for identical colors', () => {
      expect(getContrastRatio('#10B981', '#10B981')).toBeCloseTo(1, 1);
    });
  });
});
