import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ProfilePage } from '@/pages/ProfilePage';
import { MyOrdersSection } from '@/components/profile/MyOrdersSection';
import { profileApi } from '@/api/profileApi';
import * as orderApi from '@/api/orderApi';
import { AuthProvider } from '@/context/AuthContext';
import type { ProfileResponse, DietaryRestrictionItem } from '@/types/profile';
import type { DinerOrderSummaryResponse } from '@/types/order';

vi.mock('@/api/orderApi', () => ({
  getMyOrders: vi.fn(),
  cancelOrder: vi.fn(),
  createOrder: vi.fn(),
  getOrderById: vi.fn(),
  fetchPickupSlots: vi.fn(),
}));

describe('ProfilePage & MyOrdersSection Bento (US5, FR-013)', () => {
  const mockProfile: ProfileResponse = {
    id: 'user-uuid-1',
    firstName: 'Valentino',
    lastName: 'Carmona',
    fullName: 'Valentino Carmona',
    phone: '+5491123456789',
    email: 'valentino@example.com',
    pendingEmail: 'nuevo@example.com',
    emailVerificationPending: true,
    avatarUrl: 'https://example.com/broken-avatar.jpg',
    dietaryRestrictions: [{ code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'COMMON_ALLERGEN' }],
  };

  const mockTaxonomy: DietaryRestrictionItem[] = [
    { code: 'CELIAC', name: 'Sin TACC / Celíaco', category: 'COMMON_ALLERGEN' },
    { code: 'VEGAN', name: 'Vegano', category: 'DIETARY_LIFESTYLE' },
    { code: 'PEANUTS', name: 'Maní', category: 'COMMON_ALLERGEN' },
  ];

  const sampleOrders: DinerOrderSummaryResponse[] = [
    {
      orderId: 'order-1',
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
      trackingUrl: '/pedidos/order-1/seguimiento',
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
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(profileApi, 'getProfile').mockResolvedValue(mockProfile);
    vi.spyOn(profileApi, 'getDietaryTaxonomy').mockResolvedValue(mockTaxonomy);
    vi.mocked(orderApi.getMyOrders).mockResolvedValue(sampleOrders);
  });

  const renderProfile = (initialEntries: string[] = ['/perfil']) =>
    render(
      <MemoryRouter initialEntries={initialEntries}>
        <AuthProvider>
          <ProfilePage />
        </AuthProvider>
      </MemoryRouter>
    );

  it('renders segmented Bento tablist with >= 44px touch targets and syncs with /perfil#pedidos', async () => {
    const user = userEvent.setup();
    renderProfile(['/perfil']);

    const tablist = await screen.findByRole('tablist', { name: /secciones de perfil/i });
    expect(tablist).toHaveAttribute('data-bento-tablist', 'true');

    const profileTab = screen.getByRole('tab', { name: /datos y preferencias/i });
    const ordersTab = screen.getByRole('tab', { name: /mis pedidos/i });
    expect(profileTab.className).toMatch(/min-h-\[44px\]/);
    expect(ordersTab.className).toMatch(/min-h-\[44px\]/);

    await user.click(ordersTab);
    expect(ordersTab).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('Bodegón Boedo')).toBeInTheDocument();
  });

  it('renders 2-column Bento grid in Mi perfil with PersonalDataForm, DietaryRestrictionsSelector and avatar fallback', async () => {
    const user = userEvent.setup();
    renderProfile(['/perfil']);

    const grid = await screen.findByTestId('profile-bento-grid');
    expect(grid.className).toMatch(/lg:grid-cols-2/);

    // PendingEmailBanner rendered
    expect(screen.getByText(/nuevo@example\.com/i)).toBeInTheDocument();

    // Avatar image error triggers clean fallback
    const avatarImg = screen.getByRole('img', { name: /avatar/i });
    fireEvent.error(avatarImg);
    expect(screen.queryByRole('img', { name: /avatar/i })).not.toBeInTheDocument();

    // PhotoDeleteConfirmModal opens via AccessibleModal and closes with Escape
    const removeBtn = screen.getByRole('button', { name: /eliminar foto/i });
    await user.click(removeBtn);
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
    await user.keyboard('{Escape}');
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    // DietaryRestrictionsSelector has redundant active/inactive indicator beyond color
    const celiacCheckbox = screen.getByRole('checkbox', { name: /sin tacc/i });
    expect(celiacCheckbox).toBeChecked();
    expect(celiacCheckbox.closest('label')?.className).toMatch(/min-h-\[44px\]/);
  });

  it('renders MyOrdersSection in Bento card grid with StatusBadge, mono tabular total, LoadingSkeleton, EmptyState and FeedbackState retry', async () => {
    // Loading state
    vi.mocked(orderApi.getMyOrders).mockImplementationOnce(() => new Promise(() => {}));
    const { unmount } = render(
      <MemoryRouter>
        <MyOrdersSection />
      </MemoryRouter>
    );
    expect(screen.getByTestId('orders-loading-skeleton')).toBeInTheDocument();
    unmount();

    // Error state with retry
    vi.mocked(orderApi.getMyOrders).mockRejectedValueOnce(new Error('Network error'));
    render(
      <MemoryRouter>
        <MyOrdersSection />
      </MemoryRouter>
    );
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument();
  });

  it('renders MyOrdersSection EmptyState and Bento order cards with mono tabular total', async () => {
    vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce([]);
    const { unmount } = render(
      <MemoryRouter>
        <MyOrdersSection />
      </MemoryRouter>
    );
    expect(await screen.findByText(/todavía no realizaste pedidos/i)).toBeInTheDocument();
    unmount();

    vi.mocked(orderApi.getMyOrders).mockResolvedValueOnce(sampleOrders);
    render(
      <MemoryRouter>
        <MyOrdersSection />
      </MemoryRouter>
    );
    expect(await screen.findByText('Bodegón Boedo')).toBeInTheDocument();
    const grid = screen.getByTestId('my-orders-bento-grid');
    expect(grid).toBeInTheDocument();
    const totalEl = screen.getByTestId('order-total-order-1');
    expect(totalEl.className).toMatch(/font-ee-mono/);
    expect(totalEl.className).toMatch(/tabular-nums/);
  });
});
