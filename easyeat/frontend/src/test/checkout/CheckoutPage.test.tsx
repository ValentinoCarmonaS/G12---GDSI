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
    ],
  }),
}));

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 'user-123', email: 'diner@test.com', role: 'COMENSAL' },
    logout: vi.fn(),
  }),
}));

describe('CheckoutPage Component (T041 / US4)', () => {
  const mockItems: CartItem[] = [
    {
      dishId: 'dish-1',
      tenantId: 'est-123',
      name: 'Hamburguesa Completa',
      price: 2500,
      quantity: 1,
    },
  ];

  const renderWithProviders = (items: CartItem[] = mockItems) => {
    return render(
      <MemoryRouter>
        <CartContext.Provider
          value={{
            items,
            addItem: vi.fn(),
            removeItem: vi.fn(),
            updateQuantity: vi.fn(),
            clearCart: vi.fn(),
            reEvaluateCompliance: vi.fn(),
            totalAmount: items.reduce((acc, i) => acc + i.price * i.quantity, 0),
            totalCount: items.length,
            hasConflictingItems: false,
            toastMessage: null,
          }}
        >
          <CheckoutPage />
        </CartContext.Provider>
      </MemoryRouter>
    );
  };

  it('renders empty cart state when no items in cart', () => {
    renderWithProviders([]);

    expect(screen.getByText(/tu carrito está vacío/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /explorar carta/i })).toBeInTheDocument();
  });

  it('renders fulfillment mode selector and checkout summary when cart has items', () => {
    renderWithProviders();

    expect(screen.getByText(/modalidad de entrega/i)).toBeInTheDocument();
    expect(screen.getByText(/lo antes posible/i)).toBeInTheDocument();
    expect(screen.getByText(/para más tarde/i)).toBeInTheDocument();
    expect(screen.getByText(/resumen del pedido/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /confirmar pedido/i })).toBeDisabled();
  });

  it('enables confirm button when IMMEDIATE mode is selected', async () => {
    renderWithProviders();

    const immediateOption = screen.getByText(/lo antes posible/i);
    fireEvent.click(immediateOption);

    const confirmButton = screen.getByRole('button', { name: /confirmar pedido/i });
    expect(confirmButton).not.toBeDisabled();
  });

  it('successfully creates order and transitions to pending order countdown screen', async () => {
    const mockOrderResponse: any = {
      orderId: 'order-999',
      status: 'PENDING_PAYMENT',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-03',
      timeSlotStart: '12:00:00',
      timeSlotEnd: '12:30:00',
      totalAmount: 2500,
      expiresAt: new Date(Date.now() + 900000).toISOString(),
      serverTime: new Date().toISOString(),
      items: [],
    };

    vi.mocked(orderApi.createOrder).mockResolvedValueOnce(mockOrderResponse);

    renderWithProviders();

    const immediateOption = screen.getByText(/lo antes posible/i);
    fireEvent.click(immediateOption);

    const confirmButton = screen.getByRole('button', { name: /confirmar pedido/i });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(screen.getByText(/reserva en espera de pago/i)).toBeInTheDocument();
      expect(screen.getByText(/#order-99/i)).toBeInTheDocument();
    });
  });

  it('displays conflict modal when order creation encounters 409 SLOT_SOLD_OUT', async () => {
    const error409 = {
      response: {
        status: 409,
        data: {
          code: 'SLOT_SOLD_OUT',
          detail: 'La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.',
        },
      },
    };

    vi.mocked(orderApi.createOrder).mockRejectedValueOnce(error409);

    renderWithProviders();

    const immediateOption = screen.getByText(/lo antes posible/i);
    fireEvent.click(immediateOption);

    const confirmButton = screen.getByRole('button', { name: /confirmar pedido/i });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(screen.getByTestId('conflict-notification-modal')).toBeInTheDocument();
      expect(screen.getByText(/franja horaria agotada/i)).toBeInTheDocument();
    });
  });
});
