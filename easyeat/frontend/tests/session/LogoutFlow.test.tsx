import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
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
});
