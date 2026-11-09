import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { PaymentReturnPage } from '../../pages/PaymentReturnPage';
import { CartContext } from '../../context/CartContext';
import * as paymentApi from '../../api/paymentApi';

vi.mock('../../api/paymentApi', () => ({
  getOrderPaymentStatus: vi.fn(),
  initiatePayment: vi.fn(),
  cancelPayment: vi.fn(),
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
  });

  const renderComponent = (searchParams = '?collection_status=approved&external_reference=order-123') => {
    return render(
      <MemoryRouter initialEntries={[`/checkout/payment-return${searchParams}`]}>
        <CartContext.Provider
          value={{
            items: [
              { dishId: 'dish-1', tenantId: 'est-1', name: 'Pizza', price: 5000, quantity: 1 },
            ],
            tenantId: 'est-1',
            addItem: vi.fn(),
            updateQuantity: vi.fn(),
            removeItem: vi.fn(),
            clearCart: clearCartMock,
            totalItems: 1,
            totalPrice: 5000,
          }}
        >
          <PaymentReturnPage />
        </CartContext.Provider>
      </MemoryRouter>
    );
  };

  it('verifies approved payment against backend, clears cart automatically, and displays success message (AS-2, Q4)', async () => {
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'PAID',
      reservationStatus: 'CONFIRMED',
      paymentStatus: 'APPROVED',
      statusDetail: 'accredited',
      transactionAmount: 5000,
      pickupDate: '2026-11-10',
      timeSlotStart: '13:00:00',
      timeSlotEnd: '13:15:00',
      expiresAt: null,
      expired: false,
    });

    renderComponent('?collection_status=approved&external_reference=order-123');

    await waitFor(() => {
      expect(screen.getByText(/¡pago confirmado!/i)).toBeInTheDocument();
    });

    // Validar vaciado automático del carrito (AS-2, Q4)
    expect(clearCartMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/tu pedido fue procesado exitosamente/i)).toBeInTheDocument();
  });

  it('redirects to checkout with alert banner when payment is rejected (AS-3, Q2)', async () => {
    vi.mocked(paymentApi.getOrderPaymentStatus).mockResolvedValueOnce({
      orderId: 'order-123',
      orderStatus: 'CANCELLED',
      reservationStatus: 'RELEASED',
      paymentStatus: 'REJECTED',
      statusDetail: 'cc_rejected_insufficient_amount',
      transactionAmount: 5000,
      pickupDate: '2026-11-10',
      timeSlotStart: '13:00:00',
      timeSlotEnd: '13:15:00',
      expiresAt: null,
      expired: false,
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
      reservationStatus: 'ACTIVE',
      paymentStatus: 'IN_PROCESS',
      statusDetail: null,
      transactionAmount: 5000,
      pickupDate: '2026-11-10',
      timeSlotStart: '13:00:00',
      timeSlotEnd: '13:15:00',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      expired: false,
    });

    renderComponent('?collection_status=null&external_reference=order-123&status=null');

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(
        '/checkout',
        expect.objectContaining({
          state: expect.objectContaining({
            infoMessage: expect.stringMatching(/pago no completado|pago en curso/i),
          }),
        })
      );
    });

    expect(clearCartMock).not.toHaveBeenCalled();
  });
});
