import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AdminWorkspacePage } from '@/pages/AdminWorkspacePage';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

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

  it('renders admin workspace layout with title, operational cards, and tenant scope (T035)', () => {
    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByText('Panel de Administración')).toBeInTheDocument();
    expect(
      screen.getByText('Configuración global del establecimiento, usuarios y auditoría')
    ).toBeInTheDocument();
    expect(screen.getByText('Personal Activo')).toBeInTheDocument();
    expect(screen.getByText('Menú y Precios')).toBeInTheDocument();
    expect(screen.getByText('Auditoría de Sesiones')).toBeInTheDocument();
    expect(screen.getByTestId('admin-tenant-scope')).toHaveTextContent('Tenant ID: tenant-restaurante-alpha');
  });

  it('invokes authApi.me on mount to validate token version and session liveness', () => {
    render(
      <MemoryRouter>
        <AdminWorkspacePage />
      </MemoryRouter>
    );

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });
});
