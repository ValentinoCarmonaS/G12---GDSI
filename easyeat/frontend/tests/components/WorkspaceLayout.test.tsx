import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

describe('WorkspaceLayout (T026, FR-026, COM-1.4.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders diner navigation links and logout button when user is COMENSAL', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: {
        id: 'user-diner-1',
        email: 'comensal@easyeat.com',
        role: 'COMENSAL',
        tokenVersion: 1,
        tenantId: null,
      },
      token: 'jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <WorkspaceLayout title="Mi Perfil">
          <div>Profile content</div>
        </WorkspaceLayout>
      </MemoryRouter>
    );

    const catalogoLink = screen.getByRole('link', { name: /catálogo/i });
    expect(catalogoLink).toBeInTheDocument();
    expect(catalogoLink).toHaveAttribute('href', '/catalogo');

    const perfilLink = screen.getByRole('link', { name: /mi perfil/i });
    expect(perfilLink).toBeInTheDocument();
    expect(perfilLink).toHaveAttribute('href', '/perfil');

    expect(screen.getByRole('button', { name: /cerrar sesión/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /iniciar sesión/i })).not.toBeInTheDocument();
  });

  it('renders guest login and register actions and hides logout when user is null (FR-026)', () => {
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <WorkspaceLayout title="Catálogo de Platos">
          <div>Catalog content</div>
        </WorkspaceLayout>
      </MemoryRouter>
    );

    expect(screen.queryByRole('button', { name: /cerrar sesión/i })).not.toBeInTheDocument();

    const loginLink = screen.getByRole('link', { name: /iniciar sesión/i });
    expect(loginLink).toBeInTheDocument();
    expect(loginLink).toHaveAttribute('href', '/login');

    const registerLink = screen.getByRole('link', { name: /registrarse/i });
    expect(registerLink).toBeInTheDocument();
    expect(registerLink).toHaveAttribute('href', '/register');
  });
});
