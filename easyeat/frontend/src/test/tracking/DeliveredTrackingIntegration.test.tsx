import { render, screen, waitFor, act, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LiveTrackingWidget } from '../../components/tracking/LiveTrackingWidget';
import { OrderConfirmationTrackingPage } from '../../pages/OrderConfirmationTrackingPage';
import { orderTrackingApi } from '../../api/orderTrackingApi';
import type { OrderConfirmationTrackingResponse, OrderLiveTrackingData } from '../../types/orderTracking';

vi.mock('../../api/orderTrackingApi', () => ({
  orderTrackingApi: {
    getConfirmationTracking: vi.fn(),
    getLiveTracking: vi.fn(),
  },
}));

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 'user-123', email: 'diner@test.com', role: 'COMENSAL' },
    logout: vi.fn(),
  }),
}));

vi.mock('../../api/profileApi', () => ({
  profileApi: { getProfile: vi.fn().mockResolvedValue({ avatarUrl: null }) },
}));

vi.mock('../../api/adminNotificationApi', () => ({
  adminNotificationApi: {
    getUnreadCount: vi.fn().mockResolvedValue(0),
    getNotifications: vi.fn().mockResolvedValue([]),
  },
}));

/**
 * Spec 022 T029 (US2, RF-014, RF-015, edge cases 8 and 12, contract 03 §4): the tracking screen switches to
 * DeliveredOrderCallout on COMPLETED, without any PIN, while the page keeps its full purchase summary.
 */
describe('Delivered order on the tracking screen (Spec 022 T029)', () => {
  const FINAL_MESSAGE = 'Pedido Entregado — ¡Que disfrutes tu comida!';
  const ready: OrderLiveTrackingData = {
    orderId: 'order-uuid-999',
    ticketNumber: 142,
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
    items: [{ dishName: 'Hamburguesa Clásica EasyEat', quantity: 2 }],
  };
  const completed: OrderLiveTrackingData = {
    ...ready,
    status: 'COMPLETED',
    isReadyForPickup: false,
    message: FINAL_MESSAGE,
    securityCode: null,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders DeliveredOrderCallout, and no ready callout or PIN, for a COMPLETED order', () => {
    render(
      <LiveTrackingWidget orderId="order-uuid-999" ticketNumber={142} fulfillmentMode="IMMEDIATE" initialTracking={completed} />
    );

    const callout = screen.getByTestId('delivered-order-callout');
    expect(within(callout).getByText(FINAL_MESSAGE)).toBeInTheDocument();
    expect(within(callout).getByText('#142')).toBeInTheDocument();
    expect(screen.queryByTestId('ready-for-pickup-callout')).not.toBeInTheDocument();
    expect(screen.queryByTestId('security-pin-display')).not.toBeInTheDocument();
  });

  it('switches live from the ready callout with PIN to the delivered callout without it', async () => {
    vi.useFakeTimers();
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(completed);

    render(
      <LiveTrackingWidget orderId="order-uuid-999" ticketNumber={142} fulfillmentMode="IMMEDIATE" initialTracking={ready} />
    );
    expect(screen.getByTestId('security-pin-display').textContent).toBe('4 8 2 1');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });

    expect(screen.getByTestId('delivered-order-callout')).toBeInTheDocument();
    expect(screen.queryByTestId('security-pin-display')).not.toBeInTheDocument();
    expect(screen.queryByText('4 8 2 1')).not.toBeInTheDocument();
  });

  it('keeps the full purchase summary on the page once the order is delivered (contract 03 §4)', async () => {
    const confirmation: OrderConfirmationTrackingResponse = {
      orderId: 'order-uuid-999',
      ticketNumber: 142,
      status: 'COMPLETED',
      approvalStatus: 'APPROVED',
      establishmentId: 'est-123',
      establishmentName: 'Café Central Palermo',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-18',
      timeSlotStart: '13:00:00',
      timeSlotEnd: '13:15:00',
      totalAmount: 4200,
      items: [{ dishId: 'dish-1', dishName: 'Hamburguesa Clásica EasyEat', quantity: 2, unitPrice: 2100, subtotal: 4200 }],
      trackingUrl: '/pedidos/order-uuid-999/seguimiento',
      serverTime: new Date().toISOString(),
    };
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValue(confirmation);
    vi.mocked(orderTrackingApi.getLiveTracking).mockResolvedValue(completed);

    render(
      <MemoryRouter initialEntries={['/pedidos/order-uuid-999/seguimiento']}>
        <Routes>
          <Route path="/pedidos/:orderId/seguimiento" element={<OrderConfirmationTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('delivered-order-callout')).toBeInTheDocument();
    });
    expect(screen.getByText(/hamburguesa clásica easyeat/i)).toBeInTheDocument();
    expect(screen.getAllByText(/\$4200|\$4,200/).length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByTestId('security-pin-display')).not.toBeInTheDocument();
    expect(screen.queryByText('Código de retiro')).not.toBeInTheDocument();
  });
});
