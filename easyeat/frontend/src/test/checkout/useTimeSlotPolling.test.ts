import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useTimeSlotPolling } from '../../hooks/useTimeSlotPolling';
import * as orderApi from '../../api/orderApi';
import { PickupSlotCatalogResponse } from '../../types/order';

vi.mock('../../api/orderApi', () => ({
  fetchPickupSlots: vi.fn(),
}));

describe('useTimeSlotPolling Hook', () => {
  const mockCatalog: PickupSlotCatalogResponse = {
    establishmentId: 'est-123',
    date: '2026-11-03',
    timezone: 'America/Argentina/Buenos_Aires',
    isOperatingDay: true,
    leadTimeMinutes: 30,
    slots: [
      {
        startTime: '12:30:00',
        endTime: '12:45:00',
        date: '2026-11-03',
        maxCapacity: 10,
        bookedCount: 5,
        occupancyPercentage: 50,
        status: 'AVAILABLE',
        isSelectable: true,
      },
    ],
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('fetches slots immediately on mount and executes periodic polling at configured interval (5-10s)', async () => {
    vi.mocked(orderApi.fetchPickupSlots).mockResolvedValue(mockCatalog);

    const { result } = renderHook(() =>
      useTimeSlotPolling({
        establishmentId: 'est-123',
        date: '2026-11-03',
        intervalMs: 5000,
        enabled: true,
      })
    );

    // Initial loading state
    expect(result.current.isLoading).toBe(true);

    await act(async () => {
      await Promise.resolve();
    });

    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(1);
    expect(result.current.slots).toEqual(mockCatalog.slots);
    expect(result.current.isLoading).toBe(false);

    // Advance 5 seconds
    await act(async () => {
      vi.advanceTimersByTime(5000);
      await Promise.resolve();
    });

    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(2);

    // Advance another 5 seconds
    await act(async () => {
      vi.advanceTimersByTime(5000);
      await Promise.resolve();
    });

    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(3);
  });

  it('performs background refresh without resetting isLoading to true (no screen flashing)', async () => {
    vi.mocked(orderApi.fetchPickupSlots).mockResolvedValue(mockCatalog);

    const { result } = renderHook(() =>
      useTimeSlotPolling({
        establishmentId: 'est-123',
        date: '2026-11-03',
        intervalMs: 5000,
        enabled: true,
      })
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isRefreshing).toBe(false);

    // During background polling
    let resolveSecondCall: (value: PickupSlotCatalogResponse) => void;
    vi.mocked(orderApi.fetchPickupSlots).mockReturnValue(
      new Promise((resolve) => {
        resolveSecondCall = resolve;
      })
    );

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isRefreshing).toBe(true);

    await act(async () => {
      resolveSecondCall!(mockCatalog);
      await Promise.resolve();
    });

    expect(result.current.isRefreshing).toBe(false);
  });

  it('stops polling when tab is hidden and polls immediately when tab regains focus/visibility', async () => {
    vi.mocked(orderApi.fetchPickupSlots).mockResolvedValue(mockCatalog);

    let isDocumentVisible = true;
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => (isDocumentVisible ? 'visible' : 'hidden'),
    });

    renderHook(() =>
      useTimeSlotPolling({
        establishmentId: 'est-123',
        date: '2026-11-03',
        intervalMs: 5000,
        enabled: true,
      })
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(1);

    // Hide document
    isDocumentVisible = false;
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });

    // Advance 10s while hidden - should not poll
    await act(async () => {
      vi.advanceTimersByTime(10000);
      await Promise.resolve();
    });

    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(1);

    // Regain visibility
    isDocumentVisible = true;
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });

    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(2);
  });

  it('cleans up interval and aborts pending requests on unmount', async () => {
    vi.mocked(orderApi.fetchPickupSlots).mockResolvedValue(mockCatalog);

    const { unmount } = renderHook(() =>
      useTimeSlotPolling({
        establishmentId: 'est-123',
        date: '2026-11-03',
        intervalMs: 5000,
        enabled: true,
      })
    );

    await act(async () => {
      await Promise.resolve();
    });

    unmount();

    await act(async () => {
      vi.advanceTimersByTime(10000);
      await Promise.resolve();
    });

    // Still only 1 call
    expect(orderApi.fetchPickupSlots).toHaveBeenCalledTimes(1);
  });
});
