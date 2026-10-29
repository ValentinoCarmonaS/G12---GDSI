import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '@/router/AppRoutes';
import * as AuthModule from '@/hooks/useAuth';
import { authApi } from '@/api/authApi';
import { dishReviewApi } from '@/api/dishReviewApi';
import type { Role } from '@/types/auth';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
    getDetail: vi.fn(),
  },
}));
vi.mock('@/api/staffApi', () => ({
  staffApi: {
    listStaff: vi.fn().mockResolvedValue([]),
    createStaff: vi.fn(),
    updateStaff: vi.fn(),
  },
}));
vi.mock('@/api/inventoryApi', () => ({
  inventoryApi: {
    listIngredients: vi.fn().mockResolvedValue([]),
    createIngredient: vi.fn(),
    updateIngredient: vi.fn(),
    updateStatus: vi.fn(),
    adjustStock: vi.fn(),
    getAuditHistory: vi.fn(),
    getChefCatalog: vi.fn(),
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
vi.mock('@/api/dishApi', () => ({
  dishApi: {
    getFoodCostAudit: vi.fn().mockRejectedValue(new Error('not under test')),
  },
}));

/** The hub's subtitle: what tells AdminWorkspacePage apart from a page that links to it. */
const HUB_SUBTITLE = 'Configuración global del establecimiento, usuarios y auditoría';

function authenticateAs(role: Role) {
  vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
    isAuthenticated: true,
    user: {
      id: `${role.toLowerCase()}-1`,
      email: `${role.toLowerCase()}@easyeat.com`,
      role,
      tenantId: 'tenant-123',
      tokenVersion: 1,
    },
    token: `${role.toLowerCase()}-jwt`,
    setToken: vi.fn(),
    clearAuth: vi.fn(),
    logout: vi.fn(),
  });
}

/**
 * T060 — the routing perimeter of the Administrative Review Inbox (FR-001).
 *
 * `/admin/dishes` is a literal path declared alongside a `/admin/*` catch-all that renders the
 * administration hub. Route order is therefore load-bearing twice over: a late declaration makes
 * the catch-all answer first and the inbox unreachable, and an over-broad declaration swallows
 * `/admin/dishes/:id/cost-audit` and the rest of the admin perimeter. Both directions are asserted
 * here, because each one fails silently in the browser — the Administrator sees *a* page, just not
 * the one they asked for.
 */
describe('AppRoutes - /admin/dishes review inbox perimeter (T060, FR-001)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.mocked(dishReviewApi.getInbox).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      pendingCount: 0,
    });
  });

  it('resolves /admin/dishes to the review inbox before the /admin/* catch-all for an ADMINISTRADOR', async () => {
    authenticateAs('ADMINISTRADOR');

    render(
      <MemoryRouter initialEntries={['/admin/dishes']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/bandeja de revisión de platos/i)).toBeInTheDocument();
    // The catch-all hub must not have answered: seeing it here is exactly the ordering bug.
    // Identified by its subtitle, not its title — the inbox carries a "Volver al Panel de
    // Administración" link of its own, so the hub's name alone does not tell the two apart.
    expect(screen.queryByText(HUB_SUBTITLE)).not.toBeInTheDocument();
  });

  it('leaves the /admin/* catch-all serving every other admin path', async () => {
    authenticateAs('ADMINISTRADOR');

    render(
      <MemoryRouter initialEntries={['/admin/sesiones']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(HUB_SUBTITLE)).toBeInTheDocument();
    expect(screen.queryByText(/bandeja de revisión de platos/i)).not.toBeInTheDocument();
  });

  it('leaves the sibling /admin/dishes/:id/cost-audit route resolving to the cost audit page', async () => {
    authenticateAs('ADMINISTRADOR');

    render(
      <MemoryRouter initialEntries={['/admin/dishes/dish-42/cost-audit']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/auditoría financiera de costos/i)).toBeInTheDocument();
    expect(screen.queryByText(/bandeja de revisión de platos/i)).not.toBeInTheDocument();
  });

  it('blocks a CHEF from /admin/dishes and redirects to /cocina', async () => {
    authenticateAs('CHEF');

    render(
      <MemoryRouter initialEntries={['/admin/dishes']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
    expect(screen.queryByText(/bandeja de revisión de platos/i)).not.toBeInTheDocument();
  });

  it('blocks an ENCARGADO from /admin/dishes and redirects to /mostrador', async () => {
    authenticateAs('ENCARGADO');

    render(
      <MemoryRouter initialEntries={['/admin/dishes']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
    expect(screen.queryByText(/bandeja de revisión de platos/i)).not.toBeInTheDocument();
  });

  it('sends an unauthenticated visitor from /admin/dishes to the login gateway', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/dishes']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByRole('button', { name: /ingresar/i })).toBeInTheDocument();
    expect(screen.queryByText(/bandeja de revisión de platos/i)).not.toBeInTheDocument();
  });

  it('never requests the inbox payload for a role that cannot reach the route', async () => {
    authenticateAs('CHEF');

    render(
      <MemoryRouter initialEntries={['/admin/dishes']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
    expect(dishReviewApi.getInbox).not.toHaveBeenCalled();
  });
});
