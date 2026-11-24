import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';
import { AdminPendingDishesProvider } from '@/context/AdminPendingDishesContext';

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

describe('AdminWorkspacePage (T035, ADM-1.1.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

  it('renders executive panel layout with title, subtitle, dashboard grid, and tenant scope (T035)', async () => {
    render(
      <MemoryRouter>
        <AdminPendingDishesProvider>
          <AdminWorkspacePage />
        </AdminPendingDishesProvider>
      </MemoryRouter>
    );

    expect((await screen.findAllByText('Panel Ejecutivo')).length).toBeGreaterThan(0);
    expect(
      screen.getByText('Salud financiera y demanda de platos en tiempo real')
    ).toBeInTheDocument();
    expect(screen.getByTestId('executive-dashboard-grid')).toBeInTheDocument();
  });

  it('renders AdminSidebar navigation links for all admin sections (T073, FR-028, FR-031)', async () => {
    render(
      <MemoryRouter>
        <AdminPendingDishesProvider>
          <AdminWorkspacePage />
        </AdminPendingDishesProvider>
      </MemoryRouter>
    );

    expect(await screen.findByRole('link', { name: /^panel$/i })).toHaveAttribute('href', '/admin');
    expect(screen.getByRole('link', { name: /^operaciones$/i })).toHaveAttribute('href', '/admin/operations');
    expect(screen.getByRole('link', { name: /^inventario$/i })).toHaveAttribute('href', '/admin/inventory');
    expect(screen.getByRole('link', { name: /^auditoría$/i })).toHaveAttribute('href', '/admin/dishes');
    expect(screen.getByRole('link', { name: /^personal$/i })).toHaveAttribute('href', '/admin/staff');
    expect(screen.getByRole('link', { name: /^mi local$/i })).toHaveAttribute('href', '/admin/mi-local');
  });

  it('invokes authApi.me on mount to validate token version and session liveness', async () => {
    render(
      <MemoryRouter>
        <AdminPendingDishesProvider>
          <AdminWorkspacePage />
        </AdminPendingDishesProvider>
      </MemoryRouter>
    );

    expect((await screen.findAllByText('Panel Ejecutivo')).length).toBeGreaterThan(0);
    expect(authApi.me).toHaveBeenCalledTimes(1);
  });
});
