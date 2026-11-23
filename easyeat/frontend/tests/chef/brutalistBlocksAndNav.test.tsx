import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { BrutalistCard } from '../../src/components/chef/BrutalistCard';
import { BrutalistModal } from '../../src/components/chef/BrutalistModal';
import { DishStatusBadge } from '../../src/components/dish/DishStatusBadge';
import { DishRejectionFeedbackBanner } from '../../src/components/chef/DishRejectionFeedbackBanner';
import { ChefMobileBottomBar } from '../../src/components/chef/ChefMobileBottomBar';
import { RoleLayoutShell } from '../../src/components/layout/RoleLayoutShell';
import { ROLE_NAVIGATION_CONFIG } from '../../src/components/layout/roleNavigationConfig';
import type { AuthenticatedUser } from '../../src/types/auth';
import type { ChefReviewFeedbackEntry } from '../../src/types/dishReview';

describe('Phase 2 — Fundaciones Brutalism y componentes transversales', () => {
  describe('BrutalistCard', () => {
    it('renderiza las variantes default, elevated, subtle, alert-warning y alert-danger con sus clases Brutalism', () => {
      const { rerender } = render(
        <BrutalistCard testId="card" variant="default">
          Contenido default
        </BrutalistCard>
      );
      let card = screen.getByTestId('card');
      expect(card.className).toContain('border-2');
      expect(card.className).toContain('border-ee-border-strong');
      expect(card.className).toContain('bg-ee-surface');

      rerender(
        <BrutalistCard testId="card" variant="elevated">
          Contenido elevated
        </BrutalistCard>
      );
      card = screen.getByTestId('card');
      expect(card.className).toContain('shadow-ee-elevated');
      expect(card.className).toContain('bg-ee-surface-elevated');

      rerender(
        <BrutalistCard testId="card" variant="subtle">
          Contenido subtle
        </BrutalistCard>
      );
      card = screen.getByTestId('card');
      expect(card.className).toContain('bg-ee-surface-subtle');

      rerender(
        <BrutalistCard testId="card" variant="alert-warning">
          Contenido warning
        </BrutalistCard>
      );
      card = screen.getByTestId('card');
      expect(card.className).toContain('border-4');
      expect(card.className).toContain('border-ee-warning-border');
      expect(card.className).toContain('bg-ee-warning-bg');

      rerender(
        <BrutalistCard testId="card" variant="alert-danger">
          Contenido danger
        </BrutalistCard>
      );
      card = screen.getByTestId('card');
      expect(card.className).toContain('border-2');
      expect(card.className).toContain('border-ee-danger-border');
      expect(card.className).toContain('bg-ee-danger-bg');
    });

    it('aplica respuesta mecánica táctil en modo interactive y respeta motion-reduce', () => {
      render(
        <BrutalistCard testId="interactive-card" interactive>
          Tarjeta interactiva
        </BrutalistCard>
      );
      const card = screen.getByTestId('interactive-card');
      expect(card.className).toContain('motion-reduce:transition-none');
      expect(card.className).toContain('motion-reduce:transform-none');
    });
  });

  describe('BrutalistModal', () => {
    it('compone useAccessibleOverlay con role="dialog", aria-modal="true", cierre por Escape y backdrop, y retorno de foco', () => {
      const onClose = vi.fn();
      const trigger = document.createElement('button');
      trigger.textContent = 'Abrir';
      document.body.appendChild(trigger);
      trigger.focus();

      const { rerender } = render(
        <BrutalistModal
          isOpen={true}
          onClose={onClose}
          title="Confirmar eliminación"
          testId="brutalist-modal"
          footer={
            <button type="button" className="min-h-[56px]">
              Confirmar
            </button>
          }
        >
          <p>¿Deseás eliminar este borrador?</p>
        </BrutalistModal>
      );

      const dialog = screen.getByRole('dialog');
      expect(dialog).toHaveAttribute('aria-modal', 'true');
      expect(screen.getByText('Confirmar eliminación')).toBeInTheDocument();

      const closeBtn = screen.getByRole('button', { name: /cerrar/i });
      expect(closeBtn.className).toContain('min-h-[48px]');
      expect(closeBtn.className).toContain('min-w-[48px]');

      fireEvent.keyDown(document, { key: 'Escape' });
      expect(onClose).toHaveBeenCalledTimes(1);

      const backdrop = screen.getByTestId('brutalist-modal-backdrop');
      fireEvent.click(backdrop);
      expect(onClose).toHaveBeenCalledTimes(2);

      rerender(
        <BrutalistModal isOpen={false} onClose={onClose} title="Confirmar eliminación">
          <p>¿Deseás eliminar este borrador?</p>
        </BrutalistModal>
      );
      expect(document.activeElement).toBe(trigger);
      trigger.remove();
    });
  });

  describe('DishStatusBadge', () => {
    it('delega en StatusBadge con etiquetas en español y tokens --ee-*', () => {
      render(<DishStatusBadge status="PUBLISHED" />);
      const badge = screen.getByTestId('dish-status-badge');
      expect(badge).toHaveTextContent('Publicado');
      expect(badge.className).toContain('bg-ee-');
      expect(badge.className).toContain('text-ee-');
      expect(badge.className).toContain('border-ee-');
    });
  });

  describe('DishRejectionFeedbackBanner', () => {
    it('renderiza en estética Brutalism con propuestas precargadas y cero campos financieros', () => {
      const entry: ChefReviewFeedbackEntry = {
        cycleNumber: 2,
        reviewerName: 'Lucía Ferraro',
        comment: 'Ajustar guarnición y reenviar.',
        createdAt: '2026-10-30T10:00:00Z',
        proposedChanges: {
          title: 'Milanesa de merluza',
          allergenCodes: ['FISH'],
        },
      };

      render(<DishRejectionFeedbackBanner entries={[entry]} />);
      const banner = screen.getByTestId('rejection-feedback-banner');
      expect(banner).toBeInTheDocument();
      expect(banner.innerHTML).not.toMatch(/\$|ARS|USD|costo|margen|precio/i);
      expect(screen.getByTestId('proposed-changes')).toBeInTheDocument();
      expect(screen.getByText(/Milanesa de merluza/)).toBeInTheDocument();
    });
  });
});

