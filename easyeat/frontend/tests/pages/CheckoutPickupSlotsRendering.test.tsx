import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CheckoutPage, formatDateToIsoString } from '@/pages/CheckoutPage';
import { ClientCacheProvider, useClientCache } from '@/context/ClientCacheContext';
import { CartContext } from '@/context/CartContext';
import * as orderApi from '@/api/orderApi';
import * as brandingApi from '@/api/brandingApi';
import { CartItem } from '@/types/cart';

vi.mock('@/api/paymentApi', () => ({
  initiatePayment: vi.fn(),
  cancelPayment: vi.fn(),
  getOrderPaymentStatus: vi.fn(),
}));

vi.mock('@/api/orderApi', () => ({
  createOrder: vi.fn(),
  cancelOrder: vi.fn(),
  getMyOrders: vi.fn().mockResolvedValue([]),
  fetchPickupSlots: vi.fn(),
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 'user-comensal-1', email: 'diner@test.com', role: 'COMENSAL' },
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/profileApi', () => ({
  profileApi: {
    getProfile: vi.fn().mockResolvedValue({ avatarUrl: null }),
  },
}));

vi.mock('@/api/adminNotificationApi', () => ({
  adminNotificationApi: {
    getUnreadCount: vi.fn().mockResolvedValue(0),
    getNotifications: vi.fn().mockResolvedValue([]),
  },
}));

const TENANT_ID = 'est-checkout-123';

const cachedBranding: brandingApi.RestaurantBranding = {
  establishmentId: TENANT_ID,
  fantasyName: 'Bistró Central',
  description: 'Cocina de autor',
  logoUrl: `/api/v1/establishments/${TENANT_ID}/logo`,
  primaryColor: '#FAD4C0',
  secondaryColor: '#80A1C1',
  accentColor: '#9A4A24',
  primaryContrastColor: '#111827',
  secondaryContrastColor: '#111827',
  accentContrastColor: '#FFFFFF',
  updatedAt: '2026-11-24T12:00:00Z',
  isDefaultFallback: false,
};

const SeedBrandingCache: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const cache = useClientCache();
  if (!cache.get(`branding:diner:${TENANT_ID}`)) {
    cache.set(`branding:diner:${TENANT_ID}`, cachedBranding);
  }
  return <>{children}</>;
};

describe('CheckoutPickupSlotsRendering (T037, FR-003, FR-007, FR-010)', () => {
  const mockItems: CartItem[] = [
    {
      dishId: 'dish-1',
      tenantId: TENANT_ID,
      name: 'Milanesa Napolitana',
      price: 9800,
      quantity: 1,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    window.sessionStorage.clear();
    window.localStorage.clear();
    vi.mocked(orderApi.getMyOrders).mockResolvedValue([]);
    vi.spyOn(brandingApi, 'getDinerBranding').mockResolvedValue(cachedBranding);

    const todayStr = formatDateToIsoString(new Date());
    vi.mocked(orderApi.fetchPickupSlots).mockResolvedValue({
      establishmentId: TENANT_ID,
      date: todayStr,
      serverTime: `${todayStr}T11:00:00-03:00`,
      timezone: 'America/Argentina/Buenos_Aires',
      isEstablishmentOpen: true,
      isOperatingDay: true,
      leadTimeMinutes: 30,
      slots: [
        {
          startTime: '13:00:00',
          endTime: '13:15:00',
          date: todayStr,
          maxCapacity: 10,
          bookedCount: 2,
          occupancyPercentage: 20,
          status: 'AVAILABLE',
          isSelectable: true,
        },
        {
          startTime: '13:15:00',
          endTime: '13:30:00',
          date: todayStr,
          maxCapacity: 10,
          bookedCount: 8,
          occupancyPercentage: 80,
          status: 'HIGH_DEMAND',
          isSelectable: true,
        },
      ],
    });
  });

  it('renders AVAILABLE and HIGH_DEMAND pickup slots and reuses branding from ClientSWRStore without blocking', async () => {
    render(
      <ClientCacheProvider>
        <SeedBrandingCache>
          <MemoryRouter initialEntries={[`/checkout?establishmentId=${TENANT_ID}`]}>
            <CartContext.Provider
              value={{
                items: mockItems,
                addItem: vi.fn(),
                removeItem: vi.fn(),
                updateQuantity: vi.fn(),
                clearCart: vi.fn(),
                reEvaluateCompliance: vi.fn(),
                totalAmount: 9800,
                totalCount: 1,
                hasConflictingItems: false,
                hasPausedItems: false,
                toastMessage: null,
              }}
            >
              <CheckoutPage />
            </CartContext.Provider>
          </MemoryRouter>
        </SeedBrandingCache>
      </ClientCacheProvider>
    );

    const scheduledBtn = await screen.findByText(/para más tarde/i);
    fireEvent.click(scheduledBtn);

    await waitFor(() => {
      expect(screen.getByText(/Seleccioná el día de retiro/i)).toBeInTheDocument();
    });

    const dayButtons = screen.getAllByRole('button').filter((btn) => btn.className.includes('min-w-[70px]'));
    const selectableDay = dayButtons.find((btn) => !btn.hasAttribute('disabled')) || dayButtons[0];
    fireEvent.click(selectableDay);

    await waitFor(() => {
      expect(orderApi.fetchPickupSlots).toHaveBeenCalledWith(TENANT_ID, expect.any(String));
    });

    expect(await screen.findByText(/13:00\s*-\s*13:15/i)).toBeInTheDocument();
    expect(await screen.findByText(/13:15\s*-\s*13:30/i)).toBeInTheDocument();

    const brandContainer = screen.getByTestId('brand-theme-container');
    expect(brandContainer.style.getPropertyValue('--brand-primary')).toBe('#FAD4C0');
    expect(brandContainer.style.getPropertyValue('--brand-accent')).toBe('#9A4A24');
  });
});
