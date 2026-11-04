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

describe('WorkspaceLayout chef header navigation (C-08, FR-027/FR-030/FR-031)', () => {
  const chefUser = {
    id: 'user-chef-1',
    email: 'chef@easyeat.com',
    role: 'CHEF' as const,
    tokenVersion: 1,
    tenantId: 'tenant-comedor-1',
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(AuthModule, 'useAuth').mockReturnValue({
      isAuthenticated: true,
      user: chefUser,
      token: 'jwt-token',
      setToken: vi.fn(),
      clearAuth: vi.fn(),
      logout: vi.fn(),
    });
  });

  const renderChefLayout = (initialEntry: string) =>
    render(
      <MemoryRouter initialEntries={[initialEntry]}>
        <WorkspaceLayout title="Kitchen Display System (KDS)">
          <div>Workspace content</div>
        </WorkspaceLayout>
      </MemoryRouter>
    );

  it('exposes the three chef sections with their canonical routes (FR-027)', () => {
    renderChefLayout('/cocina');

    const kds = screen.getByRole('link', { name: 'Comandas KDS' });
    const dishes = screen.getByRole('link', { name: 'Gestionar Platos' });
    const ingredients = screen.getByRole('link', { name: 'Ingredientes' });

    expect(kds).toHaveAttribute('href', '/cocina');
    expect(dishes).toHaveAttribute('href', '/cocina/platos');
    expect(ingredients).toHaveAttribute('href', '/cocina/ingredientes');
  });

  it('derives the active section from the route with aria-current="page" (FR-030)', () => {
    renderChefLayout('/cocina');

    expect(screen.getByRole('link', { name: 'Comandas KDS' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('link', { name: 'Gestionar Platos' })).not.toHaveAttribute(
      'aria-current'
    );
    expect(screen.getByRole('link', { name: 'Ingredientes' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('lights up "Gestionar Platos" — never "Comandas KDS" — on nested dish routes (FR-030)', () => {
    renderChefLayout('/cocina/platos/nuevo');

    expect(screen.getByRole('link', { name: 'Gestionar Platos' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('link', { name: 'Comandas KDS' })).not.toHaveAttribute(
      'aria-current'
    );
    expect(screen.getByRole('link', { name: 'Ingredientes' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('lights up "Ingredientes" on the ingredients route (FR-030)', () => {
    renderChefLayout('/cocina/ingredientes');

    expect(screen.getByRole('link', { name: 'Ingredientes' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('link', { name: 'Comandas KDS' })).not.toHaveAttribute(
      'aria-current'
    );
    expect(screen.getByRole('link', { name: 'Gestionar Platos' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('keeps the chef navigation visible and horizontally scrollable instead of hidden below sm (FR-031)', () => {
    renderChefLayout('/cocina');

    const nav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    expect(nav).not.toHaveClass('hidden');
    expect(nav).toHaveClass('overflow-x-auto');
    expect(nav).toHaveClass('min-w-0');
  });
});
