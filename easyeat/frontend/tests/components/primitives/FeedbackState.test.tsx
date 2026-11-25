import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FeedbackState, type FeedbackVariant } from '@/components/primitives/FeedbackState';

describe('FeedbackState primitive (FR-013, FR-016, FR-017)', () => {
  it('renders role="alert" and aria-live="assertive" for danger variant', () => {
    render(
      <FeedbackState
        variant="danger"
        title="Error al guardar"
        message="No se pudo conectar con el servidor."
      />
    );

    const alert = screen.getByRole('alert');
    expect(alert).toHaveAttribute('aria-live', 'assertive');
    expect(alert.className).toContain('bg-ee-danger-bg');
    expect(alert.className).toContain('text-ee-danger-fg');
    expect(alert.className).toContain('border-ee-danger-border');
    expect(alert.style.borderWidth).toBe('var(--ee-border-width)');
    expect(screen.getByText('Error al guardar')).toBeInTheDocument();
    expect(screen.getByText('No se pudo conectar con el servidor.')).toBeInTheDocument();
  });

  it.each<[Exclude<FeedbackVariant, 'danger'>, string, string, string]>([
    ['info', 'bg-ee-info-bg', 'text-ee-info-fg', 'border-ee-info-border'],
    ['success', 'bg-ee-success-bg', 'text-ee-success-fg', 'border-ee-success-border'],
    ['warning', 'bg-ee-warning-bg', 'text-ee-warning-fg', 'border-ee-warning-border'],
  ])(
    'renders role="status", aria-live="polite", and --ee-* triad for variant "%s"',
    (variant, bgClass, fgClass, borderClass) => {
      render(<FeedbackState variant={variant} message={`Mensaje ${variant}`} />);

      const status = screen.getByRole('status');
      expect(status).toHaveAttribute('aria-live', 'polite');
      expect(status.className).toContain(bgClass);
      expect(status.className).toContain(fgClass);
      expect(status.className).toContain(borderClass);
      expect(screen.getByText(`Mensaje ${variant}`)).toBeInTheDocument();
    }
  );

  it('supports decorative icon with aria-hidden="true"', () => {
    render(
      <FeedbackState
        variant="info"
        message="Actualización disponible"
        icon={<svg data-testid="feedback-icon" />}
      />
    );

    expect(screen.getByTestId('feedback-icon').parentElement).toHaveAttribute(
      'aria-hidden',
      'true'
    );
  });

  it('supports keyboard-operable retry action and dismiss control with >= 44px touch target', async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    const onDismiss = vi.fn();

    render(
      <FeedbackState
        variant="danger"
        title="Sin conexión"
        message="Reintentá en unos segundos."
        actionLabel="Reintentar"
        onAction={onAction}
        onDismiss={onDismiss}
        dismissLabel="Cerrar alerta"
      />
    );

    const retryBtn = screen.getByRole('button', { name: 'Reintentar' });
    const dismissBtn = screen.getByRole('button', { name: 'Cerrar alerta' });
    expect(dismissBtn.className).toContain('min-h-[44px]');
    expect(dismissBtn.className).toContain('min-w-[44px]');

    retryBtn.focus();
    await user.keyboard('{Enter}');
    expect(onAction).toHaveBeenCalledTimes(1);

    dismissBtn.focus();
    await user.keyboard('{Enter}');
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('uses default accessible label for dismiss button when dismissLabel is omitted', () => {
    render(
      <FeedbackState
        variant="success"
        message="Cambios guardados."
        onDismiss={vi.fn()}
      />
    );

    expect(screen.getByRole('button', { name: 'Cerrar aviso' })).toBeInTheDocument();
  });
});
