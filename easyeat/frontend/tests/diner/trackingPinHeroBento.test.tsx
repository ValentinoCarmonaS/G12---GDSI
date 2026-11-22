import { render, screen, waitFor, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { OrderConfirmationTrackingPage } from '../../src/pages/OrderConfirmationTrackingPage';
import { OrderSuccessGreenBanner } from '../../src/components/tracking/OrderSuccessGreenBanner';
import { LiveTrackingWidget } from '../../src/components/tracking/LiveTrackingWidget';
import { ScheduledSlotCountdown } from '../../src/components/tracking/ScheduledSlotCountdown';
import { ReadyForPickupCallout } from '../../src/components/tracking/ReadyForPickupCallout';
import { DeliveredOrderCallout } from '../../src/components/tracking/DeliveredOrderCallout';
import { orderTrackingApi } from '../../src/api/orderTrackingApi';
import * as pollingHook from '../../src/hooks/useOrderLiveTrackingPolling';
import type {
  OrderConfirmationTrackingResponse,
  OrderLiveTrackingResponse,
} from '../../src/types/orderTracking';

vi.mock('../../src/api/orderTrackingApi', () => ({
  orderTrackingApi: {
    getConfirmationTracking: vi.fn(),
    getLiveTracking: vi.fn(),
  },
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

vi.mock('../../src/api/brandingApi', () => ({
  brandingApi: {
    getPublicBranding: vi.fn().mockResolvedValue({
      establishmentId: 'est-123',
      primaryColor: '#1D4ED8',
      primaryContrastColor: '#FFFFFF',
      secondaryColor: '#0F172A',
      secondaryContrastColor: '#FFFFFF',
      accentColor: '#9A4A24',
      accentContrastColor: '#FFFFFF',
      logoUrl: null,
      isDefaultFallback: false,
    }),
  },
}));

const mockOrder: OrderConfirmationTrackingResponse = {
  orderId: 'order-uuid-025',
  ticketNumber: 208,
  status: 'IN_PROGRESS',
  approvalStatus: 'APPROVED',
  establishmentId: 'est-123',
  establishmentName: 'Bistró Bento Palermo',
  fulfillmentMode: 'IMMEDIATE',
  pickupDate: '2026-11-22',
  timeSlotStart: '13:00:00',
  timeSlotEnd: '13:30:00',
  totalAmount: 6450,
  items: [
    {
      dishId: 'dish-1',
      dishName: 'Bowl de Salmón',
      quantity: 1,
      unitPrice: 6450,
      subtotal: 6450,
    },
  ],
  trackingUrl: '/pedidos/order-uuid-025/seguimiento',
  serverTime: '2026-11-22T15:00:00Z',
};

const baseTracking: OrderLiveTrackingResponse = {
  orderId: 'order-uuid-025',
  ticketNumber: 208,
  status: 'IN_PROGRESS',
  fulfillmentMode: 'IMMEDIATE',
  isCountdownActive: true,
  estimatedMinutesRemaining: 14,
  progressPercentage: 45,
  isReadyForPickup: false,
  message: 'Preparando tu pedido',
  serverTime: '2026-11-22T15:05:00Z',
  positionAhead: null,
  remainingMinutes: 14,
  progressPercent: 45,
};

describe('Spec 025 US4 — Confirmación, seguimiento en vivo y Tarjeta Héroe de PIN Bento', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('envuelve OrderConfirmationTrackingPage en BrandThemeProvider con OrderSuccessGreenBanner en data-brand-slot="confirmation" y ticket monoespaciado', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockResolvedValueOnce(mockOrder);
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: baseTracking,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: false,
    });

    render(
      <MemoryRouter initialEntries={['/checkout/confirmacion/order-uuid-025']}>
        <Routes>
          <Route path="/checkout/confirmacion/:orderId" element={<OrderConfirmationTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    const brandContainer = await screen.findByTestId('brand-theme-container');
    expect(brandContainer).toHaveAttribute('data-brand-layer', 'establishment');

    const banner = await screen.findByTestId('order-success-green-banner');
    expect(banner).toHaveAttribute('data-brand-slot', 'confirmation');
    expect(banner.innerHTML).not.toMatch(/animate-pulse|blur-2xl/);

    const ticketEl = within(banner).getByText('#208');
    expect(ticketEl).toHaveClass('font-ee-mono', 'tabular-nums');
  });

  it('muestra estado de error accesible con FeedbackState y botón Reintentar en OrderConfirmationTrackingPage', async () => {
    vi.mocked(orderTrackingApi.getConfirmationTracking).mockRejectedValueOnce(new Error('Network error'));

    render(
      <MemoryRouter initialEntries={['/checkout/confirmacion/order-uuid-025']}>
        <Routes>
          <Route path="/checkout/confirmacion/:orderId" element={<OrderConfirmationTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument();
    });
  });

  it('renderiza OrderSuccessGreenBanner con slot confirmation, sin animate-pulse ni blur decorativo', () => {
    render(
      <OrderSuccessGreenBanner
        ticketNumber={99}
        pickupDate="2026-11-22"
        timeSlotStart="12:00:00"
        timeSlotEnd="12:30:00"
        establishmentName="La Esquina"
        fulfillmentMode="SCHEDULED"
      />
    );

    const banner = screen.getByTestId('order-success-green-banner');
    expect(banner).toHaveAttribute('data-brand-slot', 'confirmation');
    expect(banner.innerHTML).not.toMatch(/animate-pulse|blur-2xl/);
    expect(within(banner).getByText('#99')).toHaveClass('font-ee-mono', 'tabular-nums');
  });

  it('muestra indicador progresivo de etapas, bloque de demora con aria-live="polite" y preserva PIN ante desconexión en LiveTrackingWidget', () => {
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...baseTracking,
        kdsQueueSaturation: {
          activeOrdersAhead: 2,
          kitchenStatus: 'DELAYED',
          globalDelayAppliedMinutes: 10,
        },
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
      isDisconnected: true,
    });

    const { rerender } = render(
      <LiveTrackingWidget
        orderId="order-uuid-025"
        ticketNumber={208}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByTestId('tracking-stage-stepper')).toBeInTheDocument();
    expect(screen.getByTestId('connection-status-badge')).toBeInTheDocument();

    const delayNotice = screen.getByTestId('tracking-delay-notice');
    expect(delayNotice).toHaveAttribute('aria-live', 'polite');

    // Desconexión temporal con pedido listo preserva el PIN visible
    vi.spyOn(pollingHook, 'useOrderLiveTrackingPolling').mockReturnValue({
      tracking: {
        ...baseTracking,
        status: 'READY_FOR_PICKUP',
        isReadyForPickup: true,
        securityCode: '7391',
        message: '¡Tu pedido está listo para retirar!',
      },
      isLoading: false,
      error: new Error('Offline'),
      refetch: vi.fn(),
      isDisconnected: true,
    });

    rerender(
      <LiveTrackingWidget
        orderId="order-uuid-025"
        ticketNumber={208}
        fulfillmentMode="IMMEDIATE"
      />
    );

    expect(screen.getByTestId('connection-status-badge')).toBeInTheDocument();
    expect(screen.getByTestId('security-pin-display')).toHaveTextContent('7 3 9 1');
  });

  it('renderiza ScheduledSlotCountdown en font-ee-mono tabular-nums sin animate-pulse', () => {
    const { container } = render(
      <ScheduledSlotCountdown
        isCountdownActive={true}
        timeSlotStart="14:00:00"
        timeSlotEnd="14:30:00"
        estimatedMinutesRemaining={12}
      />
    );

    const countdown = screen.getByTestId('active-proximity-countdown');
    expect(countdown).toBeInTheDocument();
    expect(container.innerHTML).not.toMatch(/animate-pulse/);
    expect(within(countdown).getByText('12 min')).toHaveClass('font-ee-mono', 'tabular-nums');
  });

  it('renderiza ReadyForPickupCallout como Tarjeta Héroe Bento con SecurityPinDisplay en gran formato (text-4xl+)', () => {
    render(
      <ReadyForPickupCallout
        ticketNumber={208}
        securityCode="4821"
        items={[{ dishName: 'Bowl de Salmón', quantity: 1 }]}
      />
    );

    const callout = screen.getByTestId('ready-for-pickup-callout');
    expect(callout).toHaveAttribute('data-bento-hero', 'pickup-pin');

    const pin = within(callout).getByTestId('security-pin-display');
    expect(pin).toHaveTextContent('4 8 2 1');
    expect(pin.className).toMatch(/text-(4xl|5xl)/);
    expect(within(callout).getByText(/Dictale este código de 4 dígitos/i)).toBeInTheDocument();
  });

  it('renderiza DeliveredOrderCallout en bloque Bento con accesos a catálogo y Mis pedidos y sin PIN', () => {
    render(
      <MemoryRouter>
        <DeliveredOrderCallout ticketNumber={208} />
      </MemoryRouter>
    );

    const callout = screen.getByTestId('delivered-order-callout');
    expect(callout).toBeInTheDocument();
    expect(within(callout).getByText('#208')).toHaveClass('font-ee-mono', 'tabular-nums');
    expect(within(callout).getByRole('link', { name: /explorar catálogo|volver al catálogo/i })).toHaveAttribute(
      'href',
      '/catalogo'
    );
    expect(within(callout).getByRole('link', { name: /mis pedidos/i })).toHaveAttribute(
      'href',
      '/perfil#pedidos'
    );
    expect(within(callout).queryByTestId('security-pin-display')).not.toBeInTheDocument();
  });
});
