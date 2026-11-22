import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from '@/components/primitives/StatusBadge';
import type { SemanticVariant } from '@/theme/types';

describe('StatusBadge primitive (FR-012, FR-016, FR-017)', () => {
  it('renders with default neutral semantic triad and visible textual label', () => {
    render(<StatusBadge>Pendiente</StatusBadge>);

    const badge = screen.getByText('Pendiente').closest('[data-testid="status-badge"]');
    expect(badge).toBeInTheDocument();
    expect(badge?.className).toContain('bg-ee-neutral-bg');
    expect(badge?.className).toContain('text-ee-neutral-fg');
    expect(badge?.className).toContain('border-ee-neutral-border');
    expect((badge as HTMLElement).style.borderWidth).toBe('var(--ee-border-width)');
  });

  it.each<[SemanticVariant, string, string, string]>([
    ['neutral', 'bg-ee-neutral-bg', 'text-ee-neutral-fg', 'border-ee-neutral-border'],
    ['info', 'bg-ee-info-bg', 'text-ee-info-fg', 'border-ee-info-border'],
    ['success', 'bg-ee-success-bg', 'text-ee-success-fg', 'border-ee-success-border'],
    ['warning', 'bg-ee-warning-bg', 'text-ee-warning-fg', 'border-ee-warning-border'],
    ['danger', 'bg-ee-danger-bg', 'text-ee-danger-fg', 'border-ee-danger-border'],
  ])(
    'applies --ee-* semantic triad classes for variant "%s"',
    (variant, bgClass, fgClass, borderClass) => {
      render(<StatusBadge variant={variant}>Estado {variant}</StatusBadge>);

      const badge = screen.getByText(`Estado ${variant}`).closest('[data-testid="status-badge"]');
      expect(badge).toBeInTheDocument();
      expect(badge?.className).toContain(bgClass);
      expect(badge?.className).toContain(fgClass);
      expect(badge?.className).toContain(borderClass);
    }
  );

  it('renders decorative icon with aria-hidden="true" and optional textual prefix for non-color redundancy', () => {
    render(
      <StatusBadge
        variant="warning"
        icon={<svg data-testid="badge-icon" />}
        prefix="Stock:"
      >
        Crítico
      </StatusBadge>
    );

    const icon = screen.getByTestId('badge-icon');
    expect(icon.parentElement).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('Stock:')).toBeInTheDocument();
    expect(screen.getByText('Crítico')).toBeInTheDocument();
  });

  it('forwards custom HTML attributes and className cleanly', () => {
    render(
      <StatusBadge variant="success" id="order-status" className="uppercase">
        Listo para retiro
      </StatusBadge>
    );

    const badge = document.getElementById('order-status');
    expect(badge).toBeInTheDocument();
    expect(badge?.className).toContain('uppercase');
    expect(badge).toHaveTextContent('Listo para retiro');
  });
});
