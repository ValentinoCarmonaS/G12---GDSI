import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { authApi } from '@/api/authApi';
import { staffApi } from '@/api/staffApi';
import { inventoryApi } from '@/api/inventoryApi';
import { dishReviewApi } from '@/api/dishReviewApi';
import { adminNotificationApi } from '@/api/adminNotificationApi';
import * as AuthModule from '@/hooks/useAuth';
import type { DishReviewInboxPage } from '@/types/dishReview';

vi.mock('@/api/staffApi');
vi.mock('@/api/inventoryApi');
vi.mock('@/hooks/useAuth');
vi.mock('@/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn().mockResolvedValue({
      items: [],
      page: 0,
      size: 1,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      pendingCount: 0,
    }),
    getDetail: vi.fn(),
  },
}));
vi.mock('@/api/adminNotificationApi', () => ({
  adminNotificationApi: {
    listNotifications: vi.fn().mockResolvedValue({
      content: [],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
      isLast: true,
      unreadCount: 0,
    }),
    markAsRead: vi.fn(),
  },
}));

function inboxPage(overrides: Partial<DishReviewInboxPage> = {}): DishReviewInboxPage {
  return {
    items: [],
    page: 0,
    size: 1,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    pendingCount: 0,
    ...overrides,
  };
}

describe('AdminWorkspacePage (T035, ADM-1.1.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(staffApi.listStaff).mockResolvedValue([]);
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue([]);
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-user-1',
        email: 'roberto.admin@laesquina.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-restaurante-alpha',
        tokenVersion: 1,
      },
      token: 'jwt-admin-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders admin workspace layout with title, operational cards, and tenant scope (T035)', async () => {
    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    expect(await screen.findByText('Panel de Administración')).toBeInTheDocument();
    expect(
      screen.getByText('Configuración global del establecimiento, usuarios y auditoría')
    ).toBeInTheDocument();
    expect(screen.getByText('Personal Activo')).toBeInTheDocument();
    expect(screen.getByText('Materias Primas')).toBeInTheDocument();
    expect(screen.getByText('Menú y Precios')).toBeInTheDocument();
    expect(screen.getByText('Auditoría de Sesiones')).toBeInTheDocument();
    expect(screen.getByTestId('admin-tenant-scope')).toHaveTextContent('Tenant ID: tenant-restaurante-alpha');
  });

  it('links to /admin/staff and /admin/inventory and displays real counts (T073, FR-028, FR-031)', async () => {
    vi.mocked(staffApi.listStaff).mockResolvedValue([
      { id: '1', email: 's1@test.com', fullName: 'Juan Perez', role: 'CHEF', status: 'ACTIVE', createdAt: '2026-10-24', updatedAt: '2026-10-24' },
      { id: '2', email: 's2@test.com', fullName: 'Ana Gomez', role: 'ENCARGADO', status: 'ACTIVE', createdAt: '2026-10-24', updatedAt: '2026-10-24' },
      { id: '3', email: 's3@test.com', fullName: 'Luis Diaz', role: 'ADMINISTRADOR', status: 'ACTIVE', createdAt: '2026-10-24', updatedAt: '2026-10-24' },
    ]);
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue([
      { id: '1', tenantId: 't1', name: 'Harina', unitOfMeasure: 'kg', currentStock: 25, unitPurchaseCost: 100, status: 'ACTIVE', category: 'Dry Goods', createdAt: '2026-10-24', updatedAt: '2026-10-24' },
      { id: '2', tenantId: 't1', name: 'Tomate', unitOfMeasure: 'kg', currentStock: 10, unitPurchaseCost: 150, status: 'ACTIVE', category: 'Produce', createdAt: '2026-10-24', updatedAt: '2026-10-24' },
    ]);

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    const staffLink = screen.getByText('Personal Activo').closest('a');
    expect(staffLink).toHaveAttribute('href', '/admin/staff');

    const inventoryLink = screen.getByText('Materias Primas').closest('a');
    expect(inventoryLink).toHaveAttribute('href', '/admin/inventory');

    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(await screen.findByText('2')).toBeInTheDocument();
  });

  it('displays fallback -- when count endpoints fail', async () => {
    vi.mocked(staffApi.listStaff).mockRejectedValue(new Error('Network error'));
    vi.mocked(inventoryApi.listIngredients).mockRejectedValue(new Error('Network error'));

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    const dashes = await screen.findAllByText('--');
    expect(dashes.length).toBeGreaterThanOrEqual(2);
  });

  it('invokes authApi.me on mount to validate token version and session liveness', async () => {
    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    expect(await screen.findByText('Panel de Administración')).toBeInTheDocument();
    expect(authApi.me).toHaveBeenCalledTimes(1);
  });
});

