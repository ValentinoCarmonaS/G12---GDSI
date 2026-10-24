import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { authApi } from '@/api/authApi';
import { staffApi } from '@/api/staffApi';
import { inventoryApi } from '@/api/inventoryApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/api/staffApi');
vi.mock('@/api/inventoryApi');
vi.mock('@/hooks/useAuth');

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
