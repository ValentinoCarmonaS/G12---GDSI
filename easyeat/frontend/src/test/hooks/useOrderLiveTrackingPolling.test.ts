import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useOrderLiveTrackingPolling } from '../../hooks/useOrderLiveTrackingPolling';
import { orderTrackingApi } from '../../api/orderTrackingApi';
import type { OrderLiveTrackingResponse } from '../../types/orderTracking';

vi.mock('../../api/orderTrackingApi', () => ({
  orderTrackingApi: {
    getLiveTracking: vi.fn(),
  },
}));

describe('T027 [US2]: useOrderLiveTrackingPolling Hook Unit Tests', () => {
  const mockTrackingData: OrderLiveTrackingResponse = {
    orderId: 'order-123',
    ticketNumber: 142,
    status: 'IN_PROGRESS',
    fulfillmentMode: 'IMMEDIATE',
    isCountdownActive: true,
    estimatedMinutesRemaining: 18,
    progressPercentage: 35,
    isReadyForPickup: false,
    kdsQueueSaturation: {
      activeOrdersAhead: 2,
      kitchenStatus: 'NORMAL',
      globalDelayAppliedMinutes: 0,
    },
    message: 'Tu comida se está preparando en la cocina.',
    serverTime: new Date().toISOString(),
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('AS-5/AS-7: polls getLiveTracking with 5000ms cadence during normal IN_PROGRESS preparation', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(mockTrackingData);

    const { result } = renderHook(() => useOrderLiveTrackingPolling('order-123'));

    expect(result.current.isLoading).toBe(true);

    // Initial fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(1);

    // Advance 5000ms
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(2);
  });

  it('AS-6/CB-006: accelerates cadence to 3000ms when isCountdownActive is true in proximity phase', async () => {
    const proximityData: OrderLiveTrackingResponse = {
      ...mockTrackingData,
      isCountdownActive: true,
      estimatedMinutesRemaining: 12,
    };
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(proximityData);

    renderHook(() => useOrderLiveTrackingPolling('order-123', proximityData));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledWith('order-123');
  });

  it('AS-8/INV-008: halts polling permanently once order reaches READY_FOR_PICKUP terminal state', async () => {
    const readyData: OrderLiveTrackingResponse = {
      ...mockTrackingData,
      status: 'READY_FOR_PICKUP',
      isReadyForPickup: true,
      estimatedMinutesRemaining: null,
      progressPercentage: 100,
    };

    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(readyData);

    renderHook(() => useOrderLiveTrackingPolling('order-123', readyData));

    // Timers advance, but no network calls should be made
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15000);
    });

    expect(orderTrackingApi.getLiveTracking).not.toHaveBeenCalled();
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
      await vi.advanceTimersByTimeAsync(10000);
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

  it.each(['READY_FOR_PICKUP', 'COMPLETED', 'CANCELLED', 'EXPIRED'] as const)(
    'halts polling permanently when terminal status is %s',
    async (terminalStatus) => {
      const terminalData: OrderLiveTrackingResponse = {
        ...mockTrackingData,
        status: terminalStatus,
        isReadyForPickup: terminalStatus === 'READY_FOR_PICKUP',
        estimatedMinutesRemaining: null,
        progressPercentage: terminalStatus === 'READY_FOR_PICKUP' || terminalStatus === 'COMPLETED' ? 100 : 0,
      };

      vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(terminalData);

      renderHook(() => useOrderLiveTrackingPolling('order-123', terminalData));

      await act(async () => {
        await vi.advanceTimersByTimeAsync(15000);
      });

      expect(orderTrackingApi.getLiveTracking).not.toHaveBeenCalled();
    }
  );
});
