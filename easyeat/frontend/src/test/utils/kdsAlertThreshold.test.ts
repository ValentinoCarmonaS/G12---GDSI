import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { isTicketDelayed } from '../../utils/kdsAlertThreshold';
import { calculateElapsedMinutes } from '../../utils/kdsTimeUtils';

describe('kdsAlertThreshold - isTicketDelayed (T030, US5, RF-013, RF-014)', () => {
  const baseTime = new Date('2026-11-16T12:00:00.000Z').getTime();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(baseTime);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('retorna false cuando elapsedMinutes es estrictamente menor a 15', () => {
    expect(isTicketDelayed(0)).toBe(false);
    expect(isTicketDelayed(5)).toBe(false);
    expect(isTicketDelayed(14)).toBe(false);
  });

  it('retorna false a los 14 minutos y 59 segundos acumulados', () => {
    // 14 min 59s atrás respecto a las 12:00:00 -> 11:45:01
    const startedAtIso = '2026-11-16T11:45:01.000Z';
    const elapsedMinutes = calculateElapsedMinutes(startedAtIso);
    expect(elapsedMinutes).toBe(14);
    expect(isTicketDelayed(elapsedMinutes)).toBe(false);
  });

  it('retorna true exactamente a los 15 minutos y 00 segundos acumulados', () => {
    // 15 min 00s atrás respecto a las 12:00:00 -> 11:45:00
    const startedAtIso = '2026-11-16T11:45:00.000Z';
    const elapsedMinutes = calculateElapsedMinutes(startedAtIso);
    expect(elapsedMinutes).toBe(15);
    expect(isTicketDelayed(elapsedMinutes)).toBe(true);
  });

  it('retorna true para tiempos superiores a 15 minutos', () => {
    expect(isTicketDelayed(16)).toBe(true);
    expect(isTicketDelayed(20)).toBe(true);
    expect(isTicketDelayed(60)).toBe(true);
  });
});
