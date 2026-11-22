import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { GlassCard } from '@/components/admin/GlassCard';
import { AdminSidebar } from '@/components/admin/AdminSidebar';
import { ROLE_NAVIGATION_CONFIG } from '@/components/layout/roleNavigationConfig';
import { RoleLayoutShell } from '@/components/layout/RoleLayoutShell';
import { RoleHamburgerMenu } from '@/components/layout/RoleHamburgerMenu';
import {
  AdminPendingDishesProvider,
  useAdminPendingDishes,
} from '@/context/AdminPendingDishesContext';
import { dishReviewApi } from '@/api/dishReviewApi';

vi.mock('@/api/dishReviewApi', () => ({
  dishReviewApi: {
    getInbox: vi.fn(),
  },
}));

const mockedGetInbox = vi.mocked(dishReviewApi.getInbox);

const PendingConsumer: React.FC = () => {
  const { pendingCount, setPendingCount, decrementPendingCount, refreshPendingCount } =
    useAdminPendingDishes();

  return (
    <div>
      <span data-testid="pending-count">
        {pendingCount === null ? 'null' : String(pendingCount)}
      </span>
      <button type="button" onClick={() => setPendingCount(5)}>
        set-5
      </button>
      <button type="button" onClick={() => decrementPendingCount()}>
        decrement
      </button>
      <button type="button" onClick={() => void refreshPendingCount()}>
        refresh
      </button>
    </div>
  );
};

describe('Phase 2 Foundational — GlassCard & AdminPendingDishesContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('GlassCard', () => {
    it('renders elevated, subtle, interactive, and accent variants with 1px border and backdrop blur', () => {
      const { rerender } = render(
        <GlassCard data-testid="glass-card" variant="elevated">
          Elevated
        </GlassCard>
      );
      const card = screen.getByTestId('glass-card');
      expect(card.className).toContain('border');
      expect(card.className).toContain('backdrop-blur-[12px]');

      rerender(
        <GlassCard data-testid="glass-card" variant="subtle">
          Subtle
        </GlassCard>
      );
      expect(screen.getByTestId('glass-card').className).toContain('bg-ee-surface-subtle');

      rerender(
        <GlassCard data-testid="glass-card" variant="interactive">
          Interactive
        </GlassCard>
      );
      const interactiveClass = screen.getByTestId('glass-card').className;
      expect(interactiveClass).toContain('duration-150');
      expect(interactiveClass).toContain('motion-reduce:transition-none');

      rerender(
        <GlassCard data-testid="glass-card" variant="accent">
          Accent
        </GlassCard>
      );
      expect(screen.getByTestId('glass-card').className).toContain('border-ee-primary');
    });

    it('supports --ee-* padding and radius tokens and solid fallback for reduced-transparency', () => {
      render(
        <GlassCard data-testid="glass-card" padding="lg" radius="sm" as="section">
          Content
        </GlassCard>
      );
      const card = screen.getByTestId('glass-card');
      expect(card.tagName).toBe('SECTION');
      expect(card.className).toContain('p-8');
      expect(card.className).toContain('rounded-ee-sm');
      expect(card.className).toContain('[@media(prefers-reduced-transparency:reduce)]:bg-ee-surface-elevated');
      expect(card.className).toContain('[@media(prefers-reduced-transparency:reduce)]:backdrop-blur-none');
    });
  });

  describe('AdminPendingDishesProvider', () => {
    it('fetches minimal inbox (size=1) once on mount without polling', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockedGetInbox.mockResolvedValueOnce({
        items: [],
        page: 0,
        size: 1,
        totalElements: 3,
        totalPages: 3,
        hasNext: true,
        pendingCount: 3,
      });

      render(
        <AdminPendingDishesProvider>
          <PendingConsumer />
        </AdminPendingDishesProvider>
      );

      expect(screen.getByTestId('pending-count')).toHaveTextContent('null');

      await waitFor(() => {
        expect(screen.getByTestId('pending-count')).toHaveTextContent('3');
      });

      expect(mockedGetInbox).toHaveBeenCalledTimes(1);
      expect(mockedGetInbox).toHaveBeenCalledWith({ tab: 'PENDING', page: 0, size: 1 });

      await act(async () => {
        vi.advanceTimersByTime(60_000);
      });
      expect(mockedGetInbox).toHaveBeenCalledTimes(1);
    });

    it('keeps pendingCount === null on fetch error (no false zero)', async () => {
      mockedGetInbox.mockRejectedValueOnce(new Error('Network error'));

      render(
        <AdminPendingDishesProvider>
          <PendingConsumer />
        </AdminPendingDishesProvider>
      );

      await waitFor(() => {
        expect(mockedGetInbox).toHaveBeenCalledTimes(1);
      });
      expect(screen.getByTestId('pending-count')).toHaveTextContent('null');
    });

    it('synchronizes setPendingCount and decrementPendingCount in memory without extra requests', async () => {
      const user = userEvent.setup();
      mockedGetInbox.mockResolvedValueOnce({
        items: [],
        page: 0,
        size: 1,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        pendingCount: 1,
      });

      render(
        <AdminPendingDishesProvider>
          <PendingConsumer />
        </AdminPendingDishesProvider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('pending-count')).toHaveTextContent('1');
      });

      await user.click(screen.getByText('set-5'));
      expect(screen.getByTestId('pending-count')).toHaveTextContent('5');

      await user.click(screen.getByText('decrement'));
      expect(screen.getByTestId('pending-count')).toHaveTextContent('4');

      expect(mockedGetInbox).toHaveBeenCalledTimes(1);
    });
  });
});

