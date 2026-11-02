import { describe, expect, it } from 'vitest';
import {
  readAuditQuantity,
  formatAuditQuantity,
} from '../../src/utils/foodCostAudit';

/**
 * The audit line's quantity key is `recipeQuantity` (spec 006's `RecipeItemAuditResponse`). This
 * suite pins the canonical read and the defensive aliases so a rename cannot silently turn the
 * CANTIDAD column back into a stack of zeroes.
 */
describe('readAuditQuantity', () => {
  it('reads the contract key `recipeQuantity`', () => {
    expect(readAuditQuantity({ recipeQuantity: 0.4 })).toBe(0.4);
    expect(readAuditQuantity({ recipeQuantity: 250 })).toBe(250);
  });

  it('falls back to legacy aliases when the canonical key is absent', () => {
    expect(readAuditQuantity({ quantity: 0.15 })).toBe(0.15);
    expect(readAuditQuantity({ amount: 3 })).toBe(3);
    expect(readAuditQuantity({ quantityKg: 1.25 })).toBe(1.25);
  });

  it('prefers the canonical key over an alias', () => {
    expect(readAuditQuantity({ recipeQuantity: 2, quantity: 99 })).toBe(2);
  });

  it('never returns NaN or undefined', () => {
    expect(readAuditQuantity(undefined)).toBe(0);
    expect(readAuditQuantity(null)).toBe(0);
    expect(readAuditQuantity({})).toBe(0);
    expect(readAuditQuantity({ recipeQuantity: 'no-es-un-numero' })).toBe(0);
  });
});

describe('formatAuditQuantity', () => {
  it('keeps meaningful decimals with es-AR formatting', () => {
    expect(formatAuditQuantity({ recipeQuantity: 0.4 })).toBe('0,4');
    expect(formatAuditQuantity({ recipeQuantity: 0.15 })).toBe('0,15');
  });

  it('does not pad whole numbers with trailing zeroes', () => {
    expect(formatAuditQuantity({ recipeQuantity: 250 })).toBe('250');
  });

  it('renders an absent quantity as 0 rather than a blank cell', () => {
    expect(formatAuditQuantity({})).toBe('0');
  });
});
