import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { RoleHamburgerMenu } from '@/components/layout/RoleHamburgerMenu';
import type { EasyEatUserRole } from '@/theme/types';

interface RenderMenuOptions {
  role: EasyEatUserRole;
  email?: string;
  initialEntry?: string;
  onLogout?: () => void | Promise<void>;
}

function renderMenu({
  role,
  email = 'usuario@easyeat.com',
  initialEntry = '/',
  onLogout = vi.fn(),
}: RenderMenuOptions) {
  return {
    onLogout,
    ...render(
      <MemoryRouter initialEntries={[initialEntry]}>
        <div>
          <div data-testid="outside-area">Área exterior</div>
          <RoleHamburgerMenu role={role} userEmail={email} onLogout={onLogout} />
        </div>
      </MemoryRouter>
    ),
  };
}

describe('RoleHamburgerMenu (FR-008, FR-010, SC-003, SC-004)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders an accessible trigger button >= 44x44px with aria-expanded and aria-controls', () => {
    renderMenu({ role: 'COMENSAL', initialEntry: '/catalogo' });

    const trigger = screen.getByRole('button', { name: /abrir menú de navegación/i });
    expect(trigger).toBeInTheDocument();
    expect(trigger).toHaveAttribute('type', 'button');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveAttribute('aria-controls', 'ee-role-menu-panel');
    expect(trigger.className).toMatch(/min-h-\[44px\]/);
    expect(trigger.className).toMatch(/min-w-\[44px\]/);
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('toggles open and closed on click and updates aria-expanded and aria-label', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'COMENSAL', initialEntry: '/catalogo' });

    const trigger = screen.getByRole('button', { name: /abrir menú de navegación/i });
    await user.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(trigger).toHaveAccessibleName(/cerrar menú de navegación/i);
    expect(screen.getByRole('navigation')).toHaveAttribute('id', 'ee-role-menu-panel');

    await user.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveAccessibleName(/abrir menú de navegación/i);
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('closes when Escape is pressed and restores focus deterministically to trigger button', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ADMINISTRADOR', initialEntry: '/admin' });

    const trigger = screen.getByRole('button', { name: /abrir menú de navegación/i });
    await user.click(trigger);

    const firstLink = screen.getByRole('link', { name: 'Panel' });
    firstLink.focus();
    expect(firstLink).toHaveFocus();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveFocus();
  });

  it('closes when clicking outside the menu container', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ENCARGADO', initialEntry: '/mostrador' });

    const trigger = screen.getByRole('button', { name: /abrir menú de navegación/i });
    await user.click(trigger);
    expect(screen.getByRole('navigation')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByTestId('outside-area'));

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('closes when selecting a navigation link', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ADMINISTRADOR', initialEntry: '/admin' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));
    await user.click(screen.getByRole('link', { name: 'Inventario' }));

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('shows readable Spanish role label, user email, and logout action inside the panel', async () => {
    const user = userEvent.setup();
    const { onLogout } = renderMenu({
      role: 'ENCARGADO',
      email: 'maria.mostrador@easyeat.com',
      initialEntry: '/mostrador',
    });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    expect(screen.getByText('Encargado del Mostrador')).toBeInTheDocument();
    expect(screen.getByText('maria.mostrador@easyeat.com')).toBeInTheDocument();
    expect(screen.queryByText('ENCARGADO')).not.toBeInTheDocument();

    const logoutButton = screen.getByRole('button', { name: /cerrar sesión/i });
    await user.click(logoutButton);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('exposes the 4 Comensal sections including Mi perfil and marks hash routes accurately', async () => {
    const user = userEvent.setup();
    renderMenu({
      role: 'COMENSAL',
      email: 'lucia@easyeat.com',
      initialEntry: '/perfil#pedidos',
    });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(4);
    expect(screen.getByRole('link', { name: 'Descubrimiento' })).toHaveAttribute('href', '/descubrimiento');
    expect(screen.getByRole('link', { name: 'Catálogo' })).toHaveAttribute('href', '/catalogo');
    expect(screen.getByRole('link', { name: 'Mis pedidos' })).toHaveAttribute('href', '/perfil#pedidos');
    expect(screen.getByRole('link', { name: 'Mi perfil' })).toHaveAttribute('href', '/perfil');

    expect(screen.getByRole('link', { name: 'Mis pedidos' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Mi perfil' })).not.toHaveAttribute('aria-current');
  });

  it('marks Mi perfil active on /perfil without #pedidos hash', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'COMENSAL', initialEntry: '/perfil' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    expect(screen.getByRole('link', { name: 'Mi perfil' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Mis pedidos' })).not.toHaveAttribute('aria-current');
  });

  it('exposes 1 section for ENCARGADO and zero links to /perfil', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ENCARGADO', initialEntry: '/mostrador' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Despacho en mostrador' })).toHaveAttribute('href', '/mostrador');
    expect(screen.getByRole('link', { name: 'Despacho en mostrador' })).toHaveAttribute('aria-current', 'page');
    expect(links.some((link) => link.getAttribute('href')?.includes('/perfil'))).toBe(false);
  });

  it('exposes 3 sections for CHEF with exact KDS match, prefix dish match, and zero links to /perfil', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'CHEF', initialEntry: '/cocina/platos/nuevo' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(3);
    expect(screen.getByRole('link', { name: 'Comandas KDS' })).toHaveAttribute('href', '/cocina');
    expect(screen.getByRole('link', { name: 'Gestionar Platos' })).toHaveAttribute('href', '/cocina/platos');
    expect(screen.getByRole('link', { name: 'Ingredientes' })).toHaveAttribute('href', '/cocina/ingredientes');

    expect(screen.getByRole('link', { name: 'Gestionar Platos' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Comandas KDS' })).not.toHaveAttribute('aria-current');
    expect(links.some((link) => link.getAttribute('href')?.includes('/perfil'))).toBe(false);
  });

  it('exposes all 7 sections for ADMINISTRADOR with dynamic active state and zero links to /perfil', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ADMINISTRADOR', initialEntry: '/admin' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(6);

    const expected: ReadonlyArray<[string, string]> = [
      ['Panel', '/admin'],
      ['Auditoría', '/admin/dishes'],
      ['Personal', '/admin/staff'],
      ['Inventario', '/admin/inventory'],
      ['Operaciones', '/admin/operations'],
      ['Mi local', '/admin/mi-local'],
    ];

    for (const [label, href] of expected) {
      expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', href);
    }

    expect(screen.getByRole('link', { name: 'Panel' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Personal' })).not.toHaveAttribute('aria-current');
    expect(links.some((link) => link.getAttribute('href')?.includes('/perfil'))).toBe(false);
  });

  it('groups ADMINISTRADOR navigation items into 3 semantic groups of <= 3 items to reduce cognitive load (T046 P1-3)', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ADMINISTRADOR', initialEntry: '/admin' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));

    expect(screen.getByText('Operación')).toBeInTheDocument();
    expect(screen.getByText('Catálogo y equipo')).toBeInTheDocument();
    expect(screen.getByText('Negocio')).toBeInTheDocument();
  });

  it('closes the dropdown when keyboard focus leaves the menu container (T046 P1-2)', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <div>
          <RoleHamburgerMenu role="COMENSAL" userEmail="lucia@easyeat.com" onLogout={vi.fn()} />
          <button type="button">Botón externo</button>
        </div>
      </MemoryRouter>
    );

    const trigger = screen.getByRole('button', { name: /abrir menú de navegación/i });
    await user.click(trigger);
    const nav = screen.getByRole('navigation');
    expect(nav).toBeInTheDocument();

    const outsideButton = screen.getByRole('button', { name: 'Botón externo' });
    fireEvent.blur(nav, { relatedTarget: outsideButton });
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('applies bounded viewport height with vertical scroll so items never clip at 200% zoom or landscape mobile (T046 P1-2)', async () => {
    const user = userEvent.setup();
    renderMenu({ role: 'ADMINISTRADOR', initialEntry: '/admin' });

    await user.click(screen.getByRole('button', { name: /abrir menú de navegación/i }));
    const nav = screen.getByRole('navigation');
    expect(nav.className).toContain('overflow-y-auto');
    expect(nav.className).toMatch(/max-h-/);
  });
});
