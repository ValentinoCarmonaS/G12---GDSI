import type { ComponentProps } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeScopeProvider } from '@/theme/ThemeScopeProvider';
import { AuthContext } from '@/context/AuthContext';
import { CartContext, CartContextType } from '@/context/CartContext';
import { CheckoutPage } from '@/pages/CheckoutPage';
import { PaymentReturnPage } from '@/pages/PaymentReturnPage';
import { FulfillmentModeSelector } from '@/components/checkout/FulfillmentModeSelector';
import { OperatingDatePicker } from '@/components/checkout/OperatingDatePicker';
import { TimeSlotGrid } from '@/components/checkout/TimeSlotGrid';
import { CheckoutSummary } from '@/components/checkout/CheckoutSummary';
import { ConflictNotificationModal } from '@/components/checkout/ConflictNotificationModal';
import { ConcurrencyAlertModal } from '@/components/checkout/ConcurrencyAlertModal';
import { PendingOrderCountdown } from '@/components/checkout/PendingOrderCountdown';
import { PendingAccreditationBanner } from '@/components/checkout/PendingAccreditationBanner';
import { InProcessPaymentPanel } from '@/components/checkout/InProcessPaymentPanel';
import { PaymentAlertBanner } from '@/components/checkout/PaymentAlertBanner';
import * as orderApi from '@/api/orderApi';
import * as paymentApi from '@/api/paymentApi';
import * as checkoutApi from '@/api/checkoutApi';
import * as brandingApi from '@/api/brandingApi';
import type { CartItem } from '@/types/cart';
import type { TimeSlotAvailability } from '@/types/order';

type AuthContextValue = ComponentProps<typeof AuthContext.Provider>['value'];

const mockAuthContext: AuthContextValue = {
  token: 'mock-token',
  user: {
    id: 'user-1',
    email: 'comensal@easyeat.com',
    role: 'COMENSAL',
    tenantId: 'est-123',
    tokenVersion: 1,
    mustChangePassword: false,
  },
  isAuthenticated: true,
  setToken: vi.fn(),
  clearAuth: vi.fn(),
  logout: vi.fn(),
};

const mockCartItems: CartItem[] = [
  {
    dishId: 'dish-1',
    name: 'Milanesa Napolitana',
    price: 4500,
    quantity: 2,
    tenantId: 'est-123',
    isCompliant: true,
    conflictingRestrictions: [],
    pausedByChef: false,
  },
];

const mockCartContext: CartContextType = {
  items: mockCartItems,
  addItem: vi.fn(),
  removeItem: vi.fn(),
  updateQuantity: vi.fn(),
  clearCart: vi.fn(),
  reEvaluateCompliance: vi.fn(),
  totalAmount: 9000,
  totalCount: 2,
  hasConflictingItems: false,
  hasPausedItems: false,
};

const mockSlots: TimeSlotAvailability[] = [
  {
    date: '2026-11-22',
    startTime: '12:00:00',
    endTime: '12:15:00',
    status: 'AVAILABLE',
    maxCapacity: 20,
    bookedCount: 5,
    occupancyPercentage: 25,
    isSelectable: true,
  },
  {
    date: '2026-11-22',
    startTime: '12:15:00',
    endTime: '12:30:00',
    status: 'HIGH_DEMAND',
    maxCapacity: 20,
    bookedCount: 17,
    occupancyPercentage: 85,
    isSelectable: true,
  },
];

