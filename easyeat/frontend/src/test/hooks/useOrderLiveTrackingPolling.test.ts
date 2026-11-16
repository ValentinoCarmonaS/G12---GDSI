import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useOrderLiveTrackingPolling } from '../../hooks/useOrderLiveTrackingPolling';
import { orderTrackingApi } from '../../api/orderTrackingApi';
import type { OrderLiveTrackingData } from '../../types/orderTracking';

vi.mock('../../api/orderTrackingApi', () => ({
  orderTrackingApi: {
    getLiveTracking: vi.fn(),
  },
}));

describe('T011 [US1]: useOrderLiveTrackingPolling Hook Unit Tests (Spec 021 & 016)', () => {
  const mockTrackingData: OrderLiveTrackingData = {
    orderId: 'order-123',
    ticketNumber: 142,
    status: 'IN_PROGRESS',
    fulfillmentMode: 'IMMEDIATE',
    isCountdownActive: true,
    estimatedMinutesRemaining: 18,
    progressPercentage: 35,
    isReadyForPickup: false,
    message: 'Tu comida se está preparando en la cocina.',
    serverTime: new Date().toISOString(),
    positionAhead: null,
    estimatedTotalMinutes: 20,
    elapsedMinutes: 2,
    remainingMinutes: 18,
    progressPercent: 35,
    specificDelayMinutes: 0,
    securityCode: null,
    startedAt: new Date(Date.now() - 120000).toISOString(),
    readyAt: null,
    items: [{ dishName: 'Milanesa con Papas', quantity: 1 }],
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('polls getLiveTracking with regular 6000ms cadence during active tracking (RF-022, SC-007)', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(mockTrackingData);

    const { result } = renderHook(() => useOrderLiveTrackingPolling('order-123'));

    expect(result.current.isLoading).toBe(true);

    // Initial fetch at t=0
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(1);

    // Advance 6000ms
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(2);

    // Advance another 6000ms
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(3);
  });

  it('activates isDisconnected = true after sustained network errors exceeding 15 seconds (RF-026, SC-008)', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking)
      .mockResolvedValueOnce(mockTrackingData) // Initial success
      .mockRejectedValue(new Error('Network offline')); // Subsequent failures

    const { result } = renderHook(() => useOrderLiveTrackingPolling('order-123'));

    // t = 0: initial success
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.isDisconnected).toBe(false);
    expect(result.current.tracking).toEqual(mockTrackingData);

    // t = 6000: first failure (elapsed 6s <= 15s) -> isDisconnected remains false
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(result.current.isDisconnected).toBe(false);
    // Preserves last known valid tracking data
    expect(result.current.tracking).toEqual(mockTrackingData);

    // t = 12000: second failure (elapsed 12s <= 15s) -> isDisconnected remains false
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(result.current.isDisconnected).toBe(false);

    // t = 18000: third failure (elapsed 18s > 15s threshold) -> isDisconnected becomes true
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(result.current.isDisconnected).toBe(true);
    // Still preserves last known valid state
    expect(result.current.tracking).toEqual(mockTrackingData);

    // Recovery at t = 24000: network resumes with success -> isDisconnected clears to false
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValueOnce(mockTrackingData);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(result.current.isDisconnected).toBe(false);
  });

  it('pauses polling when Page Visibility API reports hidden and resumes on visible', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(mockTrackingData);

    renderHook(() => useOrderLiveTrackingPolling('order-123', mockTrackingData));

    // Simulate tab hidden
    Object.defineProperty(document, 'visibilityState', {
      value: 'hidden',
      writable: true,
    });
    document.dispatchEvent(new Event('visibilitychange'));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(12000);
    });

    // Should not poll while hidden
    expect(orderTrackingApi.getLiveTracking).not.toHaveBeenCalled();

    // Tab becomes visible again
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
    });
    document.dispatchEvent(new Event('visibilitychange'));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(1);
  });

  it('halts polling permanently once order reaches READY_FOR_PICKUP terminal state', async () => {
    const readyData: OrderLiveTrackingData = {
      ...mockTrackingData,
      status: 'READY_FOR_PICKUP',
      isReadyForPickup: true,
      remainingMinutes: null,
      progressPercent: null,
    };

    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(readyData);

    renderHook(() => useOrderLiveTrackingPolling('order-123', readyData));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(18000);
    });

    expect(orderTrackingApi.getLiveTracking).not.toHaveBeenCalled();
  });
});
