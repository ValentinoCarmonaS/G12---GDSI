import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import * as AuthModule from '@/hooks/useAuth';

vi.mock('@/hooks/useAuth');

describe('WorkspaceLayout (T026, FR-026, COM-1.4.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders diner navigation links and logout button when user is COMENSAL', async () => {
    const user = userEvent.setup();
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

    expect(screen.queryByRole('button', { name: /cerrar sesión/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /iniciar sesión/i })).not.toBeInTheDocument();

    const menuTrigger = screen.getByRole('button', { name: /abrir menú de navegación/i });
    await user.click(menuTrigger);

    const dinerNav = screen.getByRole('navigation', { name: /navegación de comensal/i });
    expect(dinerNav).toBeInTheDocument();
    expect(within(dinerNav).getByRole('button', { name: /cerrar sesión/i })).toBeInTheDocument();

    const discoveryLink = within(dinerNav).getByRole('link', { name: /descubrimiento/i });
    expect(discoveryLink).toHaveAttribute('href', '/descubrimiento');

    const catalogoLink = within(dinerNav).getByRole('link', { name: /catálogo/i });
    expect(catalogoLink).toHaveAttribute('href', '/catalogo');

    const myOrdersLink = within(dinerNav).getByRole('link', { name: /mis pedidos/i });
    expect(myOrdersLink).toHaveAttribute('href', '/perfil#pedidos');

    const perfilLink = within(dinerNav).getByRole('link', { name: /mi perfil/i });
    expect(perfilLink).toHaveAttribute('href', '/perfil');
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

    const chefNav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    const kds = within(chefNav).getByRole('link', { name: 'Comandas KDS' });
    const dishes = within(chefNav).getByRole('link', { name: 'Gestionar Platos' });
    const ingredients = within(chefNav).getByRole('link', { name: 'Ingredientes' });

    expect(kds).toHaveAttribute('href', '/cocina');
    expect(dishes).toHaveAttribute('href', '/cocina/platos');
    expect(ingredients).toHaveAttribute('href', '/cocina/ingredientes');
  });

  it('derives the active section from the route with aria-current="page" (FR-030)', () => {
    renderChefLayout('/cocina');

    const chefNav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    expect(within(chefNav).getByRole('link', { name: 'Comandas KDS' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(within(chefNav).getByRole('link', { name: 'Gestionar Platos' })).not.toHaveAttribute(
      'aria-current'
    );
    expect(within(chefNav).getByRole('link', { name: 'Ingredientes' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('lights up "Gestionar Platos" — never "Comandas KDS" — on nested dish routes (FR-030)', () => {
    renderChefLayout('/cocina/platos/nuevo');

    const chefNav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    expect(within(chefNav).getByRole('link', { name: 'Gestionar Platos' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(within(chefNav).getByRole('link', { name: 'Comandas KDS' })).not.toHaveAttribute(
      'aria-current'
    );
    expect(within(chefNav).getByRole('link', { name: 'Ingredientes' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('lights up "Ingredientes" on the ingredients route (FR-030)', () => {
    renderChefLayout('/cocina/ingredientes');

    const chefNav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    expect(within(chefNav).getByRole('link', { name: 'Ingredientes' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(within(chefNav).getByRole('link', { name: 'Comandas KDS' })).not.toHaveAttribute(
      'aria-current'
    );
    expect(within(chefNav).getByRole('link', { name: 'Gestionar Platos' })).not.toHaveAttribute(
      'aria-current'
    );
  });

  it('keeps the chef direct navigation on desktop (md:flex) alongside the accessible hamburger menu (FR-009, C-08)', () => {
    renderChefLayout('/cocina');

    const nav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    expect(nav).toHaveClass('md:flex');
    expect(screen.getByRole('button', { name: /abrir menú de navegación/i })).toBeInTheDocument();
  });
});
