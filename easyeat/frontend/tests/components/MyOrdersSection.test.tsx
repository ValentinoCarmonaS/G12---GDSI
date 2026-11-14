import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { MyOrdersSection } from '@/components/profile/MyOrdersSection';
import * as orderApi from '@/api/orderApi';
import * as paymentApi from '@/api/paymentApi';
import type { DinerOrderSummaryResponse } from '@/types/order';

vi.mock('@/api/orderApi', () => ({
  getMyOrders: vi.fn(),
  cancelOrder: vi.fn(),
  createOrder: vi.fn(),
  getOrderById: vi.fn(),
  fetchPickupSlots: vi.fn(),
}));

vi.mock('@/api/paymentApi', () => ({
  cancelPayment: vi.fn(),
  initiatePayment: vi.fn(),
  getOrderPaymentStatus: vi.fn(),
}));

describe('MyOrdersSection (T028, US4, FR-008, FR-009, Q5)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderSection = (props?: { onResumePayment?: (url: string) => void }) =>
    render(
      <MemoryRouter>
        <MyOrdersSection onResumePayment={props?.onResumePayment} />
      </MemoryRouter>
    );

  const sampleOrders: DinerOrderSummaryResponse[] = [
    {
      orderId: 'order-in-progress-1',
      ticketNumber: 104,
      establishmentId: 'est-1',
      establishmentName: 'Bodegón Boedo',
      status: 'IN_PROGRESS',
      fulfillmentMode: 'SCHEDULED',
      pickupDate: '2026-11-11',
      timeSlotStart: '20:00:00',
      timeSlotEnd: '20:15:00',
      totalAmount: 18500,
      expiresAt: '2026-11-11T23:00:00Z',
      createdAt: '2026-11-11T22:10:00Z',
      redirectUrl: null,
      trackingUrl: '/pedidos/order-in-progress-1/seguimiento',
      items: [
        {
          dishId: 'dish-1',
          dishName: 'Milanesa Napolitana',
          quantity: 2,
          unitPrice: 9250,
          subtotal: 18500,
        },
      ],
    },
    {
      orderId: 'order-pending-with-url-2',
      ticketNumber: null,
      establishmentId: 'est-2',
      establishmentName: 'Parrilla Sur',
      status: 'PENDING_PAYMENT',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-11',
      timeSlotStart: null,
      timeSlotEnd: null,
      totalAmount: 9400,
      expiresAt: '2026-11-11T22:25:00Z',
      createdAt: '2026-11-11T22:00:00Z',
      redirectUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-abc',
      trackingUrl: '/pedidos/order-pending-with-url-2/seguimiento',
      items: [
        {
          dishId: 'dish-2',
          dishName: 'Empanadas de Carne x6',
          quantity: 1,
          unitPrice: 9400,
          subtotal: 9400,
        },
      ],
    },
    {
      orderId: 'order-expired-3',
      ticketNumber: null,
      establishmentId: 'est-2',
      establishmentName: 'Café Palermo',
      status: 'EXPIRED',
      fulfillmentMode: 'IMMEDIATE',
      pickupDate: '2026-11-10',
      timeSlotStart: null,
      timeSlotEnd: null,
      totalAmount: 5200,
      expiresAt: '2026-11-10T18:15:00Z',
      createdAt: '2026-11-10T18:00:00Z',
      redirectUrl: null,
      trackingUrl: '/pedidos/order-expired-3/seguimiento',
      items: [],
    },
  ];

  it('renders ordered cards, translated Spanish badges, real ticketNumber without fake #null/#0 and tracking link', async () => {
    vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce(sampleOrders);

    renderSection();

    expect(await screen.findByText('Bodegón Boedo')).toBeInTheDocument();
    expect(screen.getByText('Parrilla Sur')).toBeInTheDocument();
    expect(screen.getByText('Café Palermo')).toBeInTheDocument();

    const cards = screen.getAllByTestId(/^order-card-/);
    expect(cards.map((c) => c.getAttribute('data-testid'))).toEqual([
      'order-card-order-in-progress-1',
      'order-card-order-pending-with-url-2',
      'order-card-order-expired-3',
    ]);

    expect(screen.getByText(/En preparación/i)).toBeInTheDocument();
    expect(screen.getByText(/Pendiente de pago/i)).toBeInTheDocument();
    expect(screen.getByText(/Expirado/i)).toBeInTheDocument();

    expect(screen.getByText(/#104/)).toBeInTheDocument();
    expect(screen.queryByText(/#0\b/)).not.toBeInTheDocument();
    expect(screen.queryByText(/#null/i)).not.toBeInTheDocument();

    const trackingLink = screen.getByRole('link', { name: /ver seguimiento/i });
    expect(trackingLink).toHaveAttribute('href', '/pedidos/order-in-progress-1/seguimiento');
  });

  it('renders translated badges for PENDING_ACCREDITATION, READY_FOR_PICKUP, COMPLETED and CANCELLED', async () => {
    vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce([
      { ...sampleOrders[0], orderId: 'o-acc', status: 'PENDING_ACCREDITATION', ticketNumber: null },
      { ...sampleOrders[0], orderId: 'o-rdy', status: 'READY_FOR_PICKUP', ticketNumber: 201 },
      { ...sampleOrders[0], orderId: 'o-cmp', status: 'COMPLETED', ticketNumber: 202 },
      { ...sampleOrders[0], orderId: 'o-cnc', status: 'CANCELLED', ticketNumber: null, redirectUrl: null },
    ]);

    renderSection();

    expect(await screen.findByText(/En acreditación/i)).toBeInTheDocument();
    expect(screen.getByText(/Listo para retirar/i)).toBeInTheDocument();
    expect(screen.getByText(/Completado/i)).toBeInTheDocument();
    expect(screen.getByText(/Cancelado/i)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /ver seguimiento/i })).toHaveLength(3);
  });

  it('renders empty state with navigation link to /catalogo when diner has no orders', async () => {
    vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce([]);

    renderSection();

    expect(await screen.findByText(/todavía no realizaste pedidos|no tenés pedidos/i)).toBeInTheDocument();
    const catalogLink = screen.getByRole('link', { name: /catálogo|explorar platos/i });
    expect(catalogLink).toHaveAttribute('href', '/catalogo');
  });

  it('renders error alert and retries loading when getMyOrders fails', async () => {
    const user = userEvent.setup();
    vi.mocked(orderApi.getMyOrders)
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce(sampleOrders);

    renderSection();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    const retryBtn = screen.getByRole('button', { name: /reintentar/i });
    await user.click(retryBtn);

    expect(await screen.findByText('Bodegón Boedo')).toBeInTheDocument();
    expect(orderApi.getMyOrders).toHaveBeenCalledTimes(2);
  });

  it('triggers "Retomar pago" and "Cancelar intento" reloading orders on PENDING_PAYMENT with redirectUrl', async () => {
    const user = userEvent.setup();
    const onResumePayment = vi.fn();
    vi.mocked(orderApi.getMyOrders)
      .mockResolvedValueOnce([sampleOrders[1]])
      .mockResolvedValueOnce([{ ...sampleOrders[1], status: 'CANCELLED', redirectUrl: null }]);
    vi.mocked(paymentApi.cancelPayment).mockResolvedValueOnce({
      orderId: 'order-pending-with-url-2',
      reservationStatus: 'RELEASED',
      paymentStatus: 'CANCELLED',
      message: 'Intento cancelado',
    });

    renderSection({ onResumePayment });

    const resumeBtn = await screen.findByRole('button', { name: /retomar pago/i });
    expect(resumeBtn).toBeEnabled();
    await user.click(resumeBtn);
    expect(onResumePayment).toHaveBeenCalledWith(
      'https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-abc'
    );

    const cancelBtn = screen.getByRole('button', { name: /cancelar intento/i });
    await user.click(cancelBtn);

    await waitFor(() => {
      expect(paymentApi.cancelPayment).toHaveBeenCalledWith({
        orderId: 'order-pending-with-url-2',
      });
    });
    await waitFor(() => {
      expect(orderApi.getMyOrders).toHaveBeenCalledTimes(2);
    });
  });

  it('hides or disables "Retomar pago" with explanation while preserving "Cancelar intento" when redirectUrl is null (Q5)', async () => {
    vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce([
      {
        ...sampleOrders[1],
        orderId: 'order-pending-no-url',
        redirectUrl: null,
      },
    ]);

    renderSection();

    expect(await screen.findByText('Parrilla Sur')).toBeInTheDocument();

    const resumeBtn = screen.queryByRole('button', { name: /retomar pago/i }) as HTMLButtonElement | null;
    expect(resumeBtn === null || resumeBtn.disabled).toBe(true);
    expect(screen.getByText(/no hay.*preferencia.*pago activa|no.*enlace.*pago activo/i)).toBeInTheDocument();

    const cancelBtn = screen.getByRole('button', { name: /cancelar intento/i });
    expect(cancelBtn).toBeEnabled();
  });
});
