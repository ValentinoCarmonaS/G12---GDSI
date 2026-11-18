import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LiveTrackingWidget } from '../../components/tracking/LiveTrackingWidget';
import * as pollingHook from '../../hooks/useOrderLiveTrackingPolling';
import type { OrderLiveTrackingData } from '../../types/orderTracking';

vi.mock('../../hooks/useOrderLiveTrackingPolling');

describe('T012 [US1]: LiveTrackingWidget Component Tests (Spec 021 & 016)', () => {
  const defaultImmediateTracking: OrderLiveTrackingData = {
    orderId: 'order-123',
    ticketNumber: 142,
    status: 'IN_PROGRESS',
    fulfillmentMode: 'IMMEDIATE',
    isCountdownActive: true,
    estimatedMinutesRemaining: 24,
    progressPercentage: 35,
    isReadyForPickup: false,
    message: 'Preparando tu pedido',
    serverTime: '2026-11-10T12:00:00Z',
    positionAhead: null,
    estimatedTotalMinutes: 20,
    elapsedMinutes: 5,
    remainingMinutes: 15,
    progressPercent: 35,
    specificDelayMinutes: 0,
    securityCode: null,
    startedAt: '2026-11-10T11:55:00Z',
    readyAt: null,
    items: [{ dishName: 'Milanesa con Papas', quantity: 1 }],
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
      isDisconnected: false,
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
    expect(screen.getByText(/15\s*min|24\s*min/i)).toBeInTheDocument();
  });

  it('renders QUEUED state with badge "En cola en cocina", "Tenés N pedidos por delante", and suppresses timer/progressbar (RF-003, RF-005, RF-006)', () => {
    const queuedTracking: OrderLiveTrackingData = {
      ...defaultImmediateTracking,
      isCountdownActive: false,
      positionAhead: 3,
      estimatedMinutesRemaining: null,
      progressPercentage: null,
      remainingMinutes: null,
      progressPercent: null,
      message: 'En cola en cocina',
    };

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: queuedTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/#142/)).toBeInTheDocument();
    expect(screen.getByText(/En cola en cocina/i)).toBeInTheDocument();
    expect(screen.getByText(/Tenés 3 pedidos por delante/i)).toBeInTheDocument();
    // Temporizador y barra estrictamente ocultos
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.queryByText(/min restante/i)).not.toBeInTheDocument();
  });

  it('renders literally "Tenés 0 pedidos por delante" when positionAhead is 0 in QUEUED state (RF-005)', () => {
    const topOfQueueTracking: OrderLiveTrackingData = {
      ...defaultImmediateTracking,
      isCountdownActive: false,
      positionAhead: 0,
      estimatedMinutesRemaining: null,
      progressPercentage: null,
      remainingMinutes: null,
      progressPercent: null,
      message: 'En cola en cocina',
    };

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: topOfQueueTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/Tenés 0 pedidos por delante/i)).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('renders IN_PREPARATION state with active timer, progressbar and removes queue position (RF-008, RF-009)', () => {
    const inPrepTracking: OrderLiveTrackingData = {
      ...defaultImmediateTracking,
      isCountdownActive: true,
      positionAhead: null,
      remainingMinutes: 14,
      progressPercent: 45,
      message: 'Cocinando / En preparación',
    };

    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: inPrepTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/Cocinando \/ En preparación|Preparando tu pedido/i)).toBeInTheDocument();
    expect(screen.getByText(/14\s*min/i)).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '45');
    expect(screen.queryByText(/pedidos por delante/i)).not.toBeInTheDocument();
  });

  it('renders READY_FOR_PICKUP state with green callout, 4-digit security code, dish items, and purges timer/progressbar (RF-011, RF-021)', () => {
    const readyTracking: OrderLiveTrackingData = {
      ...defaultImmediateTracking,
      status: 'READY_FOR_PICKUP',
      isReadyForPickup: true,
      isCountdownActive: false,
      positionAhead: null,
      remainingMinutes: null,
      progressPercent: null,
      estimatedMinutesRemaining: null,
      progressPercentage: null,
      securityCode: '4821',
      items: [
        { dishName: 'Hamburguesa Completa', quantity: 2 },
        { dishName: 'Papas Fritas', quantity: 1 },
      ],
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
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByText(/#142/)).toBeInTheDocument();
    expect(screen.getByText(/¡Tu pedido está listo para retirar!/i)).toBeInTheDocument();
    expect(screen.getByText('4 8 2 1')).toBeInTheDocument(); // Spec 022 RF-003: spaced digits
    expect(screen.getByText(/Hamburguesa Completa/i)).toBeInTheDocument();
    expect(screen.getByText(/Papas Fritas/i)).toBeInTheDocument();
    // Métricas purgadas
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.queryByText(/pedidos por delante/i)).not.toBeInTheDocument();
  });

  it('displays subtle ConnectionStatusBadge when isDisconnected is true (RF-026, SC-008)', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: defaultImmediateTracking,
      isLoading: false,
      error: new Error('Network timeout'),
      refetch: vi.fn(),
      isDisconnected: true,
    });

    render(
      <LiveTrackingWidget
        orderId="order-123"
        ticketNumber={142}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByTestId('connection-status-badge')).toBeInTheDocument();
    expect(screen.getByText(/Sin conexión — Reintentando.../i)).toBeInTheDocument();
  });
});
