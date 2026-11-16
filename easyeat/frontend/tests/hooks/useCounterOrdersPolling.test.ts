import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useCounterOrdersPolling } from '../../src/hooks/useCounterOrdersPolling';
import { counterApi } from '../../src/api/counterApi';
import type { CounterReadyOrder } from '../../src/types/counter';

vi.mock('../../src/api/counterApi', () => ({
  counterApi: {
    fetchCounterOrders: vi.fn(),
  },
  fetchCounterOrders: vi.fn(),
}));

const sampleOrder: CounterReadyOrder = {
  ticketId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
  orderId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  ticketNumber: 104,
  timeSlot: {
    start: '13:00',
    end: '13:15',
  },
  readyAt: '2026-11-15T19:30:00Z',
  customer: {
    userId: 'c71e2289-4b68-45e0-a7d1-e6e7d6928e01',
    fullName: 'Valentino Carmona',
    photoUrl: '/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar',
  },
  securityCode: '4821',
  items: [
    {
      quantity: 2,
      dishName: 'Hamburguesa Completa con Papas',
    },
  ],
};

describe('useCounterOrdersPolling (T015, US2, CHF-4.2.3, RF-014)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      writable: true,
      value: 'visible',
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('T015: executes immediate initial fetch and polls periodically every 4 seconds (4000ms) with AbortSignal', async () => {
    vi.mocked(counterApi.fetchCounterOrders).mockResolvedValue([sampleOrder]);

    const { result } = renderHook(() => useCounterOrdersPolling());

    await act(async () => {
      await Promise.resolve();
    });

    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(1);
    expect(counterApi.fetchCounterOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({
        signal: expect.any(AbortSignal),
      })
    );
    expect(result.current.orders).toEqual([sampleOrder]);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.isDisconnected).toBe(false);

    // Advance 3999ms -> should not poll yet
    await act(async () => {
      vi.advanceTimersByTime(3999);
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(1);

    // Advance 1ms (total 4000ms) -> triggers second poll
    await act(async () => {
      vi.advanceTimersByTime(1);
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(2);

    // Advance another 4000ms -> triggers third poll
    await act(async () => {
      vi.advanceTimersByTime(4000);
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(3);
  });

  it('T015: aborts active in-flight request via AbortController and clears interval on unmount', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.mocked(counterApi.fetchCounterOrders).mockImplementation(async (options) => {
      capturedSignal = options?.signal;
      return new Promise(() => {
        // Pending request
      });
    });

    const { unmount } = renderHook(() => useCounterOrdersPolling());

    expect(capturedSignal).toBeDefined();
    expect(capturedSignal?.aborted).toBe(false);

    unmount();

    expect(capturedSignal?.aborted).toBe(true);

    await act(async () => {
      vi.advanceTimersByTime(8000);
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(1);
  });

  it('T015: pauses polling when document.visibilityState becomes hidden and immediately resumes when visible', async () => {
    vi.mocked(counterApi.fetchCounterOrders).mockResolvedValue([sampleOrder]);

    renderHook(() => useCounterOrdersPolling());

    await act(async () => {
      await Promise.resolve();
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(1);

    // Tab becomes hidden
    act(() => {
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        writable: true,
        value: 'hidden',
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    // Advance 12 seconds while hidden -> zero additional polls
    await act(async () => {
      vi.advanceTimersByTime(12_000);
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(1);

    // Tab becomes visible again -> immediate poll + resumes 4s cycle
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        writable: true,
        value: 'visible',
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(2);

    await act(async () => {
      vi.advanceTimersByTime(4000);
    });
    expect(counterApi.fetchCounterOrders).toHaveBeenCalledTimes(3);
  });

  it('T016: activates isDisconnected = true only after >15s without a successful poll while preserving previous orders, and resets to false on first recovery', async () => {
    const secondOrder: CounterReadyOrder = {
      ...sampleOrder,
      ticketId: 'a15c329b-81d3-4e89-b620-7f2cb14197e2',
      orderId: '50e932b1-12c8-4ec7-9104-58a47de0281b',
      ticketNumber: 105,
    };

    // Initial poll at t=0s succeeds
    vi.mocked(counterApi.fetchCounterOrders).mockResolvedValueOnce([sampleOrder]);

    const { result } = renderHook(() => useCounterOrdersPolling());

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.orders).toEqual([sampleOrder]);
    expect(result.current.isDisconnected).toBe(false);

    // Subsequent polls fail with network error at t=4s, t=8s, t=12s (<= 15s threshold)
    vi.mocked(counterApi.fetchCounterOrders).mockRejectedValue(
      new Error('Network Error')
    );

    await act(async () => {
      vi.advanceTimersByTime(4000); // t = 4s
    });
    expect(result.current.isDisconnected).toBe(false);
    expect(result.current.orders).toEqual([sampleOrder]);

    await act(async () => {
      vi.advanceTimersByTime(4000); // t = 8s
    });
    expect(result.current.isDisconnected).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(4000); // t = 12s
    });
    expect(result.current.isDisconnected).toBe(false);
    expect(result.current.orders).toEqual([sampleOrder]);

    // At t = 16s (> 15s since last successful sync at t=0s), isDisconnected becomes true
    await act(async () => {
      vi.advanceTimersByTime(4000); // t = 16s
    });
    expect(result.current.isDisconnected).toBe(true);
    // Previously loaded orders remain preserved and visible (RF-018, Caso borde 1)
    expect(result.current.orders).toEqual([sampleOrder]);

    // Connection recovers on next poll at t = 20s -> isDisconnected resets to false immediately
    vi.mocked(counterApi.fetchCounterOrders).mockResolvedValueOnce([
      sampleOrder,
      secondOrder,
    ]);

    await act(async () => {
      vi.advanceTimersByTime(4000); // t = 20s
    });
    expect(result.current.isDisconnected).toBe(false);
    expect(result.current.orders).toEqual([sampleOrder, secondOrder]);
  });
});
