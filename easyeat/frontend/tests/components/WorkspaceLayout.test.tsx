import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import * as AuthModule from '@/hooks/useAuth';
import * as brandingApi from '@/api/brandingApi';

vi.mock('@/hooks/useAuth');
vi.mock('@/api/brandingApi', () => ({
  getDinerBranding: vi.fn(),
}));

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

    const myOrdersLink = screen.getByRole('link', { name: /mis pedidos/i });
    expect(myOrdersLink).toBeInTheDocument();
    expect(myOrdersLink).toHaveAttribute('href', '/perfil#pedidos');

    const perfilLink = screen.getByRole('link', { name: /mi perfil/i });
    expect(perfilLink).toBeInTheDocument();
    expect(perfilLink).toHaveAttribute('href', '/perfil');

    const dinerNav = myOrdersLink.closest('nav');
    expect(dinerNav).not.toBeNull();
    expect(dinerNav).not.toHaveClass('hidden');
    expect(dinerNav).toHaveClass('overflow-x-auto');
    expect(dinerNav).toHaveClass('min-w-0');

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

  it('exposes the three chef sections with their canonical routes (FR-027)', async () => {
    const user = userEvent.setup();
    renderChefLayout('/cocina');

    // Menu dropdown button exists and is accessible
    const menuButton = screen.getByRole('button', { name: /menú de navegación|secciones de cocina/i });
    expect(menuButton).toBeInTheDocument();
    await user.click(menuButton);

    const kds = screen.getByRole('link', { name: 'Comandas KDS' });
    const dishes = screen.getByRole('link', { name: 'Gestionar Platos' });
    const ingredients = screen.getByRole('link', { name: 'Ingredientes' });

    expect(kds).toHaveAttribute('href', '/cocina');
    expect(dishes).toHaveAttribute('href', '/cocina/platos');
    expect(ingredients).toHaveAttribute('href', '/cocina/ingredientes');
  });

  it('derives the active section from the route with aria-current="page" (FR-030)', async () => {
    const user = userEvent.setup();
    renderChefLayout('/cocina');

    const menuButton = screen.getByRole('button', { name: /menú de navegación|secciones de cocina/i });
    await user.click(menuButton);

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

  it('lights up "Gestionar Platos" — never "Comandas KDS" — on nested dish routes (FR-030)', async () => {
    const user = userEvent.setup();
    renderChefLayout('/cocina/platos/nuevo');

    const menuButton = screen.getByRole('button', { name: /menú de navegación|secciones de cocina/i });
    await user.click(menuButton);

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

  it('lights up "Ingredientes" on the ingredients route (FR-030)', async () => {
    const user = userEvent.setup();
    renderChefLayout('/cocina/ingredientes');

    const menuButton = screen.getByRole('button', { name: /menú de navegación|secciones de cocina/i });
    await user.click(menuButton);

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

  it('omits avatar and profile link for CHEF role and removes redundant KDS title from header', () => {
    renderChefLayout('/cocina');

    // Avatar and profile link are not rendered for CHEF
    expect(screen.queryByTitle(/mi perfil/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/avatar de usuario/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /mi perfil/i })).not.toBeInTheDocument();

    // Redundant textual title is omitted in header for CHEF, showing only EasyEat branding
    const header = screen.getByRole('banner');
    expect(header).toHaveTextContent('EasyEat');
    expect(header).not.toHaveTextContent('Kitchen Display System (KDS)');
  });

  it('renders operational footer with branding fantasy name, CHEF badge, email and logout button', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce({
      establishmentId: 'tenant-comedor-1',
      fantasyName: 'Café Parque',
      description: 'El mejor café',
      primaryColor: '#000000',
      secondaryColor: '#ffffff',
      accentColor: '#eab308',
      primaryContrastColor: '#ffffff',
      secondaryContrastColor: '#000000',
      accentContrastColor: '#000000',
      updatedAt: '2026-11-16T10:00:00Z',
      isDefaultFallback: false,
    });

    renderChefLayout('/cocina');

    const footer = screen.getByRole('contentinfo');
    expect(footer).toBeInTheDocument();

    await waitFor(() => {
      expect(footer).toHaveTextContent('Café Parque');
    });

    expect(footer).toHaveTextContent('CHEF');
    expect(footer).toHaveTextContent('chef@easyeat.com');
    expect(footer).toHaveTextContent('Cerrar sesión');
  });

  it('renders fallback short ID with full UUID tooltip in footer when branding has no fantasy name or fails', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockRejectedValueOnce(new Error('Network error'));

    renderChefLayout('/cocina');

    const footer = screen.getByRole('contentinfo');
    expect(footer).toBeInTheDocument();

    await waitFor(() => {
      expect(footer).toHaveTextContent('Local: tenant-c');
    });

    const tenantBadge = footer.querySelector('[title="Tenant: tenant-comedor-1"]');
    expect(tenantBadge).toBeInTheDocument();
  });
});
