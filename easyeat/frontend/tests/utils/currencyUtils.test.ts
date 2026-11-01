import { describe, it, expect } from 'vitest';
import {
  toCents,
  fromCents,
  formatCurrency,
  calculateLineTotalCents,
  calculateCartTotalCents,
  calculateCartTotal,
} from '../../src/utils/currencyUtils';

describe('currencyUtils (T024, US2, COM-6.1.2, INV-2, SC-007)', () => {
  describe('toCents and fromCents conversions', () => {
    it('converts decimal amounts to integer cents with exact rounding', () => {
      expect(toCents(10.5)).toBe(1050);
      expect(toCents(19.99)).toBe(1999);
      expect(toCents(0.1)).toBe(10);
      expect(toCents(0.2)).toBe(20);
      expect(toCents(0)).toBe(0);
      expect(toCents(1234.56)).toBe(123456);
    });

    it('converts integer cents back to decimal currency amounts', () => {
      expect(fromCents(1050)).toBe(10.5);
      expect(fromCents(1999)).toBe(19.99);
      expect(fromCents(30)).toBe(0.3);
      expect(fromCents(0)).toBe(0);
      expect(fromCents(123456)).toBe(1234.56);
    });
  });

  describe('elimination of IEEE-754 floating-point drift (INV-2)', () => {
    it('eliminates 0.1 + 0.2 floating point drift via integer cents', () => {
      // Direct floating point in JS: 0.1 + 0.2 = 0.30000000000000004
      const naiveFloatSum = 0.1 + 0.2;
      expect(naiveFloatSum).not.toBe(0.3);

      const centsA = toCents(0.1);
      const centsB = toCents(0.2);
      const exactSum = fromCents(centsA + centsB);
      expect(exactSum).toBe(0.3);
    });

    it('calculates line total in cents avoiding multiplication drift', () => {
      // 19.99 * 3 in naive float = 59.970000000000006
      const naiveMultiplication = 19.99 * 3;
      expect(naiveMultiplication).not.toBe(59.97);

      const lineCents = calculateLineTotalCents(19.99, 3);
      expect(lineCents).toBe(5997);
      expect(fromCents(lineCents)).toBe(59.97);
    });

    it('calculates accumulated cart total across multiple line items with zero precision loss', () => {
      const items = [
        { price: 19.99, quantity: 3 }, // 59.97
        { price: 0.1, quantity: 2 },   // 0.20
        { price: 14.5, quantity: 7 },   // 101.50
        { price: 2500, quantity: 2 },   // 5000.00
      ];

      const totalCents = calculateCartTotalCents(items);
      expect(totalCents).toBe(5997 + 20 + 10150 + 500000); // 516167 cents

      const totalAmount = calculateCartTotal(items);
      expect(totalAmount).toBe(5161.67);
    });

    it('handles empty item lists returning 0', () => {
      expect(calculateCartTotalCents([])).toBe(0);
      expect(calculateCartTotal([])).toBe(0);
    });
  });

  describe('formatCurrency display representation', () => {
    it('formats exact monetary values with 2 decimal places and currency symbol', () => {
      expect(formatCurrency(5161.67)).toMatch(/\$\s?5\.161,67|\$5,161\.67|\$5161,67|\$ 5161\.67/);
      expect(formatCurrency(0)).toMatch(/\$\s?0,00|\$0\.00/);
      expect(formatCurrency(19.9)).toMatch(/\$\s?19,90|\$19\.90/);
    });

    it('formats from cents when fromCents flag is passed', () => {
      const formatted = formatCurrency(5997, { isCents: true });
      expect(formatted).toMatch(/\$\s?59,97|\$59\.97/);
    });
  });
});
