import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { calculateElapsedMinutes, isTicketDelayed, formatDishItem } from '../../utils/kdsTimeUtils';

describe('T004: kdsTimeUtils', () => {
  describe('calculateElapsedMinutes', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should return 0 minutes if startedAt is now', () => {
      const now = new Date('2026-11-16T12:00:00Z');
      vi.setSystemTime(now);

      const elapsed = calculateElapsedMinutes('2026-11-16T12:00:00Z');
      expect(elapsed).toBe(0);
    });

    it('should calculate floor minutes elapsed accurately', () => {
      const now = new Date('2026-11-16T12:14:59Z');
      vi.setSystemTime(now);

      const elapsed = calculateElapsedMinutes('2026-11-16T12:00:00Z');
      expect(elapsed).toBe(14);
    });

    it('should advance to 15 minutes at exactly 15:00', () => {
      const now = new Date('2026-11-16T12:15:00Z');
      vi.setSystemTime(now);

      const elapsed = calculateElapsedMinutes('2026-11-16T12:00:00Z');
      expect(elapsed).toBe(15);
    });

    it('should adjust for clockOffsetMs when client clock differs from server', () => {
      // Client is 10 seconds behind server (+10000ms offset)
      const now = new Date('2026-11-16T12:14:55Z');
      vi.setSystemTime(now);

      const elapsed = calculateElapsedMinutes('2026-11-16T12:00:00Z', 10000);
      // Client time + offset = 12:15:05 -> 15 min
      expect(elapsed).toBe(15);
    });

    it('should return 0 if startedAt is in the future due to slight client skew without offset', () => {
      const now = new Date('2026-11-16T11:59:50Z');
      vi.setSystemTime(now);

      const elapsed = calculateElapsedMinutes('2026-11-16T12:00:00Z');
      expect(elapsed).toBe(0);
    });
  });

  describe('isTicketDelayed', () => {
    it('should return false when elapsed minutes is under 15', () => {
      expect(isTicketDelayed(0)).toBe(false);
      expect(isTicketDelayed(14)).toBe(false);
    });

    it('should return true when elapsed minutes is 15 or greater', () => {
      expect(isTicketDelayed(15)).toBe(true);
      expect(isTicketDelayed(16)).toBe(true);
      expect(isTicketDelayed(45)).toBe(true);
    });
  });

  describe('formatDishItem', () => {
    it('should format dish item as [Cantidad]x [Nombre]', () => {
      expect(formatDishItem(1, 'Hamburguesa Clásica')).toBe('1x Hamburguesa Clásica');
      expect(formatDishItem(3, 'Papas Fritas')).toBe('3x Papas Fritas');
    });

    it('should handle trimmed dish names properly', () => {
      expect(formatDishItem(2, '  Ensalada César  ')).toBe('2x Ensalada César');
    });
  });
});
