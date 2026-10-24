import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import { AppRoutes } from '@/router/AppRoutes';
import { authApi } from '@/api/authApi';

function createMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.mock-signature`;
}

describe('AdminLogin Component Integration (T033, ADM-1.1.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    localStorage.clear();
    vi.spyOn(authApi, 'me').mockResolvedValue(undefined);
  });

  it('submits administrator credentials via shared LoginForm, authenticates and routes to /admin', async () => {
    const user = userEvent.setup();
    const adminToken = createMockJwt({
      sub: 'admin-uuid-789',
      email: 'roberto.admin@laesquina.com',
      role: 'ADMINISTRADOR',
      tenant_id: 'tenant-restaurante-alpha',
      token_version: 1,
      exp: Math.floor(Date.now() / 1000) + 28800,
    });

    vi.spyOn(authApi, 'login').mockResolvedValueOnce({
      access_token: adminToken,
      token_type: 'Bearer',
      expires_in: 28800,
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: /easyeat/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();

    const emailInput = screen.getByLabelText(/correo electrónico/i);
    const passwordInput = screen.getByLabelText(/contraseña/i);
    const submitBtn = screen.getByRole('button', { name: /ingresar/i });

    await user.type(emailInput, '  Roberto.Admin@LaEsquina.com  ');
    await user.type(passwordInput, 'AdminSuperPassword123!');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'roberto.admin@laesquina.com',
        password: 'AdminSuperPassword123!',
      });
    });

    expect(await screen.findByText(/panel de administración/i)).toBeInTheDocument();
    expect(screen.getByText(/configuración global del establecimiento/i)).toBeInTheDocument();
    expect(screen.getByText('ADMINISTRADOR')).toBeInTheDocument();
  });
});
