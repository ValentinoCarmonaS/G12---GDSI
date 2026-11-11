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
    expect(screen.getByText(/Demora operativa en cocina \(\+10 min\)/i)).toBeInTheDocument();
    expect(screen.getByText(/El equipo está elaborando 5 pedidos por delante en cola/i)).toBeInTheDocument();
  });

  it('displays adaptive notice when kitchen has delay with 0 orders ahead (AS-7)', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        kdsQueueSaturation: {
          activeOrdersAhead: 0,
          kitchenStatus: 'DELAYED',
          globalDelayAppliedMinutes: 15,
        },
        message: 'Preparando tu pedido',
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

    expect(screen.getByText(/Demora operativa en cocina \(\+15 min\)/i)).toBeInTheDocument();
    expect(
      screen.getByText(/El equipo de cocina extendió el tiempo de preparación para asegurar la calidad de tu pedido/i)
    ).toBeInTheDocument();
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

  it('renders in-slot last touches state for scheduled order when minutesRemaining is 0 (AS-6, CB-006)', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        orderId: 'order-123',
        ticketNumber: 142,
        status: 'IN_PROGRESS',
        fulfillmentMode: 'SCHEDULED',
        isCountdownActive: true,
        estimatedMinutesRemaining: 0,
        progressPercentage: 92,
        isReadyForPickup: false,
        kdsQueueSaturation: undefined,
        message: '¡Tu turno ha comenzado! La cocina está dando los últimos toques a tu pedido.',
        serverTime: '2026-11-10T13:30:00Z',
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="SCHEDULED"
        timeSlotStart="13:30:00"
        timeSlotEnd="13:45:00"
      />
    );

    expect(screen.getAllByText(/¡Tu turno ha comenzado!/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Unos instantes/i)).toBeInTheDocument();
    expect(screen.getByText(/13:30 - 13:45 hs/i)).toBeInTheDocument();
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

  it('renders safely when ticketNumber is null without dangling # or fake digits in preparation and ready callout', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        ticketNumber: null as unknown as number,
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    const { rerender } = render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={null as unknown as number}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.queryByText(/#/)).not.toBeInTheDocument();
    expect(screen.getByText(/Retiro en mostrador/i)).toBeInTheDocument();

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...defaultImmediateTracking,
        ticketNumber: null as unknown as number,
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

    rerender(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={null as unknown as number}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.queryByText(/#/)).not.toBeInTheDocument();
    expect(screen.getByText(/listo para retirar/i)).toBeInTheDocument();
  });
});
