import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EmptyState } from '@/components/primitives/EmptyState';

describe('EmptyState primitive (FR-015, FR-016, FR-017)', () => {
  it('renders title, description, and theme-governed container without action when omitted', () => {
    render(
      <EmptyState
        title="Sin resultados"
        description="No hay elementos que coincidan con tu búsqueda."
      />
    );

    const container = screen.getByTestId('empty-state');
    expect(container.className).toContain('rounded-ee-lg');
    expect(container.className).toContain('border-ee-border-subtle');
    expect(container.className).toContain('shadow-ee-elevated');
    expect(container.style.borderWidth).toBe('var(--ee-border-width)');

    expect(screen.getByRole('heading', { name: 'Sin resultados' })).toBeInTheDocument();
    expect(
      screen.getByText('No hay elementos que coincidan con tu búsqueda.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('renders optional decorative icon with aria-hidden="true"', () => {
    render(
      <EmptyState
        icon={<svg data-testid="empty-icon" />}
        title="Bandeja vacía"
        description="Todavía no recibiste pedidos."
      />
    );

    expect(screen.getByTestId('empty-icon').parentElement).toHaveAttribute('aria-hidden', 'true');
  });

  it('renders keyboard-operable action via Button primitive with configurable actionVariant', async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();

    const { rerender } = render(
      <EmptyState
        title="Sin platos cargados"
        description="Agregá el primer plato para empezar."
        actionLabel="Crear plato"
        onAction={onAction}
      />
    );

    const actionBtn = screen.getByRole('button', { name: 'Crear plato' });
    expect(actionBtn.className).toContain('bg-ee-primary');
    expect(actionBtn.className).toContain('min-h-[44px]');

    await user.click(actionBtn);
    actionBtn.focus();
    await user.keyboard('{Enter}');
    expect(onAction).toHaveBeenCalledTimes(2);

    rerender(
      <EmptyState
        title="Sin platos cargados"
        description="Agregá el primer plato para empezar."
        actionLabel="Explorar"
        onAction={onAction}
        actionVariant="secondary"
      />
    );
    expect(screen.getByRole('button', { name: 'Explorar' }).className).toContain('bg-ee-secondary');
  });
});
