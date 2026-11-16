import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useKdsCountUp } from '../../hooks/useKdsCountUp';

describe('useKdsCountUp Hook (T027, US4, RF-011, RF-012)', () => {
  const baseTime = new Date('2026-11-16T12:00:00.000Z').getTime();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(baseTime);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('retorna 0 minutos al iniciar si startedAt es el instante actual', () => {
    const startedAt = '2026-11-16T12:00:00.000Z';
    const { result } = renderHook(() => useKdsCountUp(startedAt));

    expect(result.current.elapsedMinutes).toBe(0);
    expect(result.current.formattedTime).toBe('0 min');
  });

  it('retorna los minutos ya transcurridos si startedAt ocurrió en el pasado', () => {
    const startedAt = '2026-11-16T11:55:00.000Z'; // 5 minutos atrás
    const { result } = renderHook(() => useKdsCountUp(startedAt));

    expect(result.current.elapsedMinutes).toBe(5);
    expect(result.current.formattedTime).toBe('5 min');
  });

  it('incrementa periódicamente el contador cada 60 segundos sin requerir llamadas HTTP', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const startedAt = '2026-11-16T12:00:00.000Z';

    const { result } = renderHook(() => useKdsCountUp(startedAt));
    expect(result.current.elapsedMinutes).toBe(0);
    expect(result.current.formattedTime).toBe('0 min');

    // Avanzar 30 segundos: no cambia de minuto
    act(() => {
      vi.advanceTimersByTime(30000);
    });
    expect(result.current.elapsedMinutes).toBe(0);
    expect(result.current.formattedTime).toBe('0 min');

    // Avanzar 30 segundos más (total 60s): cambia a 1 min
    act(() => {
      vi.advanceTimersByTime(30000);
    });
    expect(result.current.elapsedMinutes).toBe(1);
    expect(result.current.formattedTime).toBe('1 min');

    // Avanzar 60 segundos más (total 120s): cambia a 2 min
    act(() => {
      vi.advanceTimersByTime(60000);
    });
    expect(result.current.elapsedMinutes).toBe(2);
    expect(result.current.formattedTime).toBe('2 min');

    // Cero llamadas HTTP realizadas por el hook
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('retorna 0 min si startedAt es undefined o inválido', () => {
    const { result } = renderHook(() => useKdsCountUp(undefined));
    expect(result.current.elapsedMinutes).toBe(0);
    expect(result.current.formattedTime).toBe('0 min');
  });

  it('limpia el timer al desmontarse para evitar fugas de memoria', () => {
    const startedAt = '2026-11-16T12:00:00.000Z';
    const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');

    const { unmount } = renderHook(() => useKdsCountUp(startedAt));
    unmount();

    expect(clearIntervalSpy).toHaveBeenCalled();
    clearIntervalSpy.mockRestore();
  });
});
