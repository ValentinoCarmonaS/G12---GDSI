import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { KitchenWorkspacePage } from '@/pages/KitchenWorkspacePage';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

describe('KitchenWorkspacePage (T029, CHF-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-user-1',
        email: 'carlos.chef@comedorcentral.com',
        role: 'CHEF',
        tenantId: 'tenant-comedor-1',
        tokenVersion: 1,
      },
      token: 'jwt-chef-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders kitchen workspace layout with KDS title and order status cards (T029)', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByText(/kitchen display system/i)).toBeInTheDocument();
    expect(screen.getByText(/comandas en preparación y despachos de cocina/i)).toBeInTheDocument();
    expect(screen.getByText('En Cola')).toBeInTheDocument();
    expect(screen.getByText('En Preparación')).toBeInTheDocument();
    expect(screen.getByText('Para Retirar')).toBeInTheDocument();
    expect(screen.getByText('Completadas hoy')).toBeInTheDocument();
  });

  it('invokes authApi.me on mount to validate token version and session liveness', () => {
    render(
      <MemoryRouter>
        <KitchenWorkspacePage />
      </MemoryRouter>
    );

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });
});
