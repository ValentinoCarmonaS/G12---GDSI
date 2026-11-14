import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { CheckoutPage, generateWeeklyOperatingDays } from '../../pages/CheckoutPage';
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
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.mocked(orderApi.getMyOrders).mockResolvedValue([]);
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
    expect(dayButtons.length).toBe(7);
    expect(dayButtons[0]).toHaveTextContent(/Lun/i);
    expect(dayButtons[6]).toHaveTextContent(/Dom/i);
    const selectableDay = dayButtons.find((btn) => !btn.hasAttribute('disabled')) || dayButtons[0];
    fireEvent.click(selectableDay);

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
    const selectableDay = dayButtons.find((btn) => !btn.hasAttribute('disabled')) || dayButtons[0];
    fireEvent.click(selectableDay);

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

  describe('C-12: Week Structure starting on Monday & Empty Slots State', () => {
    it('verifies that generateWeeklyOperatingDays starts the week on Monday (ISO 8601) and shows only up to Sunday of the same week', () => {
      // Test reference date on Wednesday 2026-11-04
      const wednesday = new Date(2026, 10, 4);
      const days = generateWeeklyOperatingDays(wednesday);

      expect(days).toHaveLength(7);
      // Week starts on Monday 2026-11-02
      expect(days[0].dayName).toBe('Lun');
      expect(days[0].date).toBe('2026-11-02');
      expect(days[0].isOperating).toBe(false); // Past day is disabled

      // Tuesday 2026-11-03
      expect(days[1].dayName).toBe('Mar');
      expect(days[1].date).toBe('2026-11-03');
      expect(days[1].isOperating).toBe(false); // Past day is disabled

      // Wednesday 2026-11-04 (Today)
      expect(days[2].dayName).toBe('Mié');
      expect(days[2].date).toBe('2026-11-04');
      expect(days[2].isOperating).toBe(true);

      // Week ends on Sunday 2026-11-08 of the same week
      expect(days[6].dayName).toBe('Dom');
      expect(days[6].date).toBe('2026-11-08');
      expect(days[6].isOperating).toBe(true);
    });

    it('verifies that Monday is operating and selectable when reference date is Monday', () => {
      // Reference date is Monday 2026-11-02
      const monday = new Date(2026, 10, 2);
      const days = generateWeeklyOperatingDays(monday);

      expect(days).toHaveLength(7);
      expect(days[0].dayName).toBe('Lun');
      expect(days[0].date).toBe('2026-11-02');
      // On Monday itself, Monday is operating and selectable
      expect(days[0].isOperating).toBe(true);
      expect(days[6].dayName).toBe('Dom');
      expect(days[6].date).toBe('2026-11-08');
      expect(days[6].isOperating).toBe(true);
    });

    it('renders empty state message when API returns empty slots list (C-12 / FR-035)', async () => {
      vi.mocked(orderApi.fetchPickupSlots).mockResolvedValueOnce({
        establishmentId: 'est-123',
        date: '2026-11-03',
        timezone: 'America/Argentina/Buenos_Aires',
        isOperatingDay: false,
        leadTimeMinutes: 30,
        slots: [],
      });

      renderWithProviders();

      // Select Scheduled Mode
      fireEvent.click(screen.getByText(/para más tarde/i));

      // Select Date
      await waitFor(() => {
        expect(screen.getByText(/Seleccioná el día de retiro/i)).toBeInTheDocument();
      });

      const dayButtons = screen.getAllByRole('button').filter((btn) => btn.className.includes('min-w-[70px]'));
      const selectableDay = dayButtons.find((btn) => !btn.hasAttribute('disabled')) || dayButtons[0];
      fireEvent.click(selectableDay);

      // Wait for empty state message
      await waitFor(() => {
        expect(screen.getByTestId('empty-slots-message')).toBeInTheDocument();
        expect(screen.getByText(/No hay horarios de retiro disponibles para el día seleccionado/i)).toBeInTheDocument();
      });
    });
  });

  describe('US3: Desbloqueo 409 ACTIVE_RESERVATION_EXISTS, detección proactiva y navegación a catálogo', () => {
    it('handles 409 ACTIVE_RESERVATION_EXISTS with orderId, shows panel and focuses/scrolls it on "Ver mi reserva"', async () => {
      const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
      const scrollIntoViewMock = vi.fn();
      HTMLElement.prototype.scrollIntoView = scrollIntoViewMock;

      try {
        vi.mocked(paymentApi.initiatePayment).mockRejectedValueOnce({
          response: {
            status: 409,
            data: {
              code: 'ACTIVE_RESERVATION_EXISTS',
              orderId: 'order-active-409',
              detail: 'Ya tenés una reserva activa en curso pendiente de pago.',
            },
          },
        });

        vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
          orderId: 'order-active-409',
          orderStatus: 'PENDING_PAYMENT',
          reservation: {
            id: 'res-active-409',
            status: 'ACTIVE',
            expiresAt: new Date(Date.now() + 600000).toISOString(),
            remainingSeconds: 600,
            releaseReason: null,
          },
          payment: {
            id: 'pay-active-409',
            status: 'IN_PROCESS',
            statusDetail: null,
            redirectUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-409',
            sandbox: false,
          },
          fulfillment: {
            mode: 'IMMEDIATE',
            pickupDate: '2026-11-11',
            timeSlotStart: null,
            timeSlotEnd: null,
          },
          totalAmount: 6200,
          currency: 'ARS',
          serverTime: new Date().toISOString(),
        });

        renderWithProviders();

        fireEvent.click(screen.getByText(/lo antes posible/i));
        fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }));

        await waitFor(() => {
          expect(screen.getByTestId('conflict-notification-modal')).toBeInTheDocument();
          expect(paymentApi.getOrderPaymentStatus).toHaveBeenCalledWith('order-active-409');
        });

        const panel = await screen.findByRole('region', { name: /panel de pago en curso/i });
        expect(panel).toBeInTheDocument();

        const viewBtn = screen.getByRole('button', { name: /ver mi reserva/i });
        fireEvent.click(viewBtn);

        await waitFor(() => {
          expect(screen.queryByTestId('conflict-notification-modal')).not.toBeInTheDocument();
        });

        expect(scrollIntoViewMock).toHaveBeenCalled();
        const activeEl = document.activeElement;
        expect(
          activeEl === panel || panel.contains(activeEl) || activeEl?.contains(panel)
        ).toBe(true);
      } finally {
        HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
      }
    });

    it('detects active PENDING_PAYMENT order on mount even with empty cart and hides empty cart message', async () => {
      vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce([
        {
          orderId: 'order-mount-pending',
          ticketNumber: null,
          establishmentId: 'est-123',
          establishmentName: 'Bistro Central',
          status: 'PENDING_PAYMENT',
          fulfillmentMode: 'IMMEDIATE',
          pickupDate: '2026-11-11',
          timeSlotStart: null,
          timeSlotEnd: null,
          totalAmount: 3500,
          expiresAt: new Date(Date.now() + 600000).toISOString(),
          createdAt: '2026-11-11T15:00:00Z',
          redirectUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-mount',
          trackingUrl: '/pedidos/order-mount-pending/seguimiento',
          items: [],
        },
      ]);

      vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
        orderId: 'order-mount-pending',
        orderStatus: 'PENDING_PAYMENT',
        reservation: {
          id: 'res-mount',
          status: 'ACTIVE',
          expiresAt: new Date(Date.now() + 600000).toISOString(),
          remainingSeconds: 600,
          releaseReason: null,
        },
        payment: {
          id: 'pay-mount',
          status: 'IN_PROCESS',
          statusDetail: null,
          redirectUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-mount',
          sandbox: false,
        },
        fulfillment: {
          mode: 'IMMEDIATE',
          pickupDate: '2026-11-11',
          timeSlotStart: null,
          timeSlotEnd: null,
        },
        totalAmount: 3500,
        currency: 'ARS',
        serverTime: new Date().toISOString(),
      });

      renderWithProviders([]);

      await waitFor(() => {
        expect(orderApi.getMyOrders).toHaveBeenCalled();
        expect(paymentApi.getOrderPaymentStatus).toHaveBeenCalledWith('order-mount-pending');
      });

      expect(await screen.findByRole('region', { name: /panel de pago en curso/i })).toBeInTheDocument();
      expect(screen.queryByText(/tu carrito está vacío/i)).not.toBeInTheDocument();
    });

    it('does not read legacy selectedEstablishmentId from localStorage and navigates to /catalogo', async () => {
      window.localStorage.setItem('selectedEstablishmentId', 'est-legacy-999');
      const getItemSpy = vi.spyOn(Storage.prototype, 'getItem');

      render(
        <MemoryRouter initialEntries={['/checkout']}>
          <Routes>
            <Route
              path="/checkout"
              element={
                <CartContext.Provider
                  value={{
                    items: [],
                    addItem: vi.fn(),
                    removeItem: vi.fn(),
                    updateQuantity: vi.fn(),
                    clearCart: clearCartMock,
                    reEvaluateCompliance: vi.fn(),
                    totalAmount: 0,
                    totalCount: 0,
                    hasConflictingItems: false,
                    hasPausedItems: false,
                    toastMessage: null,
                  }}
                >
                  <CheckoutPage />
                </CartContext.Provider>
              }
            />
            <Route path="/catalogo" element={<div data-testid="catalogo-route">Catálogo destino</div>} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(orderApi.getMyOrders).toHaveBeenCalled();
      });

      expect(getItemSpy).not.toHaveBeenCalledWith('selectedEstablishmentId');
      getItemSpy.mockRestore();

      fireEvent.click(screen.getByRole('button', { name: /explorar carta/i }));
      expect(await screen.findByTestId('catalogo-route')).toBeInTheDocument();
    });
  });
});
