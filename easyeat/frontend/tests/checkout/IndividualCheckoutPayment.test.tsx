import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CheckoutPage } from '../../src/pages/CheckoutPage';
import { CartContext } from '../../src/context/CartContext';
import * as checkoutApi from '../../src/api/checkoutApi';
import { CartItem } from '../../src/types/cart';

vi.mock('../../src/api/checkoutApi', () => ({
  confirmPayment: vi.fn(),
  registerPendingPayment: vi.fn(),
  confirmZeroAmountOrder: vi.fn(),
}));

vi.mock('../../src/api/paymentApi', () => ({
  initiatePayment: vi.fn(),
  cancelPayment: vi.fn(),
  getOrderPaymentStatus: vi.fn(),
}));

vi.mock('../../src/api/orderApi', () => ({
  createOrder: vi.fn().mockResolvedValue({
    orderId: 'order-zero-123',
    status: 'PENDING_PAYMENT',
  }),
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

vi.mock('../../src/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 'user-123', email: 'diner@test.com', role: 'COMENSAL' },
    logout: vi.fn(),
  }),
}));

vi.mock('../../src/api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn().mockResolvedValue({ avatarUrl: null }),
  },
}));

vi.mock('../../src/api/adminNotificationApi', () => ({
  adminNotificationApi: {
    getUnreadCount: vi.fn().mockResolvedValue(0),
    getNotifications: vi.fn().mockResolvedValue([]),
  },
}));

describe('T015: IndividualCheckoutPayment frontend tests (AS-4, AS-5, FR-005, FR-006)', () => {
  const clearCartMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    window.sessionStorage.setItem('easyeat_active_tenant', JSON.stringify({ id: 'est-123' }));
  });

  const renderComponent = (items: CartItem[]) => {
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

  it('renders zero-amount confirmation button when cart total is $0 and processes direct confirmation (AS-4, FR-005)', async () => {
    const zeroItems: CartItem[] = [
      {
        dishId: 'dish-free-1',
        tenantId: 'est-123',
        name: 'Plato 100% Bonificado',
        price: 0,
        quantity: 1,
      },
    ];

    vi.mocked(checkoutApi.confirmZeroAmountOrder).mockResolvedValueOnce({
      orderId: 'order-zero-123',
      orderStatus: 'IN_PROGRESS',
      totalAmount: 0,
      gatewayBypassed: true,
      stockDebited: true,
      kdsTicket: {
        ticketId: 'ticket-123',
        ticketNumber: 42,
        status: 'QUEUED',
        queuedAt: '2026-11-10T13:00:00Z',
      },
      message: '¡Orden 100% bonificada confirmada! Tu pedido ya fue enviado a la cocina.',
    });

    renderComponent(zeroItems);

    // Select immediate fulfillment mode so form is valid
    const immediateOption = screen.getByText(/inmediato|lo antes posible/i);
    fireEvent.click(immediateOption);

    const zeroBtn = screen.getByRole('button', { name: /confirmar pedido bonificado/i });
    expect(zeroBtn).toBeInTheDocument();
    expect(zeroBtn).not.toBeDisabled();

    fireEvent.click(zeroBtn);

    await waitFor(() => {
      expect(checkoutApi.confirmZeroAmountOrder).toHaveBeenCalledWith({ orderId: 'order-zero-123' });
      expect(clearCartMock).toHaveBeenCalled();
    });
  });
});
