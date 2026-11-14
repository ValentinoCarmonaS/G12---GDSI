import { render, screen, waitFor, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { PaymentReturnPage } from '../../pages/PaymentReturnPage';
import { CartContext } from '../../context/CartContext';
import * as paymentApi from '../../api/paymentApi';
import * as checkoutApi from '../../api/checkoutApi';

vi.mock('../../api/paymentApi', () => ({
  getOrderPaymentStatus: vi.fn(),
  initiatePayment: vi.fn(),
  cancelPayment: vi.fn(),
}));

vi.mock('../../api/checkoutApi', () => ({
  confirmPayment: vi.fn(),
  registerPendingPayment: vi.fn(),
  confirmZeroAmountOrder: vi.fn(),
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

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('T023: PaymentReturnPage Component Tests (AS-2, AS-3, AS-4, Q2, Q4)', () => {
  const clearCartMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(checkoutApi.confirmPayment).mockResolvedValue({
      orderId: 'order-123',
      orderStatus: 'IN_PROGRESS',
      paymentStatus: 'APPROVED',
      stockDebited: true,
      kdsTicket: {
        ticketId: 'ticket-1',
        ticketNumber: 142,
        status: 'QUEUED',
        queuedAt: new Date().toISOString(),
      },
      idempotentReplay: false,
    });
    vi.mocked(checkoutApi.registerPendingPayment).mockResolvedValue({
      orderId: 'order-123',
      orderStatus: 'PENDING_ACCREDITATION',
      paymentStatus: 'PENDING',
      stockDebited: false,
      kdsQueued: false,
      reservationFrozen: true,
      message: 'Pendiente de acreditación',
    });
  });

  const renderComponent = (searchParams = '?collection_status=approved&external_reference=order-123') => {
    return render(
      <MemoryRouter initialEntries={[`/checkout/payment-return${searchParams}`]}>
        <CartContext.Provider
          value={{
            items: [
              { dishId: 'dish-1', tenantId: 'est-1', name: 'Pizza', price: 5000, quantity: 1 },
            ],
            addItem: vi.fn(),
            updateQuantity: vi.fn(),
            removeItem: vi.fn(),
            clearCart: clearCartMock,
            reEvaluateCompliance: vi.fn(),
            totalAmount: 5000,
            totalCount: 1,
            hasConflictingItems: false,
            hasPausedItems: false,
            toastMessage: null,
          }}
        >
          <PaymentReturnPage />
        </CartContext.Provider>
      </MemoryRouter>
    );
  };

  it.each([
    { label: 'payment APPROVED + order PAID', orderStatus: 'PAID' as const, paymentStatus: 'APPROVED' as const },
    { label: 'order CONFIRMED', orderStatus: 'CONFIRMED' as const, paymentStatus: 'IN_PROCESS' as const },
    { label: 'order IN_PROGRESS', orderStatus: 'IN_PROGRESS' as const, paymentStatus: 'APPROVED' as const },
    { label: 'order READY_FOR_PICKUP', orderStatus: 'READY_FOR_PICKUP' as const, paymentStatus: 'APPROVED' as const },
    { label: 'order COMPLETED', orderStatus: 'COMPLETED' as const, paymentStatus: 'APPROVED' as const },
  ])(
    'clears cart and redirects to /checkout/confirmacion/:orderId when $label',
    async ({ orderStatus, paymentStatus }) => {
      vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
        orderId: 'order-123',
        orderStatus,
        reservation: {
          id: 'res-1',
          status: 'CONFIRMED',
          expiresAt: '',
          remainingSeconds: 0,
          releaseReason: null,
        },
        payment: {
          id: 'pay-1',
          status: paymentStatus,
          statusDetail: 'accredited',
          redirectUrl: null,
          sandbox: true,
        },
        fulfillment: {
          mode: 'SCHEDULED',
          pickupDate: '2026-11-10',
          timeSlotStart: '13:00:00',
          timeSlotEnd: '13:15:00',
        },
        totalAmount: 5000,
        currency: 'ARS',
        serverTime: new Date().toISOString(),
      });

      renderComponent('?collection_status=approved&external_reference=order-123&payment_id=mp-pay-99');

      await waitFor(() => {
        expect(checkoutApi.confirmPayment).toHaveBeenCalledWith({
          orderId: 'order-123',
          gatewayPaymentId: 'mp-pay-99',
          status: 'approved',
        });
        expect(clearCartMock).toHaveBeenCalledTimes(1);
        expect(mockNavigate).toHaveBeenCalledWith('/checkout/confirmacion/order-123', { replace: true });
      });
    }
  );

  it('redirects to /checkout preserving cart on late 409 InvalidOrderStateException (Q3) without clearing cart', async () => {
    vi.mocked(checkoutApi.confirmPayment).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          code: 'INVALID_ORDER_STATE',
          detail: 'La orden expiró o fue cancelada.',
        },
      },
    });
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'EXPIRED',
      reservation: {
        id: 'res-1',
        status: 'EXPIRED',
        expiresAt: '',
        remainingSeconds: 0,
        releaseReason: 'EXPIRED',
      },
      payment: {
        id: 'pay-1',
        status: 'EXPIRED',
        statusDetail: null,
        redirectUrl: null,
        sandbox: true,
      },
      fulfillment: {
        mode: 'SCHEDULED',
        pickupDate: '2026-11-10',
        timeSlotStart: '13:00:00',
        timeSlotEnd: '13:15:00',
      },
      totalAmount: 5000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    renderComponent('?collection_status=approved&external_reference=order-123&payment_id=mp-late-409');

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(
        '/checkout',
        expect.objectContaining({
          state: expect.objectContaining({
            paymentError: expect.any(String),
          }),
        })
      );
    });

    expect(clearCartMock).not.toHaveBeenCalled();
  });


  it('renders PENDING_ACCREDITATION actions linking to /pedidos/:orderId/seguimiento and /perfil#pedidos without clearing cart', async () => {
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'PENDING_ACCREDITATION',
      reservation: {
        id: 'res-1',
        status: 'ACTIVE',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        remainingSeconds: 600,
        releaseReason: null,
      },
      payment: {
        id: 'pay-1',
        status: 'PENDING',
        statusDetail: 'pending_contingency',
        redirectUrl: null,
        sandbox: true,
      },
      fulfillment: {
        mode: 'SCHEDULED',
        pickupDate: '2026-11-10',
        timeSlotStart: '13:00:00',
        timeSlotEnd: '13:15:00',
      },
      totalAmount: 5000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    renderComponent('?collection_status=in_process&external_reference=order-123&payment_id=mp-pending-1');

    await waitFor(() => {
      expect(screen.getByText(/acreditación en curso/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('link', { name: /seguir pedido/i })).toHaveAttribute(
      'href',
      '/pedidos/order-123/seguimiento'
    );
    expect(within(screen.getByRole('main')).getByRole('link', { name: /mis pedidos/i })).toHaveAttribute(
      'href',
      '/perfil#pedidos'
    );
    expect(clearCartMock).not.toHaveBeenCalled();
  });


  it('redirects to checkout with alert banner when payment is rejected (AS-3, Q2)', async () => {
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'CANCELLED',
      reservation: {
        id: 'res-1',
        status: 'RELEASED',
        expiresAt: '',
        remainingSeconds: 0,
        releaseReason: 'REJECTED',
      },
      payment: {
        id: 'pay-1',
        status: 'REJECTED',
        statusDetail: 'cc_rejected_insufficient_amount',
        redirectUrl: null,
        sandbox: true,
      },
      fulfillment: {
        mode: 'SCHEDULED',
        pickupDate: '2026-11-10',
        timeSlotStart: '13:00:00',
        timeSlotEnd: '13:15:00',
      },
      totalAmount: 5000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    renderComponent('?collection_status=rejected&external_reference=order-123');

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(
        '/checkout',
        expect.objectContaining({
          state: expect.objectContaining({
            paymentError: expect.stringMatching(/fondos insuficientes|rechazado/i),
          }),
        })
      );
    });

    // El carrito no se vacía ante rechazos
    expect(clearCartMock).not.toHaveBeenCalled();
  });


  it('redirects to checkout when diner abandons voluntarily with back_urls (AS-4, Q2)', async () => {
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'PENDING_PAYMENT',
      reservation: {
        id: 'res-1',
        status: 'ACTIVE',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        remainingSeconds: 600,
        releaseReason: null,
      },
      payment: {
        id: 'pay-1',
        status: 'IN_PROCESS',
        statusDetail: null,
        redirectUrl: 'https://sandbox.mercadopago.com',
        sandbox: true,
      },
      fulfillment: {
        mode: 'SCHEDULED',
        pickupDate: '2026-11-10',
        timeSlotStart: '13:00:00',
        timeSlotEnd: '13:15:00',
      },
      totalAmount: 5000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    renderComponent('?collection_status=null&external_reference=order-123&status=null');

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/checkout', {
        state: {
          infoMessage: 'No se completó el pago. Podés retomarlo o reintentar cuando quieras.',
        },
      });
    });

    expect(clearCartMock).not.toHaveBeenCalled();
  });

  it('redirects to /checkout preserving cart when orderStatus is EXPIRED even if collection_status=approved', async () => {
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'EXPIRED',
      reservation: {
        id: 'res-1',
        status: 'EXPIRED',
        expiresAt: '',
        remainingSeconds: 0,
        releaseReason: 'EXPIRED',
      },
      payment: {
        id: 'pay-1',
        status: 'EXPIRED',
        statusDetail: null,
        redirectUrl: null,
        sandbox: true,
      },
      fulfillment: {
        mode: 'SCHEDULED',
        pickupDate: '2026-11-10',
        timeSlotStart: '13:00:00',
        timeSlotEnd: '13:15:00',
      },
      totalAmount: 5000,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    });

    renderComponent('?collection_status=approved&external_reference=order-123');

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(
        '/checkout',
        expect.objectContaining({
          state: expect.objectContaining({
            paymentError: expect.any(String),
          }),
        })
      );
    });
    expect(clearCartMock).not.toHaveBeenCalled();
  });
});