describe('Phase 3 US1 — Bimodal Executive Navigation & AdminSidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetInbox.mockResolvedValue({
      items: [],
      page: 0,
      size: 1,
      totalElements: 4,
      totalPages: 4,
      hasNext: true,
      pendingCount: 4,
    });
  });

  it('configures ADMINISTRADOR with 6 grouped sections, useDesktopSidebar: true, and no admin-metricas', () => {
    const adminConfig = ROLE_NAVIGATION_CONFIG.ADMINISTRADOR;
    expect(adminConfig.useDesktopSidebar).toBe(true);
    expect(adminConfig.items).toHaveLength(6);
    expect(adminConfig.items.some((item) => item.id === 'admin-metricas')).toBe(false);

    const groups = Array.from(new Set(adminConfig.items.map((item) => item.group)));
    expect(groups).toEqual(['Operación', 'Catálogo y equipo', 'Negocio']);

    expect(ROLE_NAVIGATION_CONFIG.COMENSAL.useDesktopSidebar).toBeFalsy();
    expect(ROLE_NAVIGATION_CONFIG.ENCARGADO.useDesktopSidebar).toBeFalsy();
    expect(ROLE_NAVIGATION_CONFIG.CHEF.useDesktopSidebar).toBeFalsy();
  });

  it('renders AdminSidebar with 3 groups, active state, conditional pendingCount badge, focus ring, and logout', async () => {
    const user = userEvent.setup();
    const onLogout = vi.fn();

    const { rerender } = render(
      <MemoryRouter initialEntries={['/admin/dishes']}>
        <AdminSidebar
          userEmail="admin@easyeat.com"
          tenantId="tenant-123"
          pendingDishesCount={3}
          onLogout={onLogout}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Operación')).toBeInTheDocument();
    expect(screen.getByText('Catálogo y equipo')).toBeInTheDocument();
    expect(screen.getByText('Negocio')).toBeInTheDocument();

    const auditLink = screen.getByRole('link', { name: /Auditoría/i });
    expect(auditLink).toHaveAttribute('aria-current', 'page');
    expect(auditLink).toHaveTextContent('3');
    expect(auditLink.className).toContain('focus-visible:ring');
    expect(auditLink.className).toContain('motion-reduce:transition-none');

    rerender(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminSidebar
          userEmail="admin@easyeat.com"
          tenantId="tenant-123"
          pendingDishesCount={0}
          onLogout={onLogout}
        />
      </MemoryRouter>
    );

    const auditLinkZero = screen.getByRole('link', { name: /Auditoría/i });
    expect(auditLinkZero).not.toHaveTextContent('0');

    await user.click(screen.getByRole('button', { name: /Cerrar sesión/i }));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('hides hamburger on lg: only for ADMINISTRADOR, hardens notification bell, and keeps account non-navigable', () => {
    const { rerender } = render(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminPendingDishesProvider>
          <RoleLayoutShell
            role="ADMINISTRADOR"
            userEmail="admin@easyeat.com"
            tenantId="tenant-123"
            onLogout={vi.fn()}
          >
            <div>Admin Content</div>
          </RoleLayoutShell>
        </AdminPendingDishesProvider>
      </MemoryRouter>
    );

    const hamburger = screen.getByRole('button', { name: /Abrir menú/i });
    expect(hamburger.className).toContain('lg:hidden');

    const bell = screen.getByRole('button', { name: /Notificaciones/i });
    expect(bell.className).toContain('focus-visible:ring');
    expect(bell).toHaveAttribute('aria-haspopup');

    expect(screen.queryByRole('link', { name: /admin@easyeat\.com/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /perfil/i })).not.toBeInTheDocument();

    rerender(
      <MemoryRouter initialEntries={['/cocina']}>
        <RoleLayoutShell
          role="CHEF"
          userEmail="chef@easyeat.com"
          tenantId="tenant-123"
          onLogout={vi.fn()}
        >
          <div>Chef Content</div>
        </RoleLayoutShell>
      </MemoryRouter>
    );

    const chefHamburger = screen.getByRole('button', { name: /Abrir menú/i });
    expect(chefHamburger.className).not.toContain('lg:hidden');
  });

  it('shows pendingCount badge next to Auditoría in RoleHamburgerMenu with >= 44px touch targets', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <RoleHamburgerMenu
          isOpen={true}
          onClose={vi.fn()}
          config={ROLE_NAVIGATION_CONFIG.ADMINISTRADOR}
          userEmail="admin@easyeat.com"
          tenantId="tenant-123"
          pendingDishesCount={7}
          onLogout={vi.fn()}
        />
      </MemoryRouter>
    );

    const auditItem = screen.getByRole('link', { name: /Auditoría/i });
    expect(auditItem).toHaveTextContent('7');
    expect(auditItem.className).toContain('min-h-[44px]');
  });
});
