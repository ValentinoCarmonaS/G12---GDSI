import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { RoleLayoutShell } from '@/components/layout/RoleLayoutShell';
import type { AuthenticatedUser } from '@/types/auth';
import type { AdminNotificationResponse } from '@/types/notification';

interface RenderShellOptions {
  title?: string;
  subtitle?: string;
  user?: AuthenticatedUser | null;
  initialEntry?: string;
  avatarUrl?: string | null;
  notifications?: AdminNotificationResponse[];
  unreadCount?: number;
  legacySurfaceMode?: 'none' | 'legacy-light' | 'legacy-dark';
  onLogout?: () => void | Promise<void>;
  onMarkAsRead?: (id: string) => void | Promise<void>;
  onRefreshNotifications?: () => void | Promise<void>;
}

function renderShell({
  title = 'Panel Operativo',
  subtitle,
  user = null,
  initialEntry = '/',
  avatarUrl = null,
  notifications = [],
  unreadCount = 0,
  legacySurfaceMode = 'none',
  onLogout = vi.fn(),
  onMarkAsRead = vi.fn(),
  onRefreshNotifications = vi.fn(),
}: RenderShellOptions = {}) {
  return {
    onLogout,
    ...render(
      <MemoryRouter initialEntries={[initialEntry]}>
        <RoleLayoutShell
          title={title}
          subtitle={subtitle}
          user={user}
          avatarUrl={avatarUrl}
          notifications={notifications}
          unreadCount={unreadCount}
          legacySurfaceMode={legacySurfaceMode}
          onLogout={onLogout}
          onMarkAsRead={onMarkAsRead}
          onRefreshNotifications={onRefreshNotifications}
        >
          <div data-testid="shell-body">Contenido principal</div>
        </RoleLayoutShell>
      </MemoryRouter>
    ),
  };
}

