import { describe, it, expect } from 'vitest';
import {
  calculateWaitMinutes,
  formatWaitMinutes,
} from '../../src/utils/counterTimeUtils';

describe('counterTimeUtils (T007, T020, RF-007, RF-008, Invariante 8)', () => {
  const baseReadyAtIso = '2026-11-15T19:30:00.000Z';
  const baseReadyAtMs = Date.parse(baseReadyAtIso);

  describe('calculateWaitMinutes', () => {
    it('returns 0 between 0 and 59 seconds after readyAt (RF-008, Caso borde 5)', () => {
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs)).toBe(0);
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs + 15_000)).toBe(0);
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs + 59_999)).toBe(0);
    });

    it('switches deterministically to 1 at 60 seconds and progresses in integer minutes (RF-007)', () => {
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs + 60_000)).toBe(1);
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs + 119_999)).toBe(1);
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs + 120_000)).toBe(2);
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs + 15 * 60_000)).toBe(15);
    });

    it('compensates for server/client clock skew via serverOffsetMs', () => {
      // Client clock is 30 seconds behind server (+30_000 ms serverOffsetMs)
      // Client sees 40s elapsed, plus +30s offset = 70s effective elapsed => 1 min
      expect(
        calculateWaitMinutes(baseReadyAtIso, 30_000, baseReadyAtMs + 40_000)
      ).toBe(1);

      // Client clock is 30 seconds ahead of server (-30_000 ms serverOffsetMs)
      // Client sees 70s elapsed, minus 30s offset = 40s effective elapsed => 0 min
      expect(
        calculateWaitMinutes(baseReadyAtIso, -30_000, baseReadyAtMs + 70_000)
      ).toBe(0);
    });

    it('never returns negative minutes if readyAt is ahead of effective current time', () => {
      expect(calculateWaitMinutes(baseReadyAtIso, 0, baseReadyAtMs - 10_000)).toBe(0);
    });

    it('returns 0 safely for invalid ISO date strings', () => {
      expect(calculateWaitMinutes('invalid-date', 0, baseReadyAtMs)).toBe(0);
    });
  });

  describe('formatWaitMinutes', () => {
    it('formats wait minutes deterministically as "${minutes} min"', () => {
      expect(formatWaitMinutes(0)).toBe('0 min');
      expect(formatWaitMinutes(1)).toBe('1 min');
      expect(formatWaitMinutes(14)).toBe('14 min');
    });

    it('clamps negative or fractional inputs to non-negative integers', () => {
      expect(formatWaitMinutes(-3)).toBe('0 min');
      expect(formatWaitMinutes(2.9)).toBe('2 min');
    });
  });
});
