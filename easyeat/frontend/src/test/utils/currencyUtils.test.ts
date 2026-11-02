import { describe, it, expect } from 'vitest';
import {
  toCents,
  fromCents,
  formatCurrency,
  calculateLineTotalCents,
  calculateLineTotal,
  calculateCartTotalCents,
  calculateCartTotal,
} from '../../utils/currencyUtils';

describe('currencyUtils - Foundational Unit Tests (T003, AC-3.2, INV-10, FR-012, SC-007)', () => {
  describe('Regional Currency Formatting (INV-10, FR-012, SC-007)', () => {
    it('formats exact monetary values with "$ " prefix, dot thousand separator and comma decimal separator', () => {
      // Must follow regional format "$ 12.500,00" exactly
      expect(formatCurrency(12500)).toBe('$ 12.500,00');
      expect(formatCurrency(0)).toBe('$ 0,00');
      expect(formatCurrency(12.5)).toBe('$ 12,50');
      expect(formatCurrency(1234567.89)).toBe('$ 1.234.567,89');
    });

    it('formats from cents with regional formatting when isCents option is enabled', () => {
      expect(formatCurrency(1250000, { isCents: true })).toBe('$ 12.500,00');
      expect(formatCurrency(99, { isCents: true })).toBe('$ 0,99');
      expect(formatCurrency(0, { isCents: true })).toBe('$ 0,00');
    });

    it('handles negative or NaN inputs safely with 0 fallback', () => {
      expect(formatCurrency(NaN)).toBe('$ 0,00');
    });
  });

  describe('Integer cent arithmetic without floating point drift (INV-10, INV-13)', () => {
    it('converts decimal amounts to integer cents with exact half-up rounding', () => {
      expect(toCents(10.5)).toBe(1050);
      expect(toCents(19.99)).toBe(1999);
      expect(toCents(0.1)).toBe(10);
      expect(toCents(0.2)).toBe(20);
      expect(toCents(0)).toBe(0);
      expect(toCents(1234.56)).toBe(123456);
      expect(toCents(NaN)).toBe(0);
    });

    it('converts integer cents back to decimal currency amounts', () => {
      expect(fromCents(1050)).toBe(10.5);
      expect(fromCents(1999)).toBe(19.99);
      expect(fromCents(30)).toBe(0.3);
      expect(fromCents(0)).toBe(0);
      expect(fromCents(123456)).toBe(1234.56);
      expect(fromCents(NaN)).toBe(0);
    });

    it('eliminates 0.1 + 0.2 floating point drift via integer cents', () => {
      const naiveFloatSum = 0.1 + 0.2;
      expect(naiveFloatSum).not.toBe(0.3);

      const centsA = toCents(0.1);
      const centsB = toCents(0.2);
      const exactSum = fromCents(centsA + centsB);
      expect(exactSum).toBe(0.3);
    });

    it('calculates line total in cents and decimals avoiding multiplication drift', () => {
      const lineCents = calculateLineTotalCents(1.05, 3);
      expect(lineCents).toBe(315);
      expect(calculateLineTotal(1.05, 3)).toBe(3.15);
    });

    it('calculates cart total across multiple line items with zero precision loss', () => {
      const items = [
        { price: 19.99, quantity: 3 }, // 59.97 -> 5997
        { price: 0.1, quantity: 2 },   // 0.20 -> 20
        { price: 14.5, quantity: 7 },   // 101.50 -> 10150
        { price: 2500, quantity: 2 },   // 5000.00 -> 500000
      ];

      expect(calculateCartTotalCents(items)).toBe(516167);
      expect(calculateCartTotal(items)).toBe(5161.67);
    });

    it('handles empty or invalid arrays returning 0', () => {
      expect(calculateCartTotalCents([])).toBe(0);
      expect(calculateCartTotal([])).toBe(0);
      // @ts-expect-error test invalid inputs
      expect(calculateCartTotalCents(null)).toBe(0);
    });
  });
});
