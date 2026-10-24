import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { LoginForm } from '@/components/auth/LoginForm';
import { authApi } from '@/api/authApi';

function createMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.mock-signature`;
}

describe('AuthContext and useAuth', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  const wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <AuthProvider>{children}</AuthProvider>
  );

  it('initializes with unauthenticated state when sessionStorage is empty', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(result.current.token).toBeNull();
  });

  it('restores authentication state from valid token in sessionStorage', () => {
    const mockToken = createMockJwt({
      sub: 'user-uuid-1',
      email: 'chef@restaurant.com',
      role: 'CHEF',
      tenant_id: 'tenant-uuid-1',
      token_version: 2,
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    sessionStorage.setItem('access_token', mockToken);

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.token).toBe(mockToken);
    expect(result.current.user).toEqual({
      id: 'user-uuid-1',
      email: 'chef@restaurant.com',
      role: 'CHEF',
      tenantId: 'tenant-uuid-1',
      tokenVersion: 2,
    });
  });

  it('synchronizes sessionStorage and auth state on setToken', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    const newToken = createMockJwt({
      sub: 'manager-uuid-2',
      email: 'maria@laesquina.com',
      role: 'ENCARGADO',
      tenant_id: 'tenant-uuid-2',
      token_version: 1,
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    act(() => {
      result.current.setToken(newToken);
    });

    expect(sessionStorage.getItem('access_token')).toBe(newToken);
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.role).toBe('ENCARGADO');
    expect(result.current.user?.email).toBe('maria@laesquina.com');
  });

  it('clears state and storage on logout', () => {
    const mockToken = createMockJwt({
      sub: 'admin-uuid-3',
      email: 'admin@easyeat.com',
      role: 'ADMINISTRADOR',
      tenant_id: 'tenant-uuid-3',
      token_version: 1,
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    sessionStorage.setItem('access_token', mockToken);

    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAuthenticated).toBe(true);

    act(() => {
      result.current.clearAuth();
    });

    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it('asserts chef login via shared LoginForm initializes active session state with role: CHEF (T026, CHF-1.1.1)', async () => {
    const user = userEvent.setup();
    const mockToken = createMockJwt({
      sub: 'chef-uuid-101',
      email: 'carlos.chef@comedorcentral.com',
      role: 'CHEF',
      tenant_id: 'tenant-comedor-1',
      token_version: 1,
      exp: Math.floor(Date.now() / 1000) + 28800,
    });

    vi.spyOn(authApi, 'login').mockResolvedValueOnce({
      access_token: mockToken,
      token_type: 'Bearer',
      expires_in: 28800,
    });

    const ChefLoginIntegration: React.FC = () => {
      const { user: authUser, isAuthenticated, setToken } = useAuth();
      const handleSubmit = async (credentials: { email: string; password: string }) => {
        const response = await authApi.login(credentials);
        setToken(response.access_token);
      };

      return (
        <div>
          <span data-testid="auth-status">{isAuthenticated ? 'authenticated' : 'unauthenticated'}</span>
          {authUser && <span data-testid="auth-role">{authUser.role}</span>}
          {authUser && <span data-testid="auth-email">{authUser.email}</span>}
          {authUser && <span data-testid="auth-tenant">{authUser.tenantId}</span>}
          <LoginForm onSubmit={handleSubmit} />
        </div>
      );
    };

    render(
      <MemoryRouter>
        <AuthProvider>
          <ChefLoginIntegration />
        </AuthProvider>
      </MemoryRouter>
    );

    expect(screen.getByTestId('auth-status')).toHaveTextContent('unauthenticated');

    await user.type(screen.getByLabelText(/correo electrónico/i), 'Carlos.Chef@ComedorCentral.Com');
    await user.type(screen.getByLabelText(/contraseña/i), 'ChefPassword123');
    await user.click(screen.getByRole('button', { name: /ingresar/i }));

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'carlos.chef@comedorcentral.com',
        password: 'ChefPassword123',
      });
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
      expect(screen.getByTestId('auth-role')).toHaveTextContent('CHEF');
      expect(screen.getByTestId('auth-email')).toHaveTextContent('carlos.chef@comedorcentral.com');
      expect(screen.getByTestId('auth-tenant')).toHaveTextContent('tenant-comedor-1');
      expect(sessionStorage.getItem('access_token')).toBe(mockToken);
    });
  });
});

