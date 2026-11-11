import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
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
    date: '2026-11-10',
    timezone: 'America/Argentina/Buenos_Aires',
    isOperatingDay: true,
    leadTimeMinutes: 30,
    slots: [
      {
        startTime: '13:00:00',
        endTime: '13:15:00',
        date: '2026-11-10',
        maxCapacity: 10,
        bookedCount: 2,
        occupancyPercentage: 20,
        status: 'AVAILABLE',
        isSelectable: true,
      },
    ],
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

describe('T021: CheckoutPaymentFlow frontend integration tests (AS-1, AS-6, FR-001, FR-002)', () => {
  const clearCartMock = vi.fn();

  const mockItems: CartItem[] = [
    {
      dishId: 'dish-1',
      tenantId: 'est-123',
      name: 'Hamburguesa Gourmet',
      price: 3000,
      quantity: 1,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(orderApi.getMyOrders).mockResolvedValue([]);
  });

  const renderComponent = (items = mockItems) => {
    return render(
      <MemoryRouter>
        <CartContext.Provider
          value={{
            items,
            addItem: vi.fn(),
            updateQuantity: vi.fn(),
            removeItem: vi.fn(),
            clearCart: clearCartMock,
            reEvaluateCompliance: vi.fn(),
            totalAmount: items.reduce((acc, i) => acc + i.price * i.quantity, 0),
            totalCount: items.reduce((acc, i) => acc + i.quantity, 0),
            hasConflictingItems: false,
            hasPausedItems: false,
            toastMessage: null,
          }}
        >
          <CheckoutPage />
        </CartContext.Provider>
      </MemoryRouter>
    );
  };

  it('renders payment button and initiates redirect to Mercado Pago Sandbox on click (AS-1)', async () => {
    const originalLocation = window.location;
    // @ts-ignore
    delete window.location;
    // @ts-ignore
    window.location = { href: '', assign: vi.fn() };

    vi.mocked(paymentApi.initiatePayment).mockResolvedValueOnce({
      orderId: 'order-1',
      reservationId: 'res-1',
      paymentId: 'pay-1',
      gatewayPreferenceId: 'pref-123',
      redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123',
      sandbox: true,
      expiresAt: new Date(Date.now() + 900000).toISOString(),
      totalAmount: 3000,
      currency: 'ARS',
    });

    renderComponent();

    // Select fulfillment mode IMMEDIATE so the form is valid
    await waitFor(() => {
      expect(screen.getByText('Hamburguesa Gourmet')).toBeInTheDocument();
    });

    const immediateOption = screen.getByLabelText(/lo antes posible/i);
    fireEvent.click(immediateOption);

    const payButton = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(payButton).toBeInTheDocument();
    expect(payButton).not.toBeDisabled();

    fireEvent.click(payButton);

    await waitFor(() => {
      expect(paymentApi.initiatePayment).toHaveBeenCalled();
    });

    // @ts-ignore
    window.location = originalLocation;
  });

  it('preserves cart and displays alert banner when payment initiation fails with 502 (AS-6)', async () => {
    vi.mocked(paymentApi.initiatePayment).mockRejectedValueOnce({
      response: {
        status: 502,
        data: { message: 'Fallo al conectar con Mercado Pago. No se realizó ningún cargo.' },
      },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Hamburguesa Gourmet')).toBeInTheDocument();
    });

    const immediateOption = screen.getByLabelText(/lo antes posible/i);
    fireEvent.click(immediateOption);

    const payButton = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(payButton).not.toBeDisabled();
    fireEvent.click(payButton);

    await waitFor(() => {
      expect(screen.getByText(/fallo al conectar con mercado pago/i)).toBeInTheDocument();
    });

    expect(clearCartMock).not.toHaveBeenCalled();
    expect(screen.getByText('Hamburguesa Gourmet')).toBeInTheDocument();
  });

  it('recovers from 409 ACTIVE_RESERVATION_EXISTS by showing InProcessPaymentPanel and re-enables checkout after cancelling', async () => {
    vi.mocked(paymentApi.initiatePayment).mockRejectedValueOnce({
      response: {
        status: 409,
        data: {
          code: 'ACTIVE_RESERVATION_EXISTS',
          orderId: 'order-lock-99',
          detail: 'Ya existe una reserva pendiente de pago.',
        },
      },
    });

    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-lock-99',
      orderStatus: 'PENDING_PAYMENT',
      reservation: {
        id: 'res-lock-99',
        status: 'ACTIVE',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        remainingSeconds: 600,
        releaseReason: null,
      },
      payment: {
        id: 'pay-lock-99',
        status: 'IN_PROCESS',
        statusDetail: null,
        redirectUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-99',
        sandbox: false,
      },
      fulfillment: {
        mode: 'IMMEDIATE',
        pickupDate: '2026-11-11',
        timeSlotStart: null,
        timeSlotEnd: null,
      },
      totalAmount: 3000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    vi.mocked(paymentApi.cancelPayment).mockResolvedValueOnce({
      orderId: 'order-lock-99',
      reservationStatus: 'RELEASED',
      paymentStatus: 'CANCELLED',
      message: 'Intento cancelado',
    });

    renderComponent();

    fireEvent.click(screen.getByLabelText(/lo antes posible/i));
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }));

    await waitFor(() => {
      expect(paymentApi.getOrderPaymentStatus).toHaveBeenCalledWith('order-lock-99');
    });

    expect(await screen.findByRole('region', { name: /panel de pago en curso/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /cancelar intento/i }));

    await waitFor(() => {
      expect(paymentApi.cancelPayment).toHaveBeenCalledWith({ orderId: 'order-lock-99' });
      expect(orderApi.getMyOrders).toHaveBeenCalledTimes(2);
    });

    await waitFor(() => {
      expect(screen.queryByRole('region', { name: /panel de pago en curso/i })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: /confirmar pedido/i })).not.toBeDisabled();
    });
  });

  it('does not navigate to /checkout/confirmacion/:orderId when initiatePayment returns redirectUrl = null', async () => {
    vi.mocked(paymentApi.initiatePayment).mockResolvedValueOnce({
      orderId: 'order-no-url-1',
      reservationId: 'res-no-url-1',
      paymentId: 'pay-no-url-1',
      gatewayPreferenceId: 'pref-no-url',
      redirectUrl: null,
      sandbox: false,
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      totalAmount: 3000,
      currency: 'ARS',
    });

    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-no-url-1',
      orderStatus: 'PENDING_PAYMENT',
      reservation: {
        id: 'res-no-url-1',
        status: 'ACTIVE',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        remainingSeconds: 600,
        releaseReason: null,
      },
      payment: {
        id: 'pay-no-url-1',
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
      totalAmount: 3000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    render(
      <MemoryRouter initialEntries={['/checkout']}>
        <Routes>
          <Route
            path="/checkout"
            element={
              <CartContext.Provider
                value={{
                  items: mockItems,
                  addItem: vi.fn(),
                  updateQuantity: vi.fn(),
                  removeItem: vi.fn(),
                  clearCart: clearCartMock,
                  reEvaluateCompliance: vi.fn(),
                  totalAmount: 3000,
                  totalCount: 2,
                  hasConflictingItems: false,
                  hasPausedItems: false,
                  toastMessage: null,
                }}
              >
                <CheckoutPage />
              </CartContext.Provider>
            }
          />
          <Route
            path="/checkout/confirmacion/:orderId"
            element={<div data-testid="false-confirmation-route">Confirmación falsa</div>}
          />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.click(screen.getByLabelText(/lo antes posible/i));
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }));

    await waitFor(() => {
      expect(paymentApi.initiatePayment).toHaveBeenCalled();
    });

    expect(screen.queryByTestId('false-confirmation-route')).not.toBeInTheDocument();
    expect(clearCartMock).not.toHaveBeenCalled();
    expect(screen.getByText(/no pudimos obtener el enlace de pago/i)).toBeInTheDocument();
    expect(screen.getByText(/no existe preferencia de pago activa/i)).toBeInTheDocument();
  });
});
