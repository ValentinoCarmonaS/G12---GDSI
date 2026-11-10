import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
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
    user: { id: 'user-persist-1', email: 'diner.persistent@test.com', role: 'COMENSAL' },
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

describe('T014 [US1]: Order Confirmation and Tracking Coexistence upon Reload or Re-entry', () => {
  const sampleOrder: OrderConfirmationTrackingResponse = {
    orderId: 'order-persist-777',
    ticketNumber: 305,
    status: 'IN_PROGRESS',
    approvalStatus: 'APPROVED',
    establishmentId: 'est-pellegrini',
    establishmentName: 'Parrilla San Telmo',
    fulfillmentMode: 'IMMEDIATE',
    pickupDate: '2026-11-09',
    timeSlotStart: '12:30:00',
    timeSlotEnd: '13:00:00',
    totalAmount: 9200,
    items: [
      {
        dishId: 'dish-ojo-bife',
        dishName: 'Ojo de Bife con Guarnición',
        quantity: 1,
        unitPrice: 9200,
        subtotal: 9200,
      },
    ],
    trackingUrl: '/pedidos/order-persist-777/seguimiento',
    serverTime: new Date().toISOString(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('CB-012/Q2: restores confirmation and tracking context upon direct URL re-entry from email link', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce(sampleOrder);

    render(
      <MemoryRouter initialEntries={['/pedidos/order-persist-777/seguimiento']}>
        <Routes>
          <Route path="/pedidos/:orderId/seguimiento" element={<OrderConfirmationTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Verifies data fetched with correct orderId parameter
    await waitFor(() => {
      expect(orderTrackingApi.getConfirmationTracking).toHaveBeenCalledWith('order-persist-777');
      expect(screen.getAllByText(/305/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText(/parrilla san telmo/i)).toBeInTheDocument();
      expect(screen.getByText(/ojo de bife con guarnición/i)).toBeInTheDocument();
      expect(screen.getAllByText(/\$9200|\$9,200/).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('CB-012: preserves confirmation details across browser reload (F5) on /checkout/confirmacion/:orderId', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce(sampleOrder);

    render(
      <MemoryRouter initialEntries={['/checkout/confirmacion/order-persist-777']}>
        <Routes>
          <Route path="/checkout/confirmacion/:orderId" element={<OrderConfirmationTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(orderTrackingApi.getConfirmationTracking).toHaveBeenCalledWith('order-persist-777');
      expect(screen.getByText(/pago confirmado|reserva confirmada/i)).toBeInTheDocument();
      expect(screen.getAllByText(/305/).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('Q2/A2: coexistence: maintains active tracking access without redirecting to empty cart', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce({
      ...sampleOrder,
      status: 'READY_FOR_PICKUP',
    });

    render(
      <MemoryRouter initialEntries={['/checkout/confirmacion/order-persist-777']}>
        <Routes>
          <Route path="/checkout/confirmacion/:orderId" element={<OrderConfirmationTrackingPage />} />
          <Route path="/checkout" element={<div>Checkout Page (Cart)</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText(/305/).length).toBeGreaterThanOrEqual(1);
      // Does not redirect away to checkout cart
      expect(screen.queryByText(/checkout page \(cart\)/i)).not.toBeInTheDocument();
    });
  });
});
