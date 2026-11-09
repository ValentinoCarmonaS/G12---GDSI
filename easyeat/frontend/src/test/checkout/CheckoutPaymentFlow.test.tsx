import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CheckoutPage } from '../../pages/CheckoutPage';
import { CartContext } from '../../context/CartContext';
import * as paymentApi from '../../api/paymentApi';
import { CartItem } from '../../types/cart';

vi.mock('../../api/paymentApi', () => ({
  initiatePayment: vi.fn(),
  cancelPayment: vi.fn(),
  getOrderPaymentStatus: vi.fn(),
}));

vi.mock('../../api/orderApi', () => ({
  createOrder: vi.fn(),
  cancelOrder: vi.fn(),
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

    const payButton = screen.getByRole('button', { name: /pagar/i });
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

    const payButton = screen.getByRole('button', { name: /pagar/i });
    expect(payButton).not.toBeDisabled();
    fireEvent.click(payButton);

    await waitFor(() => {
      expect(screen.getByText(/fallo al conectar con mercado pago/i)).toBeInTheDocument();
    });

    expect(clearCartMock).not.toHaveBeenCalled();
    expect(screen.getByText('Hamburguesa Gourmet')).toBeInTheDocument();
  });
});
