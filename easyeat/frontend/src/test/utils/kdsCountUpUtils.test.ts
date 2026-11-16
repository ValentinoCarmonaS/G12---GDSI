import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { calculateElapsedMinutes } from '../../utils/kdsCountUpUtils';

describe('kdsCountUpUtils - calculateElapsedMinutes (T026, US4, RF-011, RF-012)', () => {
  const baseTime = new Date('2026-11-16T12:00:00.000Z').getTime();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(baseTime);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('calcula 0 minutos al momento exacto de inicio (0 segundos acumulados)', () => {
    const startedAtIso = '2026-11-16T12:00:00.000Z';
    expect(calculateElapsedMinutes(startedAtIso)).toBe(0);
  });

  it('calcula 0 minutos durante los primeros 59 segundos acumulados', () => {
    const startedAtIso = '2026-11-16T11:59:01.000Z'; // 59s atrás
    expect(calculateElapsedMinutes(startedAtIso)).toBe(0);
  });

  it('calcula exactamente 1 minuto al cumplirse los 60 segundos acumulados', () => {
    const startedAtIso = '2026-11-16T11:59:00.000Z'; // 60s atrás
    expect(calculateElapsedMinutes(startedAtIso)).toBe(1);
  });

  it('calcula minutos enteros truncados (floor) ante segundos intermedios', () => {
    const startedAtIso = '2026-11-16T11:45:25.000Z'; // 14 min 35 s atrás
    expect(calculateElapsedMinutes(startedAtIso)).toBe(14);
  });

  it('aplica tolerancia al desfase de reloj (clockOffsetMs) contra el servidor', () => {
    // Si el cliente está 30 segundos adelantado respecto al servidor (clockOffsetMs = -30000)
    // baseTime = 12:00:00, startedAt = 11:59:10 (50s en cliente, pero servidor considera 20s)
    const startedAtIso = '2026-11-16T11:59:10.000Z';
    const clockOffsetMs = -30000;
    expect(calculateElapsedMinutes(startedAtIso, clockOffsetMs)).toBe(0);

    // Si con el desfase positivo el tiempo del servidor ya cruzó el minuto
    const startedAtIso2 = '2026-11-16T11:59:20.000Z'; // 40s atrás en cliente
    const clockOffsetMs2 = +30000; // servidor está a las 12:00:30 (70s transcurridos)
    expect(calculateElapsedMinutes(startedAtIso2, clockOffsetMs2)).toBe(1);
  });

  it('retorna 0 si startedAt es en el futuro (desfase extremo o reloj alterado)', () => {
    const startedAtIso = '2026-11-16T12:05:00.000Z'; // 5 minutos en el futuro
    expect(calculateElapsedMinutes(startedAtIso)).toBe(0);
  });

  it('persiste y calcula consistentemente distintos timestamps sin depender de variables de sesión volátiles', () => {
    expect(calculateElapsedMinutes('2026-11-16T11:30:00.000Z')).toBe(30);
    expect(calculateElapsedMinutes('2026-11-16T11:00:00.000Z')).toBe(60);
  });
});
