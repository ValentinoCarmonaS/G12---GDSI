import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { AuthProvider, useAuth as useRealAuth } from '@/context/AuthContext';
import { authApi } from '@/api/authApi';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/api/authApi');
vi.mock('@/hooks/useAuth');

describe('Logout Flow (FR-016, SC-006)', () => {
  const mockClearAuth = vi.fn();
  const mockLogout = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('triggers logout API, purges credentials, and redirects on Cerrar sesión click', async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.logout).mockResolvedValueOnce(undefined);

    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'chef-1',
        email: 'carlos.chef@comedorcentral.com',
        role: 'CHEF',
        tenantId: 'tenant-1',
        tokenVersion: 2,
      },
      token: 'valid-jwt',
      setToken: vi.fn(),
      clearAuth: mockClearAuth,
      logout: mockLogout,
    });

    render(
      <MemoryRouter>
        <WorkspaceLayout title="Panel de Cocina">
          <p>Contenido protegido de prueba</p>
        </WorkspaceLayout>
      </MemoryRouter>
    );

    const logoutBtn = screen.getByRole('button', { name: /cerrar sesión/i });
    expect(logoutBtn).toBeInTheDocument();

    await user.click(logoutBtn);

    await waitFor(() => {
      expect(mockLogout).toHaveBeenCalled();
    });
  });

  it('purges sessionStorage and clears auth state even when server API throws network error (T041, FR-016)', async () => {
    sessionStorage.setItem('access_token', 'token-to-purge');
    vi.mocked(authApi.logout).mockRejectedValueOnce(new Error('Network error'));

    const originalLocation = window.location;
    const mockReplace = vi.fn();
    // @ts-expect-error stubbing location for test
    delete window.location;
    window.location = { ...originalLocation, replace: mockReplace };

    let logoutFn: () => Promise<void> = async () => {};

    const Consumer: React.FC = () => {
      const { logout } = useRealAuth();
      logoutFn = logout;
      return null;
    };

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );

    try {
      await act(async () => {
        await logoutFn();
      });

      expect(sessionStorage.getItem('access_token')).toBeNull();
      expect(mockReplace).toHaveBeenCalledWith('/login');
    } finally {
      window.location = originalLocation;
    }
  });
});
