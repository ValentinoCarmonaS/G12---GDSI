import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import { AuthProvider, useAuth } from '@/context/AuthContext';

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
});