describe('RoleLayoutShell (FR-007, FR-009, FR-010, FR-018)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders a skip link targeting #main-content and main landmark', () => {
    renderShell({ title: 'Catálogo' });

    const skipLink = screen.getByRole('link', { name: 'Saltar al contenido principal' });
    expect(skipLink).toHaveAttribute('href', '#main-content');

    const main = screen.getByRole('main');
    expect(main).toHaveAttribute('id', 'main-content');
  });

  it('renders minimalist COMENSAL header without raw enum or email, with navigable avatar to /perfil and logout inside hamburger menu', async () => {
    const user = userEvent.setup();
    const { onLogout } = renderShell({
      title: 'Catálogo',
      initialEntry: '/catalogo',
      user: {
        id: 'comensal-1',
        email: 'lucia@easyeat.com',
        role: 'COMENSAL',
        tenantId: null,
        tokenVersion: 1,
      },
    });

    const header = screen.getByRole('banner');
    expect(within(header).queryByText('COMENSAL')).not.toBeInTheDocument();
    expect(within(header).queryByText('lucia@easyeat.com')).not.toBeInTheDocument();
    expect(within(header).queryByTestId('tenant-badge')).not.toBeInTheDocument();
    expect(within(header).queryByRole('button', { name: /cerrar sesión/i })).not.toBeInTheDocument();

    const avatarLink = within(header).getByRole('link', { name: /mi perfil/i });
    expect(avatarLink).toHaveAttribute('href', '/perfil');
    expect(avatarLink).toHaveClass('h-11', 'w-11');

    const menuTrigger = within(header).getByRole('button', { name: /abrir menú de navegación/i });
    await user.click(menuTrigger);

    const logoutButton = within(header).getByRole('button', { name: /cerrar sesión/i });
    await user.click(logoutButton);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('keeps the COMENSAL profile link fixed at 44x44 (h-11 w-11) both with and without an uploaded avatar image', () => {
    const comensalUser: AuthenticatedUser = {
      id: 'comensal-avatar-1',
      email: 'lucia@easyeat.com',
      role: 'COMENSAL',
      tenantId: null,
      tokenVersion: 1,
    };

    const { rerender } = render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <RoleLayoutShell
          title="Catálogo"
          user={comensalUser}
          avatarUrl={null}
          onLogout={vi.fn()}
        >
          <div>Contenido</div>
        </RoleLayoutShell>
      </MemoryRouter>
    );

    const linkWithoutAvatar = screen.getByRole('link', { name: /mi perfil/i });
    expect(linkWithoutAvatar).toHaveClass('h-11', 'w-11', 'overflow-hidden', 'rounded-full');
    expect(within(linkWithoutAvatar).queryByRole('img', { name: 'Avatar' })).not.toBeInTheDocument();

    rerender(
      <MemoryRouter initialEntries={['/catalogo']}>
        <RoleLayoutShell
          title="Catálogo"
          user={comensalUser}
          avatarUrl="/api/v1/profile/avatar/photo.jpg"
          onLogout={vi.fn()}
        >
          <div>Contenido</div>
        </RoleLayoutShell>
      </MemoryRouter>
    );

    const linkWithAvatar = screen.getByRole('link', { name: /mi perfil/i });
    expect(linkWithAvatar).toHaveClass('h-11', 'w-11', 'overflow-hidden', 'rounded-full');
    const avatarImg = within(linkWithAvatar).getByRole('img', { name: 'Avatar' });
    expect(avatarImg).toHaveClass('h-full', 'w-full', 'object-cover');
  });

  it('omits profile avatar indicator for staff roles, hides header tenant-badge for ADMINISTRADOR, and keeps it for CHEF/ENCARGADO', () => {
    const staffRoles: ReadonlyArray<{ role: 'ENCARGADO' | 'CHEF' | 'ADMINISTRADOR'; path: string }> = [
      { role: 'ENCARGADO', path: '/mostrador' },
      { role: 'CHEF', path: '/cocina' },
      { role: 'ADMINISTRADOR', path: '/admin' },
    ];

    for (const { role, path } of staffRoles) {
      const { unmount } = renderShell({
        title: `Vista ${role}`,
        initialEntry: path,
        user: {
          id: `${role}-1`,
          email: `${role.toLowerCase()}@easyeat.com`,
          role,
          tenantId: 'tenant-12345678-abcd',
          tokenVersion: 1,
        },
      });

      const header = screen.getByRole('banner');
      expect(within(header).queryByText(role)).not.toBeInTheDocument();
      expect(within(header).queryByText(`${role.toLowerCase()}@easyeat.com`)).not.toBeInTheDocument();
      expect(within(header).queryByTestId('staff-avatar-indicator')).not.toBeInTheDocument();

      const headerLinks = within(header).queryAllByRole('link');
      expect(headerLinks.some((link) => link.getAttribute('href')?.includes('/perfil'))).toBe(false);

      if (role === 'ADMINISTRADOR') {
        expect(within(header).queryByTestId('tenant-badge')).not.toBeInTheDocument();
        expect(within(header).getByRole('button', { name: /notificaciones/i })).toBeInTheDocument();
      } else {
        expect(within(header).getByTestId('tenant-badge')).toBeInTheDocument();
        expect(within(header).queryByRole('button', { name: /notificaciones/i })).not.toBeInTheDocument();
      }

      unmount();
    }
  });

  it('preserves Chef C-08 direct desktop navigation with >= 44px links and dynamic aria-current', () => {
    renderShell({
      title: 'Cocina',
      initialEntry: '/cocina/platos/nuevo',
      user: {
        id: 'chef-1',
        email: 'martin@easyeat.com',
        role: 'CHEF',
        tenantId: 'tenant-cocina-1',
        tokenVersion: 1,
      },
    });

    const chefNav = screen.getByRole('navigation', { name: /secciones de cocina/i });
    expect(chefNav.className).toContain('md:flex');

    const kdsLink = within(chefNav).getByRole('link', { name: 'Comandas KDS' });
    const dishesLink = within(chefNav).getByRole('link', { name: 'Gestionar Platos' });
    const ingredientsLink = within(chefNav).getByRole('link', { name: 'Ingredientes' });

    expect(kdsLink).toHaveAttribute('href', '/cocina');
    expect(dishesLink).toHaveAttribute('href', '/cocina/platos');
    expect(ingredientsLink).toHaveAttribute('href', '/cocina/ingredientes');

    expect(dishesLink).toHaveAttribute('aria-current', 'page');
    expect(kdsLink).not.toHaveAttribute('aria-current');

    for (const link of [kdsLink, dishesLink, ingredientsLink]) {
      expect(link.className).toMatch(/min-h-\[(44|48)px\]/);
    }
  });

  it('renders guest login and register links and hides role menu and logout when user is null', () => {
    renderShell({ title: 'Catálogo Público', user: null, initialEntry: '/catalogo' });

    const header = screen.getByRole('banner');
    expect(within(header).queryByRole('button', { name: /abrir menú de navegación/i })).not.toBeInTheDocument();
    expect(within(header).queryByRole('button', { name: /cerrar sesión/i })).not.toBeInTheDocument();

    expect(within(header).getByRole('link', { name: /iniciar sesión/i })).toHaveAttribute('href', '/login');
    expect(within(header).getByRole('link', { name: /registrarse/i })).toHaveAttribute('href', '/register');
  });

  it('renders clickable EasyEat mini brand mark linking to the default route for each role', () => {
    const cases: ReadonlyArray<{ role: AuthenticatedUser['role'] | null; expectedHref: string }> = [
      { role: null, expectedHref: '/descubrimiento' },
      { role: 'COMENSAL', expectedHref: '/descubrimiento' },
      { role: 'CHEF', expectedHref: '/cocina' },
      { role: 'ENCARGADO', expectedHref: '/mostrador' },
      { role: 'ADMINISTRADOR', expectedHref: '/admin' },
    ];

    for (const { role, expectedHref } of cases) {
      const { unmount } = renderShell({
        title: 'Vista',
        user: role
          ? {
              id: `u-${role}`,
              email: `${role.toLowerCase()}@easyeat.com`,
              role,
              tenantId: role === 'COMENSAL' ? null : 'tenant-1',
              tokenVersion: 1,
            }
          : null,
      });

      const header = screen.getByRole('banner');
      const brandLink = within(header).getByRole('link', { name: /easyeat/i });
      expect(brandLink).toHaveAttribute('href', expectedHref);
      expect(brandLink).toHaveClass('min-h-[44px]');
      expect(within(brandLink).getByTestId('easyeat-logo-mini')).toBeInTheDocument();

      unmount();
    }
  });

  it('applies transitory data-legacy-surface container for legacy-light and legacy-dark modes', () => {
    const { rerender } = render(
      <MemoryRouter>
        <RoleLayoutShell title="Mostrador" user={null} onLogout={vi.fn()} legacySurfaceMode="legacy-light">
          <div>Legacy light content</div>
        </RoleLayoutShell>
      </MemoryRouter>
    );

    expect(screen.getByTestId('legacy-surface-container')).toHaveAttribute('data-legacy-surface', 'light');

    rerender(
      <MemoryRouter>
        <RoleLayoutShell title="Métricas" user={null} onLogout={vi.fn()} legacySurfaceMode="legacy-dark">
          <div>Legacy dark content</div>
        </RoleLayoutShell>
      </MemoryRouter>
    );

    expect(screen.getByTestId('legacy-surface-container')).toHaveAttribute('data-legacy-surface', 'dark');

    rerender(
      <MemoryRouter>
        <RoleLayoutShell title="Normal" user={null} onLogout={vi.fn()} legacySurfaceMode="none">
          <div>Normal content</div>
        </RoleLayoutShell>
      </MemoryRouter>
    );

    expect(screen.queryByTestId('legacy-surface-container')).not.toBeInTheDocument();
  });
});
