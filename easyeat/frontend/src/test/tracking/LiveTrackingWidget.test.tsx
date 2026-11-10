import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LiveTrackingWidget } from '../../components/tracking/LiveTrackingWidget';
import * as pollingHook from '../../hooks/useOrderLiveTrackingPolling';
import type { OrderLiveTrackingResponse } from '../../types/orderTracking';

vi.mock('../../hooks/useOrderLiveTrackingPolling');

describe('T028 [US2]: LiveTrackingWidget Component Tests', () => {
  const defaultImmediateTracking: OrderLiveTrackingResponse = {
    orderId: 'order-123',
    ticketNumber: 142,
    status: 'IN_PROGRESS',
    fulfillmentMode: 'IMMEDIATE',
    isCountdownActive: true,
    estimatedMinutesRemaining: 24,
    progressPercentage: 35,
    isReadyForPickup: false,
    kdsQueueSaturation: {
      activeOrdersAhead: 2,
      kitchenStatus: 'NORMAL',
      globalDelayAppliedMinutes: 0,
    },
    message: 'Preparando tu pedido',
    serverTime: '2026-11-10T12:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders progress bar and dynamic estimated minutes for IMMEDIATE mode (FR-006, AS-5)', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: defaultImmediateTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    const progressBar = screen.getByRole('progressbar');
    expect(progressBar).toBeInTheDocument();
    expect(progressBar).toHaveAttribute('aria-valuenow', '35');
    expect(screen.getByText(/24\s*min/i)).toBeInTheDocument();
    expect(screen.getByText(/Preparando tu pedido/i)).toBeInTheDocument();
  });

  it('updates remaining time and progress bar silently without popups or alerts (FR-007, AS-7, CB-007)', () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});

    const { rerender } = render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        estimatedMinutesRemaining: 18,
        progressPercentage: 55,
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    rerender(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/18\s*min/i)).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '55');
    expect(alertSpy).not.toHaveBeenCalled();
  });

  it('displays kitchen saturation notice when kitchen is experiencing high demand', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        kdsQueueSaturation: {
          activeOrdersAhead: 5,
          kitchenStatus: 'HIGH_DEMAND',
          globalDelayAppliedMinutes: 10,
        },
        message: 'Cocina con alta demanda (demora de 10 min)',
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getAllByText(/alta demanda/i).length).toBeGreaterThanOrEqual(1);
  });

  it('renders last touches state when order reaches capped progress (AS-5, INV-005)', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        estimatedMinutesRemaining: 1,
        progressPercentage: 92,
        message: 'Dando los últimos toques a tu pedido',
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/Dando los últimos toques a tu pedido/i)).toBeInTheDocument();
    expect(screen.getByText(/1\s*min/i)).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '92');
  });

  it('switches to ready for pickup callout when order is ready (AS-8)', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        status: 'READY_FOR_PICKUP',
        isReadyForPickup: true,
        estimatedMinutesRemaining: null,
        progressPercentage: 100,
        message: '¡Tu pedido está listo para retirar!',
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/#142/)).toBeInTheDocument();
    expect(screen.getByText(/listo para retirar/i)).toBeInTheDocument();
  });
});
