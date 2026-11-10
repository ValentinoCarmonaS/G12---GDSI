import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CheckoutPage } from '../../pages/CheckoutPage';
import { CartContext } from '../../context/CartContext';
import * as orderApi from '../../api/orderApi';
import { CartItem } from '../../types/cart';

vi.mock('../../api/orderApi', () => ({
  createOrder: vi.fn(),
  cancelOrder: vi.fn(),
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

    vi.mocked(orderApi.createOrder).mockReturnValue(pendingPromise as any);

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
      expect(screen.getByText(/confirmando reserva|procesando/i)).toBeInTheDocument();
    });

    // Resolve the promise
    resolveOrderPromise!({
      orderId: 'order-123',
      establishmentId: 'est-123',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-09',
      timeSlotStart: '14:00:00',
      timeSlotEnd: '14:30:00',
      status: 'IN_PROGRESS',
      totalAmount: 5000,
      items: [],
      serverTime: new Date().toISOString(),
      expiresAt: null,
    });
  });

  it('CB-003/SC-004: ignores rapid consecutive clicks preventing duplicate order submissions', async () => {
    let resolveOrderPromise: (value: any) => void;
    const pendingPromise = new Promise((resolve) => {
      resolveOrderPromise = resolve;
    });

    vi.mocked(orderApi.createOrder).mockReturnValue(pendingPromise as any);

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

    // Ensure createOrder was only called once
    expect(orderApi.createOrder).toHaveBeenCalledTimes(1);

    resolveOrderPromise!({
      orderId: 'order-456',
      establishmentId: 'est-123',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-09',
      timeSlotStart: '14:00:00',
      timeSlotEnd: '14:30:00',
      status: 'IN_PROGRESS',
      totalAmount: 5000,
      items: [],
      serverTime: new Date().toISOString(),
      expiresAt: null,
    });
  });

  it('INV-003: re-enables submit button if order creation fails to allow retry', async () => {
    vi.mocked(orderApi.createOrder).mockRejectedValueOnce({
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
});
