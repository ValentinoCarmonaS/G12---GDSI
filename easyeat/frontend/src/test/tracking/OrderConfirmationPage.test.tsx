import { render, screen, waitFor, act, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { OrderConfirmationTrackingPage } from '../../pages/OrderConfirmationTrackingPage';
import { orderTrackingApi } from '../../api/orderTrackingApi';
import { OrderConfirmationTrackingResponse } from '../../types/orderTracking';

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
  profileApi: {
    getProfile: vi.fn().mockResolvedValue({ avatarUrl: null }),
  },
}));

vi.mock('../../api/adminNotificationApi', () => ({
  adminNotificationApi: {
    getUnreadCount: vi.fn().mockResolvedValue(0),
    getNotifications: vi.fn().mockResolvedValue([]),
  },
}));

describe('T013 [US1]: Green Success Confirmation Screen and Order Summary', () => {
  const mockOrderData: OrderConfirmationTrackingResponse = {
    orderId: 'order-uuid-999',
    ticketNumber: 142,
    status: 'IN_PROGRESS',
    approvalStatus: 'APPROVED',
    establishmentId: 'est-123',
    establishmentName: 'Café Central Palermo',
    fulfillmentMode: 'SCHEDULED',
    pickupDate: '2026-11-10',
    timeSlotStart: '13:00:00',
    timeSlotEnd: '13:30:00',
    totalAmount: 4850,
    items: [
      {
        dishId: 'dish-1',
        dishName: 'Hamburguesa Clásica EasyEat',
        quantity: 2,
        unitPrice: 2100,
        subtotal: 4200,
      },
      {
        dishId: 'dish-2',
        dishName: 'Agua Mineral sin Gas 500ml',
        quantity: 1,
        unitPrice: 650,
        subtotal: 650,
      },
    ],
    trackingUrl: '/pedidos/order-uuid-999/seguimiento',
    serverTime: new Date().toISOString(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  const renderWithRoute = (orderId = 'order-uuid-999') => {
    return render(
      <MemoryRouter initialEntries={[`/checkout/confirmacion/${orderId}`]}>
        <Routes>
          <Route path="/checkout/confirmacion/:orderId" element={<OrderConfirmationTrackingPage />} />
          <Route path="/pedidos/:orderId/seguimiento" element={<OrderConfirmationTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('AS-1/INV-004: renders green success banner with ticket number and establishment name', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce(mockOrderData);

    renderWithRoute();

    await waitFor(() => {
      expect(screen.getByText(/pago confirmado|reserva confirmada/i)).toBeInTheDocument();
      expect(screen.getAllByText(/142/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText(/café central palermo/i)).toBeInTheDocument();
    });
  });

  it('AS-3/FR-003: displays pickup date, time slot range and order items breakdown', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce(mockOrderData);

    renderWithRoute();

    await waitFor(() => {
      expect(screen.getByText(/2026-11-10/)).toBeInTheDocument();
      expect(screen.getAllByText(/13:00/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText(/13:30/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText(/hamburguesa clásica easyeat/i)).toBeInTheDocument();
      expect(screen.getByText(/agua mineral sin gas 500ml/i)).toBeInTheDocument();
      expect(screen.getAllByText(/\$4850|\$4,850/).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('AS-3: embeds live tracking directly in confirmation page and excludes redundant self-referencing button', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce(mockOrderData);

    renderWithRoute();

    await waitFor(() => {
      expect(screen.queryByRole('link', { name: /seguir pedido|seguimiento en vivo/i })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: /explorar carta/i })).toBeInTheDocument();
      expect(screen.getByText(/horario de retiro programado/i)).toBeInTheDocument();
    });
  });

  it('displays error state when order cannot be retrieved', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockRejectedValueOnce(new Error('Order not found'));

    renderWithRoute('invalid-order');

    await waitFor(() => {
      expect(screen.getAllByText(/no pudimos cargar la información del pedido/i).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders provisional PENDING_ACCREDITATION view without green banner, receipt notice, progressbar or fake ticketNumber and polls softly until approved', async () => {
    vi.useFakeTimers();
    const pendingData: OrderConfirmationTrackingResponse = {
      ...mockOrderData,
      status: 'PENDING_ACCREDITATION',
      approvalStatus: 'PENDING',
      ticketNumber: null,
    };

    vi.mocked(orderTrackingApi.getConfirmationTracking)
      .mockResolvedValueOnce(pendingData)
      .mockResolvedValueOnce(mockOrderData);

    renderWithRoute();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(screen.queryByTestId('order-success-green-banner')).not.toBeInTheDocument();
    expect(screen.queryByText(/comprobante emitido con respaldo por correo/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.queryByText(/#0\b|#null/i)).not.toBeInTheDocument();
    expect(screen.getByText(/pago pendiente de confirmación/i)).toBeInTheDocument();
    expect(within(screen.getByRole('main')).getByRole('link', { name: /mis pedidos/i })).toHaveAttribute(
      'href',
      '/perfil#pedidos'
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });

    expect(orderTrackingApi.getConfirmationTracking).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('order-success-green-banner')).toBeInTheDocument();
    expect(screen.getAllByText(/#142/).length).toBeGreaterThanOrEqual(1);
  });
});
