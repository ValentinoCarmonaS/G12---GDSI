import { describe, it, expect } from 'vitest';
import { isDishStatus } from '../../src/types/dish';

/**
 * T079a: this union narrowed to 'DRAFT' | 'PENDING_PRICE' before spec 007's DishStatus enum
 * admitted PUBLISHED (T005), leaving the two domains disagreeing — isDishStatus is the guard that
 * disagreement is observable through.
 */
describe('isDishStatus', () => {
  it.each(['DRAFT', 'PENDING_PRICE', 'PUBLISHED'])('accepts %s', (status) => {
    expect(isDishStatus(status)).toBe(true);
  });

  it.each(['ACTIVE', 'INACTIVE', 'archived', '', null, undefined, 42])(
    'rejects %s',
    (value) => {
      expect(isDishStatus(value)).toBe(false);
    }
  );
});