/**
 * T062 — the dish-review hub card (FR-003).
 *
 * The card is the only entry point the Administrator is given to the review inbox, and the number
 * on it is a claim about work waiting. Both halves are asserted, and the count is asserted against
 * the inbox payload specifically: FR-003 makes `pendingCount` the authority on dishes awaiting a
 * decision, and the admin notification feed — which the panel below this card still uses — counts
 * unread alerts instead. The two diverge the moment an alert is read, and a card reading zero over
 * a non-empty queue would tell the Administrator there is nothing to do.
 */
describe('AdminWorkspacePage - dish review hub card (T062, FR-003)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(staffApi.listStaff).mockResolvedValue([]);
    vi.mocked(inventoryApi.listIngredients).mockResolvedValue([]);
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-user-1',
        email: 'roberto.admin@laesquina.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-restaurante-alpha',
        tokenVersion: 1,
      },
      token: 'jwt-admin-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders the card and links it to the review inbox at /admin/dishes', async () => {
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(inboxPage({ pendingCount: 7 }));

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    const card = await screen.findByText('Menú y Precios');
    expect(card.closest('a')).toHaveAttribute('href', '/admin/dishes');
  });

  it('displays the pendingCount the inbox endpoint returns, not the unread alert count', async () => {
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(inboxPage({ pendingCount: 7 }));
    vi.mocked(adminNotificationApi.listNotifications).mockResolvedValue({
      content: [],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 2,
      totalPages: 1,
      isLast: true,
      unreadCount: 2,
    });

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    expect(await screen.findByTestId('admin-pending-review-count')).toHaveTextContent('7');
    expect(await screen.findByText('7 platos pendientes de fijación de precio')).toBeInTheDocument();
    expect(screen.queryByText('2 platos pendientes de fijación de precio')).not.toBeInTheDocument();
  });

  it('states the queue is empty when pendingCount is zero', async () => {
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(inboxPage({ pendingCount: 0 }));

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    expect(await screen.findByTestId('admin-pending-review-count')).toHaveTextContent('0');
    expect(screen.getByText('Sin platos pendientes de revisión')).toBeInTheDocument();
  });

  it('shows the -- fallback instead of a zero when the inbox count cannot be loaded', async () => {
    vi.mocked(dishReviewApi.getInbox).mockRejectedValue(new Error('Network error'));

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    // A zero here would be a false statement about an unknown queue, which is worse than no number.
    expect(await screen.findByTestId('admin-pending-review-count')).toHaveTextContent('--');
    expect(screen.queryByText(/platos pendientes de fijación de precio/)).not.toBeInTheDocument();
    // Nor may the card claim the queue is empty: it does not know that either.
    expect(screen.queryByText('Sin platos pendientes de revisión')).not.toBeInTheDocument();
  });

  it('asks the inbox for the smallest page that still carries the count', async () => {
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue(inboxPage({ pendingCount: 3 }));

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    await screen.findByText('3 platos pendientes de fijación de precio');
    // pendingCount comes back on every tab, so the hub has no use for the rows themselves.
    expect(dishReviewApi.getInbox).toHaveBeenCalledTimes(1);
    expect(dishReviewApi.getInbox).toHaveBeenCalledWith({ tab: 'PENDING', size: 1 });
  });

  it('keeps the link reachable when the count is still loading', async () => {
    let resolveInbox: (value: DishReviewInboxPage) => void = () => {};
    vi.mocked(dishReviewApi.getInbox).mockReturnValue(
      new Promise<DishReviewInboxPage>((resolve) => {
        resolveInbox = resolve;
      })
    );

    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    const card = await screen.findByText('Menú y Precios');
    expect(card.closest('a')).toHaveAttribute('href', '/admin/dishes');

    resolveInbox(inboxPage({ pendingCount: 1 }));
    expect(await screen.findByTestId('admin-pending-review-count')).toHaveTextContent('1');
  });
});
