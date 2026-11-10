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
    date: '2026-11-03',
    timezone: 'America/Argentina/Buenos_Aires',
    isOperatingDay: true,
    leadTimeMinutes: 30,
    slots: [
      {
        startTime: '13:00:00',
        endTime: '13:15:00',
        date: '2026-11-03',
        maxCapacity: 10,
        bookedCount: 2,
        occupancyPercentage: 20,
        status: 'AVAILABLE',
        isSelectable: true,
      },
      {
        startTime: '13:15:00',
        endTime: '13:30:00',
        date: '2026-11-03',
        maxCapacity: 10,
        bookedCount: 9,
        occupancyPercentage: 90,
        status: 'HIGH_DEMAND',
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

describe('T044: CheckoutPage End-to-End Integration Tests', () => {
  const clearCartMock = vi.fn();

  const mockItems: CartItem[] = [
    {
      dishId: 'dish-1',
      tenantId: 'est-123',
      name: 'Hamburguesa Completa',
      price: 2500,
      quantity: 2,
    },
    {
      dishId: 'dish-2',
      tenantId: 'est-123',
      name: 'Papas Rústicas',
      price: 1200,
      quantity: 1,
    },
  ];

  const renderWithProviders = (items: CartItem[] = mockItems) => {
    return render(
      <MemoryRouter initialEntries={['/checkout?establishmentId=est-123']}>
        <CartContext.Provider
          value={{
            items,
            addItem: vi.fn(),
            removeItem: vi.fn(),
            updateQuantity: vi.fn(),
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

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty cart state when no items in cart', () => {
    renderWithProviders([]);

    expect(screen.getByText(/tu carrito está vacío/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /explorar carta/i })).toBeInTheDocument();
  });

  it('loads cart items from CartContext and displays summary total', () => {
    renderWithProviders();

    expect(screen.getByText(/modalidad de entrega/i)).toBeInTheDocument();
    expect(screen.getByText('Hamburguesa Completa')).toBeInTheDocument();
    expect(screen.getByText('Papas Rústicas')).toBeInTheDocument();
    expect(screen.getByText(/resumen del pedido/i)).toBeInTheDocument();
    expect(screen.getByText('$6200.00')).toBeInTheDocument();
  });

  it('enables confirm button when IMMEDIATE mode is selected', async () => {
    renderWithProviders();

    const immediateOption = screen.getByText(/lo antes posible/i);
    fireEvent.click(immediateOption);

    const confirmButton = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(confirmButton).not.toBeDisabled();
  });

  it('selects date and 15-minute slot from grid, submits order and initiates online payment redirect', async () => {
    const originalLocation = window.location;
    // @ts-ignore
    delete window.location;
    // @ts-ignore
    window.location = { href: '', assign: vi.fn() };

    const mockPaymentResponse: any = {
      orderId: 'order-999',
      reservationId: 'res-999',
      paymentId: 'pay-999',
      gatewayPreferenceId: 'pref-999',
      redirectUrl: 'https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-999',
      sandbox: true,
      expiresAt: new Date(Date.now() + 900000).toISOString(),
      totalAmount: 6200,
      currency: 'ARS',
    };

    vi.mocked(paymentApi.initiatePayment).mockResolvedValueOnce(mockPaymentResponse);

    renderWithProviders();

    // 1. Select Scheduled Mode ("Para más tarde")
    const scheduledOption = screen.getByText(/para más tarde/i);
    fireEvent.click(scheduledOption);

    // 2. Select Date from operating days
    await waitFor(() => {
      expect(screen.getByText(/Seleccioná el día de retiro/i)).toBeInTheDocument();
    });

    const dayButtons = screen.getAllByRole('button').filter((btn) => btn.className.includes('min-w-[70px]'));
    expect(dayButtons.length).toBeGreaterThan(0);
    fireEvent.click(dayButtons[0]);

    // 3. Select 15-minute time slot from grid
    await waitFor(() => {
      const slotButton = screen.getByText(/13:00\s*-\s*13:15/i);
      expect(slotButton).toBeInTheDocument();
      fireEvent.click(slotButton);
    });

    // 4. Confirm button should now be enabled
    const confirmButton = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(confirmButton).not.toBeDisabled();

    // 5. Submit order
    fireEvent.click(confirmButton);

    // 6. Smoothly invokes initiatePayment with selected slot and payment method
    await waitFor(() => {
      expect(paymentApi.initiatePayment).toHaveBeenCalledWith(
        expect.objectContaining({
          establishmentId: 'est-123',
          fulfillmentMode: 'SCHEDULED',
          paymentMethod: 'MERCADO_PAGO',
          timeSlotStart: '13:00:00',
          timeSlotEnd: '13:15:00',
        })
      );
    });

    // @ts-ignore
    window.location = originalLocation;
  });

  it('receives 409 conflict modal (SLOT_SOLD_OUT) and recovers without losing cart state', async () => {
    const error409 = {
      response: {
        status: 409,
        data: {
          code: 'SLOT_SOLD_OUT',
          detail: 'La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.',
        },
      },
    };

    vi.mocked(paymentApi.initiatePayment).mockRejectedValueOnce(error409);

    renderWithProviders();

    // Select Scheduled Mode
    const scheduledOption = screen.getByText(/para más tarde/i);
    fireEvent.click(scheduledOption);

    // Select Date
    await waitFor(() => {
      expect(screen.getByText(/Seleccioná el día de retiro/i)).toBeInTheDocument();
    });

    const dayButtons = screen.getAllByRole('button').filter((btn) => btn.className.includes('min-w-[70px]'));
    fireEvent.click(dayButtons[0]);

    // Select Slot
    await waitFor(() => {
      const slotButton = screen.getByText(/13:00\s*-\s*13:15/i);
      fireEvent.click(slotButton);
    });

    // Confirm
    const confirmButton = screen.getByRole('button', { name: /confirmar pedido/i });
    fireEvent.click(confirmButton);

    // Modal appears
    await waitFor(() => {
      expect(screen.getByTestId('conflict-notification-modal')).toBeInTheDocument();
      expect(screen.getByText(/franja horaria agotada/i)).toBeInTheDocument();
      expect(screen.getByText(/La franja horaria seleccionada acaba de agotarse/i)).toBeInTheDocument();
    });

    // Cart items are preserved
    expect(clearCartMock).not.toHaveBeenCalled();
    expect(screen.getByText('Hamburguesa Completa')).toBeInTheDocument();
    expect(screen.getByText('Papas Rústicas')).toBeInTheDocument();

    // Recover by clicking choose another slot
    const chooseAnotherButton = screen.getByRole('button', { name: /elegir otra franja/i });
    fireEvent.click(chooseAnotherButton);

    // Modal closes, cart state is still intact
    await waitFor(() => {
      expect(screen.queryByTestId('conflict-notification-modal')).not.toBeInTheDocument();
      expect(screen.getByText('Hamburguesa Completa')).toBeInTheDocument();
    });
  });
});
