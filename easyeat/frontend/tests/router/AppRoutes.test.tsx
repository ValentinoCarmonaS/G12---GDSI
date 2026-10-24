import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '@/router/AppRoutes';
import * as AuthModule from '@/hooks/useAuth';
import { authApi } from '@/api/authApi';

vi.mock('@/hooks/useAuth');

describe('AppRoutes - Counter Workspace Routing (T021, ENC-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
  });

  it('renders CounterWorkspacePage when authenticated ENCARGADO visits /mostrador', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'encargado-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/mostrador']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
  });

  it('instantly redirects authenticated ENCARGADO from /login to /mostrador', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'encargado-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
  });

  it('redirects authenticated ENCARGADO from root / to /mostrador', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'encargado-1',
        email: 'maria.encargada@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/mostrador y despacho/i)).toBeInTheDocument();
  });
});
