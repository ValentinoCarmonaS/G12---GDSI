import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrutalistCard } from '../../src/components/chef/BrutalistCard';
import { BrutalistModal } from '../../src/components/chef/BrutalistModal';
import { DishStatusBadge } from '../../src/components/dish/DishStatusBadge';
import { DishRejectionFeedbackBanner } from '../../src/components/chef/DishRejectionFeedbackBanner';
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
