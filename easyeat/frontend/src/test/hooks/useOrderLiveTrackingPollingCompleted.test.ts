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

/**
 * Spec 022 T021 (US2, RF-017, edge case 8, invariant 12, research D-11): polling keeps running while the
 * order is READY_FOR_PICKUP, so the diner at the counter sees the delivery live, and stops for good on
 * COMPLETED (and on the other terminal states CANCELLED and EXPIRED).
 */
describe('useOrderLiveTrackingPolling terminal stop on COMPLETED (Spec 022 T021)', () => {
  const ready: OrderLiveTrackingData = {
    orderId: 'order-123',
    ticketNumber: 42,
    status: 'READY_FOR_PICKUP',
    fulfillmentMode: 'IMMEDIATE',
    isCountdownActive: false,
    estimatedMinutesRemaining: null,
    progressPercentage: 100,
    isReadyForPickup: true,
    message: '¡Tu pedido está listo para retirar! Por favor, acercate al mostrador para retirar tu comida.',
    serverTime: new Date().toISOString(),
    positionAhead: null,
    estimatedTotalMinutes: null,
    elapsedMinutes: null,
    remainingMinutes: null,
    progressPercent: null,
    specificDelayMinutes: null,
    securityCode: '4821',
    startedAt: null,
    readyAt: new Date().toISOString(),
    items: [{ dishName: 'Milanesa con Papas', quantity: 1 }],
  };
  const completed: OrderLiveTrackingData = {
    ...ready,
    status: 'COMPLETED',
    isReadyForPickup: false,
    message: 'Pedido Entregado — ¡Que disfrutes tu comida!',
    securityCode: null,
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps polling every 6000ms while the order is READY_FOR_PICKUP', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(ready);

    renderHook(() => useOrderLiveTrackingPolling('order-123'));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(12000);
    });
    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(3);
  });

  it('keeps polling when the screen opens on an already READY_FOR_PICKUP order', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(ready);

    renderHook(() => useOrderLiveTrackingPolling('order-123', ready));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });

    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(1);
  });

  it('switches to COMPLETED and never polls again', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking)
      .mockResolvedValueOnce(ready)
      .mockResolvedValueOnce(completed)
      .mockResolvedValue(completed);

    const { result } = renderHook(() => useOrderLiveTrackingPolling('order-123'));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(result.current.tracking?.status).toBe('COMPLETED');
    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60000);
    });
    expect(orderTrackingApi.getLiveTracking).toHaveBeenCalledTimes(2);
  });

  it('does not refetch on visibility return once COMPLETED', async () => {
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(completed);

    renderHook(() => useOrderLiveTrackingPolling('order-123', completed));
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(18000);
    });

    expect(orderTrackingApi.getLiveTracking).not.toHaveBeenCalled();
  });

  it.each(['CANCELLED', 'EXPIRED'] as const)('still stops for good on %s', async (status) => {
    const terminal: OrderLiveTrackingData = { ...ready, status, isReadyForPickup: false, securityCode: null };
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(terminal);

    renderHook(() => useOrderLiveTrackingPolling('order-123', terminal));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(18000);
    });

    expect(orderTrackingApi.getLiveTracking).not.toHaveBeenCalled();
  });
});
