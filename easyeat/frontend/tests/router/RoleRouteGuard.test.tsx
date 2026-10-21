import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { RoleRouteGuard } from '@/router/RoleRouteGuard';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

const LocationWatcher: React.FC = () => {
  const location = useLocation();
  return (
    <div>
      <span data-testid="current-path">{location.pathname}</span>
      <span data-testid="state-from">{(location.state as { from?: string })?.from || ''}</span>
    </div>
  );
};

describe('RoleRouteGuard Component', () => {
  it('redirects unauthenticated user to /login and preserves target URL in state.from (FR-011)', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/counter/dispatch/10']}>
        <Routes>
          <Route path="/login" element={<div><p>Login Page</p><LocationWatcher /></div>} />
          <Route
            path="/counter/dispatch/:id"
            element={
              <RoleRouteGuard allowedRoles={['ENCARGADO']}>
                <p>Counter Workspace View</p>
              </RoleRouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Counter Workspace View')).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/login');
    expect(screen.getByTestId('state-from')).toHaveTextContent('/counter/dispatch/10');
  });

  it('silently redirects authenticated user attempting cross-role access to their own workspace (FR-012, SC-003)', () => {
    // A CHEF attempting to access /admin
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@comedorcentral.com',
        role: 'CHEF',
        tenantId: 'tenant-1',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/kds" element={<div><p>Kitchen KDS Workspace</p><LocationWatcher /></div>} />
          <Route
            path="/admin"
            element={
              <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
                <p>Admin Workspace View</p>
              </RoleRouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    // Guard silently routes CHEF to /kds without showing Admin content or intermediate prompt
    expect(screen.getByText('Kitchen KDS Workspace')).toBeInTheDocument();
    expect(screen.queryByText('Admin Workspace View')).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/kds');
  });

  it('renders protected child component when authenticated user has authorized role', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'manager-1',
        email: 'maria@laesquina.com',
        role: 'ENCARGADO',
        tenantId: 'tenant-1',
        tokenVersion: 1,
      },
      token: 'mock-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/counter']}>
        <Routes>
          <Route
            path="/counter"
            element={
              <RoleRouteGuard allowedRoles={['ENCARGADO']}>
                <p>Counter Protected Workspace</p>
              </RoleRouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Counter Protected Workspace')).toBeInTheDocument();
  });
});
