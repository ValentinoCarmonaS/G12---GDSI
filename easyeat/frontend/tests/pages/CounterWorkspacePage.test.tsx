import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { CounterWorkspacePage } from '@/pages/CounterWorkspacePage';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

describe('CounterWorkspacePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-enc-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-enc-1',
        tokenVersion: 1,
      },
      token: 'jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('renders workspace layout with counter title and operational cards (T022)', () => {
    render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    expect(screen.getByText('Mostrador y Despacho')).toBeInTheDocument();
    expect(
      screen.getByText('Punto de atención para gestión y entrega de pedidos en salón')
    ).toBeInTheDocument();
    expect(screen.getByText('Pedidos por cobrar')).toBeInTheDocument();
    expect(screen.getByText('Listos para entrega')).toBeInTheDocument();
    expect(screen.getByText('Caja actual')).toBeInTheDocument();
  });

  it('invokes authApi.me on mount to validate token version and session liveness', () => {
    render(
      <MemoryRouter>
        <CounterWorkspacePage />
      </MemoryRouter>
    );

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });
});