const chefUser: AuthenticatedUser = {
  id: 'chef-1',
  email: 'chef@easyeat.com',
  role: 'CHEF',
  tenantId: 'tenant-1',
  tokenVersion: 1,
};

describe('Phase 3 — US1: Arquitectura visual Brutalism y navegación táctil de un toque', () => {
  describe('ROLE_NAVIGATION_CONFIG', () => {
    it('habilita useMobileBottomBar exclusivamente para CHEF y mantiene sus 3 destinos', () => {
      expect(ROLE_NAVIGATION_CONFIG.CHEF.useMobileBottomBar).toBe(true);
      expect(ROLE_NAVIGATION_CONFIG.CHEF.items.map((i) => i.label)).toEqual([
        'Comandas KDS',
        'Gestionar Platos',
        'Ingredientes',
      ]);
      expect(ROLE_NAVIGATION_CONFIG.COMENSAL.useMobileBottomBar).toBeFalsy();
      expect(ROLE_NAVIGATION_CONFIG.ENCARGADO.useMobileBottomBar).toBeFalsy();
      expect(ROLE_NAVIGATION_CONFIG.ADMINISTRADOR.useMobileBottomBar).toBeFalsy();
    });
  });

  describe('ChefMobileBottomBar', () => {
    it('renderiza los 3 accesos con min-h-[56px], aria-current="page" y estilo Brutalism activo', () => {
      render(
        <MemoryRouter initialEntries={['/cocina/platos']}>
          <ChefMobileBottomBar />
        </MemoryRouter>
      );

      const nav = screen.getByRole('navigation', { name: 'Navegación rápida móvil de cocina' });
      expect(nav.className).toContain('md:hidden');

      const kdsLink = screen.getByRole('link', { name: 'Comandas KDS' });
      const platosLink = screen.getByRole('link', { name: 'Platos' });
      const ingLink = screen.getByRole('link', { name: 'Ingredientes' });

      expect(kdsLink.className).toContain('min-h-[56px]');
      expect(platosLink.className).toContain('min-h-[56px]');
      expect(ingLink.className).toContain('min-h-[56px]');

      expect(platosLink).toHaveAttribute('aria-current', 'page');
      expect(kdsLink).not.toHaveAttribute('aria-current', 'page');
    });
  });

  describe('RoleLayoutShell para CHEF', () => {
    it('aplica min-h-[48px] en cabecera, reserva pb-24 md:pb-6 en main y elimina max-w-7xl con fullWidth', () => {
      const { rerender } = render(
        <MemoryRouter initialEntries={['/cocina']}>
          <RoleLayoutShell title="Cocina" user={chefUser} onLogout={vi.fn()}>
            <div>Contenido</div>
          </RoleLayoutShell>
        </MemoryRouter>
      );

      const desktopNav = screen.getByRole('navigation', { name: 'Secciones de cocina' });
      const desktopLinks = desktopNav.querySelectorAll('a');
      expect(desktopLinks.length).toBe(3);
      desktopLinks.forEach((link) => {
        expect(link.className).toContain('min-h-[48px]');
      });

      const main = screen.getByRole('main');
      expect(main.className).toContain('pb-24');
      expect(main.className).toContain('md:pb-6');
      expect(main.className).toContain('max-w-7xl');

      expect(
        screen.getByRole('navigation', { name: 'Navegación rápida móvil de cocina' })
      ).toBeInTheDocument();

      rerender(
        <MemoryRouter initialEntries={['/cocina']}>
          <RoleLayoutShell title="Cocina" user={chefUser} fullWidth onLogout={vi.fn()}>
            <div>Contenido</div>
          </RoleLayoutShell>
        </MemoryRouter>
      );
      expect(screen.getByRole('main').className).not.toContain('max-w-7xl');
    });

    it('conserva metadatos de cuenta y Cerrar sesión en el menú hamburguesa sin enlace a /perfil', () => {
      render(
        <MemoryRouter initialEntries={['/cocina']}>
          <RoleLayoutShell title="Cocina" user={chefUser} onLogout={vi.fn()}>
            <div>Contenido</div>
          </RoleLayoutShell>
        </MemoryRouter>
      );

      const menuBtn = screen.getByRole('button', { name: /menú/i });
      fireEvent.click(menuBtn);

      expect(screen.getByText('chef@easyeat.com')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /cerrar sesión/i })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: /mi perfil/i })).not.toBeInTheDocument();
    });
  });
});
