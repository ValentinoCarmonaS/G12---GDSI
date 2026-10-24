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

describe('AppRoutes - Kitchen Workspace Routing (T031, CHF-1.2.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
  });

  it('renders KitchenWorkspacePage when authenticated CHEF visits /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/cocina']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });

  it('instantly redirects authenticated CHEF from /login to /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
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

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });

  it('redirects authenticated CHEF from root / to /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
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

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });

  it('redirects legacy /kds route to /cocina', async () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        tenantId: 'tenant-123',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/kds']}>
        <AppRoutes />
      </MemoryRouter>
    );

    expect(await screen.findByText(/kitchen display system/i)).toBeInTheDocument();
  });
});
