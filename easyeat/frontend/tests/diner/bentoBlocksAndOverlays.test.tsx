import React, { useState } from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, beforeEach } from 'vitest';
import {
  BentoCard,
  AuthCardShell,
  AccessibleModal,
  AccessibleDrawer,
} from '../../src/components/diner';
import { DEFAULT_NEUTRAL_BRANDING } from '../../src/hooks/useRestaurantBranding';
import { ConnectionStatusBadge } from '../../src/components/common/ConnectionStatusBadge';
import { getContrastRatio } from '../../src/utils/colorContrastUtils';

describe('Spec 025 Phase 2 — Bento blocks, accessible overlays, neutral branding & ConnectionStatusBadge', () => {
  beforeEach(() => {
    document.body.style.overflow = '';
  });

  describe('BentoCard', () => {
    it('renders elevated variant with default md padding, md radius and --ee-* tokens', () => {
      render(
        <BentoCard data-testid="bento-default">
          <span>Contenido Bento</span>
        </BentoCard>
      );

      const card = screen.getByTestId('bento-default');
      expect(card.tagName).toBe('DIV');
      expect(card.className).toContain('bg-ee-surface-elevated');
      expect(card.className).toContain('border-ee-border-subtle');
      expect(card.className).toContain('shadow-ee-elevated');
      expect(card.className).toContain('rounded-ee-md');
      expect(card.className).toContain('p-6');
    });

    it('supports subtle, interactive and hero variants with custom tag, padding and radius', () => {
      const { rerender } = render(
        <BentoCard as="section" variant="subtle" padding="sm" radius="lg" data-testid="bento-var">
          <span>Subtle</span>
        </BentoCard>
      );

      let card = screen.getByTestId('bento-var');
      expect(card.tagName).toBe('SECTION');
      expect(card.className).toContain('bg-ee-surface-subtle');
      expect(card.className).toContain('p-4');
      expect(card.className).toContain('rounded-ee-lg');

      rerender(
        <BentoCard as="article" variant="interactive" padding="lg" data-testid="bento-var">
          <span>Interactive</span>
        </BentoCard>
      );
      card = screen.getByTestId('bento-var');
      expect(card.tagName).toBe('ARTICLE');
      expect(card.className).toContain('bg-ee-surface-elevated');
      expect(card.className).toContain('p-8');
      expect(card.className).toContain('transition');

      rerender(
        <BentoCard as="aside" variant="hero" padding="none" data-testid="bento-var">
          <span>Hero</span>
        </BentoCard>
      );
      card = screen.getByTestId('bento-var');
      expect(card.tagName).toBe('ASIDE');
      expect(card.className).toContain('border-ee-border-strong');
      expect(card.className).toContain('p-0');
    });
  });

  describe('AuthCardShell', () => {
    it('renders centered polymorphic card driven 100% by --ee-* tokens without hardcoded Bento colors', () => {
      render(
        <AuthCardShell
          title="Iniciar sesión"
          subtitle="Ingresá con tu cuenta de EasyEat"
          footer={<a href="/register">Crear cuenta</a>}
          maxWidth="md"
        >
          <form>
            <input aria-label="Email" />
          </form>
        </AuthCardShell>
      );

      expect(screen.getByText('EasyEat')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
      expect(screen.getByText('Ingresá con tu cuenta de EasyEat')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Crear cuenta' })).toBeInTheDocument();

      const shell = screen.getByTestId('auth-card-shell');
      expect(shell.className).toContain('bg-ee-surface');
      expect(shell.className).not.toContain('bg-gray-50');
      expect(shell.className).not.toContain('bg-white');
      expect(shell.className).not.toContain('#FFF5E6');
    });
  });

  describe('AccessibleModal', () => {
    const ModalHarness: React.FC<{ dismissible?: boolean }> = ({ dismissible = true }) => {
      const [open, setOpen] = useState(false);
      return (
        <div>
          <button type="button" onClick={() => setOpen(true)}>
            Abrir modal
          </button>
          <AccessibleModal
            isOpen={open}
            onClose={() => setOpen(false)}
            title="Detalle de prueba"
            subtitle="Subtítulo"
            dismissible={dismissible}
            testId="test-modal"
            backdropTestId="test-modal-backdrop"
            closeButtonTestId="test-modal-close"
          >
            <p>Cuerpo del modal</p>
          </AccessibleModal>
        </div>
      );
    };

    it('enforces role=dialog, aria-modal=true, >=44px close target, body scroll lock, Escape/backdrop close and focus return', async () => {
      render(<ModalHarness />);

      const trigger = screen.getByRole('button', { name: 'Abrir modal' });
      trigger.focus();
      fireEvent.click(trigger);

      const dialog = screen.getByRole('dialog');
      expect(dialog).toHaveAttribute('aria-modal', 'true');
      expect(dialog).toHaveAttribute('aria-labelledby');
      expect(document.body.style.overflow).toBe('hidden');

      const closeBtn = screen.getByTestId('test-modal-close');
      expect(closeBtn.className).toContain('min-h-[44px]');
      expect(closeBtn.className).toContain('min-w-[44px]');

      // Close with Escape
      fireEvent.keyDown(document, { key: 'Escape' });

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(document.body.style.overflow).toBe('');
      expect(document.activeElement).toBe(trigger);

      // Reopen and close via backdrop click
      fireEvent.click(trigger);
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      fireEvent.click(screen.getByTestId('test-modal-backdrop'));

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(document.body.style.overflow).toBe('');
    });

    it('does not close on Escape or backdrop when dismissible is false', () => {
      render(<ModalHarness dismissible={false} />);

      fireEvent.click(screen.getByRole('button', { name: 'Abrir modal' }));
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      fireEvent.keyDown(document, { key: 'Escape' });
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      fireEvent.click(screen.getByTestId('test-modal-backdrop'));
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
  });

  describe('AccessibleDrawer', () => {
    const DrawerHarness: React.FC<{ side?: 'left' | 'right' }> = ({ side = 'right' }) => {
      const [open, setOpen] = useState(false);
      return (
        <div>
          <button type="button" onClick={() => setOpen(true)}>
            Abrir panel
          </button>
          <AccessibleDrawer
            isOpen={open}
            onClose={() => setOpen(false)}
            side={side}
            title="Filtros dietéticos"
            testId="test-drawer"
            backdropTestId="test-drawer-backdrop"
            closeButtonTestId="test-drawer-close"
          >
            <p>Contenido del drawer</p>
          </AccessibleDrawer>
        </div>
      );
    };

    it('renders dialog with aria-modal, >=44px close button, Escape close, scroll lock and focus return', async () => {
      render(<DrawerHarness side="left" />);

      const trigger = screen.getByRole('button', { name: 'Abrir panel' });
      trigger.focus();
      fireEvent.click(trigger);

      const dialog = screen.getByRole('dialog');
      expect(dialog).toHaveAttribute('aria-modal', 'true');
      expect(dialog).toHaveAttribute('data-side', 'left');
      expect(document.body.style.overflow).toBe('hidden');

      const closeBtn = screen.getByTestId('test-drawer-close');
      expect(closeBtn.className).toContain('min-h-[44px]');
      expect(closeBtn.className).toContain('min-w-[44px]');

      fireEvent.keyDown(document, { key: 'Escape' });

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(document.body.style.overflow).toBe('');
      expect(document.activeElement).toBe(trigger);
    });
  });

  describe('DEFAULT_NEUTRAL_BRANDING Bento WCAG 2.2 AA', () => {
    it('uses Bento-aligned neutral branding colors with >= 4.5:1 WCAG 2.2 AA contrast', () => {
      expect(DEFAULT_NEUTRAL_BRANDING.primaryColor).toBe('#FAD4C0');
      expect(DEFAULT_NEUTRAL_BRANDING.primaryContrastColor).toBe('#111827');
      expect(DEFAULT_NEUTRAL_BRANDING.secondaryColor).toBe('#80A1C1');
      expect(DEFAULT_NEUTRAL_BRANDING.secondaryContrastColor).toBe('#111827');
      expect(DEFAULT_NEUTRAL_BRANDING.accentColor).toBe('#9A4A24');
      expect(DEFAULT_NEUTRAL_BRANDING.accentContrastColor).toBe('#FFFFFF');

      expect(
        getContrastRatio(
          DEFAULT_NEUTRAL_BRANDING.primaryColor,
          DEFAULT_NEUTRAL_BRANDING.primaryContrastColor
        )
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(
          DEFAULT_NEUTRAL_BRANDING.secondaryColor,
          DEFAULT_NEUTRAL_BRANDING.secondaryContrastColor
        )
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(
          DEFAULT_NEUTRAL_BRANDING.accentColor,
          DEFAULT_NEUTRAL_BRANDING.accentContrastColor
        )
      ).toBeGreaterThanOrEqual(4.5);
    });
  });

  describe('ConnectionStatusBadge', () => {
    it('renders with polymorphic --ee-warning-* tokens and without continuous animate-pulse', () => {
      render(<ConnectionStatusBadge isDisconnected={true} />);

      const badge = screen.getByTestId('connection-status-badge');
      expect(badge).toHaveAttribute('role', 'status');
      expect(badge).toHaveAttribute('aria-live', 'polite');
      expect(badge.className).toContain('bg-ee-warning-bg');
      expect(badge.className).toContain('text-ee-warning-fg');
      expect(badge.className).toContain('border-ee-warning-border');
      expect(badge.className).not.toContain('animate-pulse');
      expect(badge.className).not.toContain('bg-amber-100');
    });

    it('renders nothing when connected', () => {
      const { container } = render(<ConnectionStatusBadge isDisconnected={false} />);
      expect(container).toBeEmptyDOMElement();
    });
  });
});