describe('Spec 025 US3 — Checkout Bento 2-column & PaymentReturn', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    vi.spyOn(orderApi, 'getMyOrders').mockResolvedValue([]);
    vi.spyOn(brandingApi, 'getDinerBranding').mockResolvedValue({
      establishmentId: 'est-123',
      fantasyName: 'La Parrilla',
      description: 'Parrilla argentina',
      primaryColor: '#1E3A8A',
      primaryContrastColor: '#FFFFFF',
      secondaryColor: '#0F766E',
      secondaryContrastColor: '#FFFFFF',
      accentColor: '#9A4A24',
      accentContrastColor: '#FFFFFF',
      logoUrl: null,
      updatedAt: new Date().toISOString(),
      isDefaultFallback: false,
    });
  });

  describe('CheckoutPage Bento 2-column layout & BrandThemeProvider', () => {
    it('wraps CheckoutPage in BrandThemeProvider, uses 2-column Bento grid, sticky summary with JetBrains Mono, and CTA with data-brand-slot="cta"', async () => {
      const { container } = render(
        <MemoryRouter initialEntries={['/checkout?establishmentId=est-123']}>
          <AuthContext.Provider value={mockAuthContext}>
            <ThemeScopeProvider>
              <CartContext.Provider value={mockCartContext}>
                <CheckoutPage />
              </CartContext.Provider>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByTestId('brand-theme-container')).toBeInTheDocument();
      });

      const grid = screen.getByTestId('checkout-bento-grid');
      expect(grid.className).toContain('lg:grid-cols-[minmax(0,1fr)_380px]');

      const summaryColumn = screen.getByTestId('checkout-summary-column');
      expect(summaryColumn.className).toContain('lg:sticky');

      const totalDisplay = screen.getByTestId('checkout-summary-total');
      expect(totalDisplay.className).toContain('font-ee-mono');
      expect(totalDisplay.className).toContain('tabular-nums');

      const ctaButton = screen.getByRole('button', { name: /confirmar pedido/i });
      expect(ctaButton.getAttribute('data-brand-slot')).toBe('cta');
      expect(ctaButton.className).toContain('min-h-[44px]');

      expect(container.innerHTML).not.toContain('bg-emerald-600');
      expect(container.innerHTML).not.toContain('bg-[#121216]');
    });
  });

  describe('Selectors & Summary touch targets (>=44px) and Bento tokens', () => {
    it('FulfillmentModeSelector uses Bento tokens and accessible radio controls', () => {
      const onSelectMode = vi.fn();
      const { container } = render(
        <FulfillmentModeSelector selectedMode="IMMEDIATE" onSelectMode={onSelectMode} />
      );

      expect(screen.getByRole('radiogroup', { name: /modalidad de retiro/i })).toBeInTheDocument();
      expect(container.innerHTML).not.toContain('border-indigo-600');
      expect(container.innerHTML).not.toContain('bg-indigo-50');
    });

    it('OperatingDatePicker and TimeSlotGrid render >= 44px touch targets with Bento tokens', () => {
      const onSelectDate = vi.fn();
      const onSelectSlot = vi.fn();

      render(
        <div>
          <OperatingDatePicker
            operatingDays={[
              { date: '2026-11-22', dayName: 'Dom', dayNumber: 22, isOperating: true },
              { date: '2026-11-23', dayName: 'Lun', dayNumber: 23, isOperating: false },
            ]}
            selectedDate="2026-11-22"
            onSelectDate={onSelectDate}
          />
          <TimeSlotGrid
            slots={mockSlots}
            selectedSlot={mockSlots[0]}
            onSelectSlot={onSelectSlot}
          />
        </div>
      );

      const dateButton = screen.getByRole('button', { name: /dom 22/i });
      expect(dateButton.className).toContain('min-h-[44px]');
      expect(dateButton.className).not.toContain('border-emerald-600');

      const slotButton = screen.getByRole('button', { name: /12:00 - 12:15/i });
      expect(slotButton.className).toContain('min-h-[44px]');
      expect(slotButton.className).not.toContain('border-emerald-600');
    });

    it('CheckoutSummary renders BentoCard with tabular monospace amounts', () => {
      render(
        <CheckoutSummary
          items={mockCartItems}
          fulfillmentMode="SCHEDULED"
          selectedDate="2026-11-22"
          selectedSlot={mockSlots[1]}
          totalAmount={9000}
        />
      );

      const total = screen.getByTestId('checkout-summary-total');
      expect(total.className).toContain('font-ee-mono');
      expect(total.className).toContain('tabular-nums');
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
  });

  describe('Contingency states & AccessibleModal dialogs', () => {
    it('ConflictNotificationModal closes on Escape via AccessibleModal', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      render(
        <ConflictNotificationModal
          isOpen
          errorCode="SLOT_SOLD_OUT"
          onClose={onClose}
          onRefreshSlots={vi.fn()}
        />
      );

      expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
      await user.keyboard('{Escape}');
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('ConcurrencyAlertModal closes on Escape via AccessibleModal and uses --ee-* tokens (no dark hardcode)', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      const { container } = render(
        <ConcurrencyAlertModal
          isOpen
          type="INSUFFICIENT_STOCK"
          unavailableDishes={[{ dishId: 'd1', dishName: 'Milanesa', limitingIngredient: 'Carne' }]}
          onAction={vi.fn()}
          onClose={onClose}
        />
      );

      expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
      expect(container.innerHTML).not.toContain('bg-[#121216]');
      await user.keyboard('{Escape}');
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('PendingOrderCountdown, PendingAccreditationBanner, InProcessPaymentPanel and PaymentAlertBanner use Bento --ee-* tokens', () => {
      const { container } = render(
        <div>
          <PendingOrderCountdown
            orderId="ord-12345678"
            expiresAt={new Date(Date.now() + 300_000).toISOString()}
            initialSeconds={300}
            onCancel={vi.fn()}
          />
          <PendingAccreditationBanner orderId="ord-12345678" />
          <InProcessPaymentPanel
            redirectUrl="https://sandbox.mercadopago.com.ar/checkout"
            onCancelAttempt={vi.fn()}
          />
          <PaymentAlertBanner type="error" message="Pago rechazado" onClose={vi.fn()} />
        </div>
      );

      const timer = screen.getByTestId('pending-order-timer');
      expect(timer.className).toContain('font-ee-mono');
      expect(timer.className).toContain('tabular-nums');
      expect(container.innerHTML).not.toContain('bg-rose-950/50');
      expect(container.innerHTML).not.toContain('animate-pulse');
    });
  });

  describe('PaymentReturnPage Bento card states', () => {
    it('renders centered Bento card with FeedbackState when order reference is missing (error state)', async () => {
      render(
        <MemoryRouter initialEntries={['/checkout/payment-return']}>
          <AuthContext.Provider value={mockAuthContext}>
            <ThemeScopeProvider>
              <CartContext.Provider value={mockCartContext}>
                <PaymentReturnPage />
              </CartContext.Provider>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByTestId('payment-return-card')).toBeInTheDocument();
      });
      expect(screen.getByTestId('feedback-state')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /volver al checkout/i })).toBeInTheDocument();
    });

    it('renders centered Bento card with StatusBadge and PendingAccreditationBanner in pending state', async () => {
      vi.spyOn(checkoutApi, 'registerPendingPayment').mockResolvedValue({} as any);
      vi.spyOn(paymentApi, 'getOrderPaymentStatus').mockResolvedValue({
        orderId: 'ord-pending-1',
        orderStatus: 'PENDING_ACCREDITATION',
        reservation: null,
        payment: {
          id: 'pay-1',
          status: 'PENDING',
          statusDetail: 'pending_contingency',
          redirectUrl: null,
          sandbox: true,
        },
        fulfillment: {
          mode: 'IMMEDIATE',
          pickupDate: null,
          timeSlotStart: null,
          timeSlotEnd: null,
        },
        totalAmount: 9000,
        currency: 'ARS',
        serverTime: new Date().toISOString(),
      });

      render(
        <MemoryRouter
          initialEntries={[
            '/checkout/payment-return?external_reference=ord-pending-1&collection_status=pending',
          ]}
        >
          <AuthContext.Provider value={mockAuthContext}>
            <ThemeScopeProvider>
              <CartContext.Provider value={mockCartContext}>
                <PaymentReturnPage />
              </CartContext.Provider>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByTestId('payment-return-card')).toBeInTheDocument();
      });
      expect(screen.getByText(/acreditación en curso/i)).toBeInTheDocument();
      expect(screen.getByTestId('status-badge')).toBeInTheDocument();
    });

    it('redirects to /checkout with paymentError when payment is rejected', async () => {
      vi.spyOn(paymentApi, 'getOrderPaymentStatus').mockResolvedValue({
        orderId: 'ord-rej-1',
        orderStatus: 'CANCELLED',
        reservation: null,
        payment: {
          id: 'pay-2',
          status: 'REJECTED',
          statusDetail: 'cc_rejected_insufficient_amount',
          redirectUrl: null,
          sandbox: true,
        },
        fulfillment: {
          mode: 'IMMEDIATE',
          pickupDate: null,
          timeSlotStart: null,
          timeSlotEnd: null,
        },
        totalAmount: 9000,
        currency: 'ARS',
        serverTime: new Date().toISOString(),
      });

      render(
        <MemoryRouter
          initialEntries={[
            '/checkout/payment-return?external_reference=ord-rej-1&collection_status=rejected',
          ]}
        >
          <AuthContext.Provider value={mockAuthContext}>
            <ThemeScopeProvider>
              <CartContext.Provider value={mockCartContext}>
                <Routes>
                  <Route path="/checkout/payment-return" element={<PaymentReturnPage />} />
                  <Route path="/checkout" element={<div data-testid="checkout-redirected">Checkout</div>} />
                </Routes>
              </CartContext.Provider>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByTestId('checkout-redirected')).toBeInTheDocument();
      });
    });

    it('clears cart and redirects to confirmation when payment is approved', async () => {
      vi.spyOn(checkoutApi, 'confirmPayment').mockResolvedValue({} as any);
      vi.spyOn(paymentApi, 'getOrderPaymentStatus').mockResolvedValue({
        orderId: 'ord-ok-1',
        orderStatus: 'PAID',
        reservation: null,
        payment: {
          id: 'pay-3',
          status: 'APPROVED',
          statusDetail: 'accredited',
          redirectUrl: null,
          sandbox: true,
        },
        fulfillment: {
          mode: 'IMMEDIATE',
          pickupDate: null,
          timeSlotStart: null,
          timeSlotEnd: null,
        },
        totalAmount: 9000,
        currency: 'ARS',
        serverTime: new Date().toISOString(),
      });

      render(
        <MemoryRouter
          initialEntries={[
            '/checkout/payment-return?external_reference=ord-ok-1&collection_status=approved',
          ]}
        >
          <AuthContext.Provider value={mockAuthContext}>
            <ThemeScopeProvider>
              <CartContext.Provider value={mockCartContext}>
                <Routes>
                  <Route path="/checkout/payment-return" element={<PaymentReturnPage />} />
                  <Route
                    path="/checkout/confirmacion/:orderId"
                    element={<div data-testid="confirmation-redirected">Confirmed</div>}
                  />
                </Routes>
              </CartContext.Provider>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByTestId('confirmation-redirected')).toBeInTheDocument();
      });
      expect(mockCartContext.clearCart).toHaveBeenCalled();
    });
  });
});
