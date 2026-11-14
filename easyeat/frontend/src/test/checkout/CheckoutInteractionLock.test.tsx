import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CheckoutPage } from '../../pages/CheckoutPage';
import { CartContext } from '../../context/CartContext';
import * as paymentApi from '../../api/paymentApi';
import * as orderApi from '../../api/orderApi';
import { CartItem } from '../../types/cart';

vi.mock('../../api/paymentApi', () => ({
  initiatePayment: vi.fn(),
  cancelPayment: vi.fn(),
  getOrderPaymentStatus: vi.fn(),
}));

vi.mock('../../api/orderApi', () => ({
  createOrder: vi.fn(),
  cancelOrder: vi.fn(),
  getMyOrders: vi.fn().mockResolvedValue([]),
  fetchPickupSlots: vi.fn().mockResolvedValue({
    establishmentId: 'est-123',
    date: '2026-11-09',
    timezone: 'America/Argentina/Buenos_Aires',
    isOperatingDay: true,
    leadTimeMinutes: 20,
    slots: [],
  }),
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

describe('T012 [US1]: Checkout UI Interaction Lock and Duplicate Payment Prevention', () => {
  const clearCartMock = vi.fn();

  const mockItems: CartItem[] = [
    {
      dishId: 'dish-1',
      tenantId: 'est-123',
      name: 'Hamburguesa Doble Cheddar',
      price: 2500,
      quantity: 2,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(orderApi.getMyOrders).mockResolvedValue([]);
  });

  const renderCheckout = () => {
    return render(
      <MemoryRouter initialEntries={['/checkout?establishmentId=est-123']}>
        <CartContext.Provider
          value={{
            items: mockItems,
            totalAmount: 5000,
            addItem: vi.fn(),
            removeItem: vi.fn(),
            updateQuantity: vi.fn(),
            clearCart: clearCartMock,
            totalCount: 2,
            reEvaluateCompliance: vi.fn(),
            hasConflictingItems: false,
            hasPausedItems: false,
          }}
        >
          <CheckoutPage />
        </CartContext.Provider>
      </MemoryRouter>
    );
  };

  it('AS-4/INV-003: disables submit button and shows spinner during order processing', async () => {
    let resolveOrderPromise: (value: any) => void;
    const pendingPromise = new Promise((resolve) => {
      resolveOrderPromise = resolve;
    });

    vi.mocked(paymentApi.initiatePayment).mockReturnValue(pendingPromise as any);

    renderCheckout();

    // Select IMMEDIATE fulfillment mode to make form valid
    const immediateOption = screen.getByRole('radio', { name: /lo antes posible/i });
    fireEvent.click(immediateOption);

    const submitBtn = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(submitBtn).toBeEnabled();

    // Click confirm
    fireEvent.click(submitBtn);

    // Button should immediately become disabled with spinner / processing text
    await waitFor(() => {
      expect(submitBtn).toBeDisabled();
      expect(screen.getByText(/confirmando reserva|procesando|iniciando pago/i)).toBeInTheDocument();
    });

    // Resolve the promise
    resolveOrderPromise!({
      orderId: 'order-123',
      reservationId: 'res-123',
      paymentId: 'pay-123',
      redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123',
      sandbox: true,
      expiresAt: new Date(Date.now() + 900000).toISOString(),
      totalAmount: 5000,
      currency: 'ARS',
    });
  });

  it('CB-003/SC-004: ignores rapid consecutive clicks preventing duplicate order submissions', async () => {
    let resolveOrderPromise: (value: any) => void;
    const pendingPromise = new Promise((resolve) => {
      resolveOrderPromise = resolve;
    });

    vi.mocked(paymentApi.initiatePayment).mockReturnValue(pendingPromise as any);

    renderCheckout();

    // Select IMMEDIATE mode
    const immediateOption = screen.getByRole('radio', { name: /lo antes posible/i });
    fireEvent.click(immediateOption);

    const submitBtn = screen.getByRole('button', { name: /confirmar pedido/i });

    // Compulsive rapid clicks
    fireEvent.click(submitBtn);
    fireEvent.click(submitBtn);
    fireEvent.click(submitBtn);
    fireEvent.click(submitBtn);

    // Ensure initiatePayment was only called once
    expect(paymentApi.initiatePayment).toHaveBeenCalledTimes(1);

    resolveOrderPromise!({
      orderId: 'order-456',
      reservationId: 'res-456',
      paymentId: 'pay-456',
      redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-456',
      sandbox: true,
      expiresAt: new Date(Date.now() + 900000).toISOString(),
      totalAmount: 5000,
      currency: 'ARS',
    });
  });

  it('INV-003: re-enables submit button if order creation fails to allow retry', async () => {
    vi.mocked(paymentApi.initiatePayment).mockRejectedValueOnce({
      response: {
        status: 500,
        data: { detail: 'Error temporal en pasarela' },
      },
    });

    renderCheckout();

    const immediateOption = screen.getByRole('radio', { name: /lo antes posible/i });
    fireEvent.click(immediateOption);

    const submitBtn = screen.getByRole('button', { name: /confirmar pedido/i });
    fireEvent.click(submitBtn);

    // After failure, error message appears and button is re-enabled
    await waitFor(() => {
      expect(screen.getByText(/error temporal en pasarela/i)).toBeInTheDocument();
      expect(submitBtn).toBeEnabled();
    });
  });

  it('US3: locks submit while proactive PENDING_PAYMENT order exists and refreshes getMyOrders on cancel', async () => {
    vi.mocked(orderApi.getMyOrders)
      .mockResolvedValueOnce([
        {
          orderId: 'order-proactive-1',
          ticketNumber: null,
          establishmentId: 'est-123',
          establishmentName: 'Resto Test',
          status: 'PENDING_PAYMENT',
          fulfillmentMode: 'IMMEDIATE',
          pickupDate: '2026-11-11',
          timeSlotStart: null,
          timeSlotEnd: null,
          totalAmount: 5000,
          expiresAt: new Date(Date.now() + 600000).toISOString(),
          createdAt: '2026-11-11T15:00:00Z',
          redirectUrl: null,
          trackingUrl: '/pedidos/order-proactive-1/seguimiento',
          items: [],
        },
      ])
      .mockResolvedValueOnce([]);

    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-proactive-1',
      orderStatus: 'PENDING_PAYMENT',
      reservation: {
        id: 'res-proactive-1',
        status: 'ACTIVE',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        remainingSeconds: 600,
        releaseReason: null,
      },
      payment: {
        id: 'pay-proactive-1',
        status: 'IN_PROCESS',
        statusDetail: null,
        redirectUrl: null,
        sandbox: false,
      },
      fulfillment: {
        mode: 'IMMEDIATE',
        pickupDate: '2026-11-11',
        timeSlotStart: null,
        timeSlotEnd: null,
      },
      totalAmount: 5000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    vi.mocked(paymentApi.cancelPayment).mockResolvedValueOnce({
      orderId: 'order-proactive-1',
      reservationStatus: 'RELEASED',
      paymentStatus: 'CANCELLED',
      message: 'Liberado',
    });

    renderCheckout();

    expect(await screen.findByRole('region', { name: /panel de pago en curso/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('radio', { name: /lo antes posible/i }));
    const submitBtn = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(submitBtn).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /cancelar intento/i }));

    await waitFor(() => {
      expect(paymentApi.cancelPayment).toHaveBeenCalledWith({ orderId: 'order-proactive-1' });
      expect(orderApi.getMyOrders).toHaveBeenCalledTimes(2);
    });

    await waitFor(() => {
      expect(submitBtn).toBeEnabled();
    });
  });
});
