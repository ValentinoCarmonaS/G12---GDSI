import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { RoleRouteGuard } from '@/router/RoleRouteGuard';
import { resolvePostAuthRedirect } from '@/router/postAuthRouting';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

const LocationWatcher: React.FC = () => {
  const location = useLocation();
  return (
    <div>
      <span data-testid="current-path">{location.pathname}</span>
    </div>
  );
};

describe('First Login Routing & Perimeter Guard (FR-003, FR-030)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('redirects authenticated staff with mustChangePassword=true to /first-login-password-change when accessing admin workspace', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'staff-pending-1',
        email: 'newadmin@restaurant.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-100',
        tokenVersion: 0,
        mustChangePassword: true,
      },
      token: 'mock-jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin/staff']}>
        <Routes>
          <Route
            path="/first-login-password-change"
            element={
              <div>
                <h1>First Login Password Change Page</h1>
                <LocationWatcher />
              </div>
            }
          />
          <Route
            path="/admin/*"
            element={
              <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
                <h1>Admin Workspace</h1>
              </RoleRouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('First Login Password Change Page')).toBeInTheDocument();
    expect(screen.queryByText('Admin Workspace')).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/first-login-password-change');
  });

  it('redirects authenticated chef with mustChangePassword=true to /first-login-password-change when accessing kitchen workspace', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-pending-2',
        email: 'newchef@restaurant.com',
        role: 'CHEF',
        tenantId: 'tenant-100',
        tokenVersion: 0,
        mustChangePassword: true,
      },
      token: 'mock-jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/cocina']}>
        <Routes>
          <Route
            path="/first-login-password-change"
            element={
              <div>
                <h1>First Login Password Change Page</h1>
                <LocationWatcher />
              </div>
            }
          />
          <Route
            path="/cocina"
            element={
              <RoleRouteGuard allowedRoles={['CHEF']}>
                <h1>Kitchen Workspace</h1>
              </RoleRouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('First Login Password Change Page')).toBeInTheDocument();
    expect(screen.queryByText('Kitchen Workspace')).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/first-login-password-change');
  });

  it('allows staff with mustChangePassword=false to proceed to their authorized workspace', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'admin-active-1',
        email: 'activeadmin@restaurant.com',
        role: 'ADMINISTRADOR',
        tenantId: 'tenant-100',
        tokenVersion: 1,
        mustChangePassword: false,
      },
      token: 'mock-jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/first-login-password-change"
            element={<div>First Login Password Change Page</div>}
          />
          <Route
            path="/admin"
            element={
              <RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>
                <h1>Admin Workspace</h1>
              </RoleRouteGuard>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Admin Workspace')).toBeInTheDocument();
    expect(screen.queryByText('First Login Password Change Page')).not.toBeInTheDocument();
  });

  it('resolvePostAuthRedirect returns /first-login-password-change when mustChangePassword is true regardless of requested deep link', () => {
    const destination = resolvePostAuthRedirect('ADMINISTRADOR', '/admin/inventory', true);
    expect(destination).toBe('/first-login-password-change');
  });

  it('resolvePostAuthRedirect respects target deep link when mustChangePassword is false', () => {
    const destination = resolvePostAuthRedirect('ADMINISTRADOR', '/admin/inventory', false);
    expect(destination).toBe('/admin/inventory');
  });
});

