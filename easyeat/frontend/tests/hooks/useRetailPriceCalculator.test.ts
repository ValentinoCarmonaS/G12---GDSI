import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRetailPriceCalculator } from '../../src/hooks/useRetailPriceCalculator';

/**
 * FR-007 (bidirectional derivation and its two boundaries), FR-008 (exact decimal values pinned
 * by research.md §3.1) and FR-030 (the interaction contract: the edited field is authoritative
 * and unreformatted while being typed, the derived field is reformatted on every recomputation,
 * and a partial value clears the derived field without raising an error).
 *
 * This is a client-side preview only — the authoritative figure is always computed server-side
 * in BigDecimal (FR-008) and this hook never claims otherwise. A margin ≤ −500% floor appeared in
 * an earlier revision of this task's own wording but was withdrawn from the specification itself
 * (spec.md CHK014): only the 100% upper boundary has mathematical grounding, and a negative
 * margin of any size is a legitimate commercial decision gated by FR-014's confirmation, not by
 * this hook refusing the input.
 */
describe('useRetailPriceCalculator', () => {
  describe('margin -> price (FR-007)', () => {
    it('derives the pinned research.md §3.1 examples at exact HALF_UP precision', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(3.33));
      act(() => result.current.onMarginChange('60'));
      expect(result.current.priceInput).toBe('8.33');

      const { result: r2 } = renderHook(() => useRetailPriceCalculator(1.25));
      act(() => r2.current.onMarginChange('70'));
      expect(r2.current.priceInput).toBe('4.17');

      const { result: r3 } = renderHook(() => useRetailPriceCalculator(0.07));
      act(() => r3.current.onMarginChange('60'));
      expect(r3.current.priceInput).toBe('0.18');
    });

    it('a margin of exactly 0 derives a price equal to the Food Cost (PVP = food_cost)', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onMarginChange('0'));
      expect(result.current.priceInput).toBe('4500.00');
    });

    it('a margin of 100% or more blocks the derivation and reports it as undeliverable', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));

      act(() => result.current.onMarginChange('100'));
      expect(result.current.priceDerivable).toBe(false);
      expect(result.current.priceInput).toBe('');

      act(() => result.current.onMarginChange('150'));
      expect(result.current.priceDerivable).toBe(false);
      expect(result.current.priceInput).toBe('');
    });

    it('a negative margin derives a price below the Food Cost without being blocked', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onMarginChange('-20'));
      expect(result.current.priceDerivable).toBe(true);
      expect(result.current.priceInput).toBe('3750.00');
    });

    it('a Food Cost of 0.00 disables the margin -> price direction entirely', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(0));
      expect(result.current.marginDerivable).toBe(false);

      act(() => result.current.onMarginChange('60'));
      expect(result.current.priceInput).toBe('');
    });

    it('the field being edited is not reformatted while typing (FR-030)', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onMarginChange('6'));
      expect(result.current.marginInput).toBe('6');
      act(() => result.current.onMarginChange('60'));
      expect(result.current.marginInput).toBe('60');
    });

    it('reformats the edited field to two decimals once the Administrator leaves it', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onMarginChange('60'));
      act(() => result.current.onMarginBlur());
      expect(result.current.marginInput).toBe('60.00');
    });

    it.each(['', '-', '.', '-.'])(
      'a partial value %j clears the derived price without raising an error',
      (partial) => {
        const { result } = renderHook(() => useRetailPriceCalculator(4500));
        act(() => result.current.onMarginChange('60'));
        expect(result.current.priceInput).not.toBe('');

        act(() => result.current.onMarginChange(partial));
        expect(result.current.priceInput).toBe('');
        expect(result.current.error).toBeNull();
      }
    );
  });

  describe('price -> margin (FR-007)', () => {
    it('derives the margin from a retail price, formatted to two decimals for display', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onPriceChange('11250.00'));
      expect(result.current.marginInput).toBe('60.00');
      expect(result.current.marginPct).toBeCloseTo(60, 4);
    });

    it('a price at or below zero cannot derive a margin', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onPriceChange('0'));
      expect(result.current.marginInput).toBe('');
    });

    it('the field being edited is not reformatted while typing', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onPriceChange('1125'));
      expect(result.current.priceInput).toBe('1125');
    });

    it('reformats the edited price to two decimals once the Administrator leaves it', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onPriceChange('11250'));
      act(() => result.current.onPriceBlur());
      expect(result.current.priceInput).toBe('11250.00');
    });

    it.each(['', '-', '.'])(
      'a partial price %j clears the derived margin without raising an error',
      (partial) => {
        const { result } = renderHook(() => useRetailPriceCalculator(4500));
        act(() => result.current.onPriceChange('11250'));
        expect(result.current.marginInput).not.toBe('');

        act(() => result.current.onPriceChange(partial));
        expect(result.current.marginInput).toBe('');
        expect(result.current.error).toBeNull();
      }
    );
  });

  describe('which field is authoritative', () => {
    it('starts with neither field edited', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      expect(result.current.editedField).toBeNull();
    });

    it('editing the price marks it, not the margin, as authoritative', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onPriceChange('11250'));
      expect(result.current.editedField).toBe('price');
    });

    it('editing the margin marks it, not the price, as authoritative', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onMarginChange('60'));
      expect(result.current.editedField).toBe('margin');
    });

    it('leaving the field being edited clears which one is authoritative (FR-030)', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onMarginChange('60'));
      expect(result.current.editedField).toBe('margin');

      act(() => result.current.onMarginBlur());
      expect(result.current.editedField).toBeNull();
    });

    it('leaving the price field clears it the same way', () => {
      const { result } = renderHook(() => useRetailPriceCalculator(4500));
      act(() => result.current.onPriceChange('11250'));
      act(() => result.current.onPriceBlur());
      expect(result.current.editedField).toBeNull();
    });
  });
});
