import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LiveTrackingWidget } from '../../components/tracking/LiveTrackingWidget';
import * as pollingHook from '../../hooks/useOrderLiveTrackingPolling';
import type { OrderLiveTrackingData } from '../../types/orderTracking';

vi.mock('../../hooks/useOrderLiveTrackingPolling');

describe('T021 [US2]: LiveTrackingWidget Delay Integration Tests (Spec 021 - COM-7.3.2 BIS)', () => {
  const baseTracking: OrderLiveTrackingData = {
    orderId: 'order-123',
    ticketNumber: 104,
    status: 'IN_PROGRESS',
    fulfillmentMode: 'IMMEDIATE',
    isCountdownActive: true,
    estimatedMinutesRemaining: 15,
    progressPercentage: 40,
    isReadyForPickup: false,
    message: 'Cocinando / En preparación',
    serverTime: new Date().toISOString(),
    positionAhead: null,
    estimatedTotalMinutes: 20,
    elapsedMinutes: 5,
    remainingMinutes: 15,
    progressPercent: 40,
    specificDelayMinutes: 0,
    securityCode: null,
    startedAt: new Date(Date.now() - 300000).toISOString(),
    readyAt: null,
    items: [{ dishName: 'Burger Porteña', quantity: 1 }],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reflects reactive increase from M -> M + 5 minutes and recalculates progress bar (RF-016, SC-004)', () => {
    const updatedTrackingWithDelay: OrderLiveTrackingData = {
      ...baseTracking,
      specificDelayMinutes: 5,
      estimatedTotalMinutes: 25,
      remainingMinutes: 20, // 15 + 5 = 20 min
      progressPercent: 32, // Progress decreases proportionally
    };

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: updatedTrackingWithDelay,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={104}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/20\s*min/i)).toBeInTheDocument();
    const progressBar = screen.getByRole('progressbar');
    expect(progressBar).toHaveAttribute('aria-valuenow', '32');
  });

  it('strictly preserves floor at 1 min and progress ceiling at 92% when preparation exceeds estimated time (RF-017, SC-006)', () => {
    const exceededTracking: OrderLiveTrackingData = {
      ...baseTracking,
      elapsedMinutes: 35,
      estimatedTotalMinutes: 25,
      remainingMinutes: 1, // Floor at 1 min
      progressPercent: 92, // Ceiling at 92%
      message: 'Dando los últimos toques a tu pedido',
    };

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: exceededTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={104}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/1\s*min/i)).toBeInTheDocument();
    expect(screen.getByText(/Dando los últimos toques a tu pedido/i)).toBeInTheDocument();
    const progressBar = screen.getByRole('progressbar');
    expect(progressBar).toHaveAttribute('aria-valuenow', '92');
  });

  it('completely purges delay indicators, timer and progress bar when order is marked READY_FOR_PICKUP (RF-019)', () => {
    const readyTracking: OrderLiveTrackingData = {
      ...baseTracking,
      status: 'READY_FOR_PICKUP',
      isReadyForPickup: true,
      remainingMinutes: null,
      progressPercent: null,
      estimatedMinutesRemaining: null,
      progressPercentage: null,
      specificDelayMinutes: 10, // Delay accumulated in kitchen is purged from diner UI
      securityCode: '9182',
      message: '¡Tu pedido está listo para retirar!',
    };

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: readyTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={104}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/¡Tu pedido está listo para retirar!/i)).toBeInTheDocument();
    expect(screen.getByText('9 1 8 2')).toBeInTheDocument(); // Spec 022 RF-003: spaced digits
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.queryByText(/min restante|min/i)).not.toBeInTheDocument();
  });
});
