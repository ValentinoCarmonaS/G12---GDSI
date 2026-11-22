import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button, type ButtonVariant } from '@/components/primitives/Button';

describe('Button primitive (FR-011, FR-016, FR-017)', () => {
  it('renders with default type="button", primary variant, and md accessible target (>= 44px)', () => {
    render(<Button>Confirmar pedido</Button>);

    const btn = screen.getByRole('button', { name: 'Confirmar pedido' });
    expect(btn).toHaveAttribute('type', 'button');
    expect(btn.className).toContain('bg-ee-primary');
    expect(btn.className).toContain('text-ee-on-primary');
    expect(btn.className).toContain('min-h-[44px]');
    expect(btn.className).toContain('min-w-[44px]');
    expect(btn.style.borderWidth).toBe('var(--ee-border-width)');
  });

  it.each<[ButtonVariant, string, string]>([
    ['primary', 'bg-ee-primary', 'text-ee-on-primary'],
    ['secondary', 'bg-ee-secondary', 'text-ee-on-secondary'],
    ['ghost', 'bg-transparent', 'text-ee-text'],
    ['outline', 'bg-ee-surface-elevated', 'text-ee-text'],
    ['danger', 'bg-ee-danger-solid', 'text-ee-on-danger'],
  ])('applies --ee-* semantic classes for variant "%s"', (variant, bgToken, fgToken) => {
    render(<Button variant={variant}>Acción {variant}</Button>);

    const btn = screen.getByRole('button', { name: `Acción ${variant}` });
    expect(btn.className).toContain(bgToken);
    expect(btn.className).toContain(fgToken);
  });

  it('enforces WCAG 2.2 AA touch target classes across sm (>= 24px), md (>= 44px), and lg (>= 44px)', () => {
    const { rerender } = render(<Button size="sm">Compacto</Button>);
    const smBtn = screen.getByRole('button', { name: 'Compacto' });
    expect(smBtn.className).toContain('min-h-[32px]');
    expect(smBtn.className).toContain('min-w-[32px]');

    rerender(<Button size="md">Estándar</Button>);
    const mdBtn = screen.getByRole('button', { name: 'Estándar' });
    expect(mdBtn.className).toContain('min-h-[44px]');
    expect(mdBtn.className).toContain('min-w-[44px]');

    rerender(<Button size="lg">Grande</Button>);
    const lgBtn = screen.getByRole('button', { name: 'Grande' });
    expect(lgBtn.className).toContain('min-h-[44px]');
    expect(lgBtn.className).toContain('min-w-[44px]');
  });

  it('supports fullWidth, explicit type="submit", leftIcon, rightIcon, and forwardRef', () => {
    const ref = React.createRef<HTMLButtonElement>();
    render(
      <Button
        ref={ref}
        type="submit"
        fullWidth
        leftIcon={<span data-testid="left-ico">L</span>}
        rightIcon={<span data-testid="right-ico">R</span>}
      >
        Enviar
      </Button>
    );

    const btn = screen.getByRole('button', { name: 'Enviar' });
    expect(ref.current).toBe(btn);
    expect(btn).toHaveAttribute('type', 'submit');
    expect(btn.className).toContain('w-full');
    expect(screen.getByTestId('left-ico').parentElement).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByTestId('right-ico').parentElement).toHaveAttribute('aria-hidden', 'true');
  });

  it('invokes onClick via pointer and keyboard when enabled, and blocks clicks when disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();

    const { rerender } = render(<Button onClick={onClick}>Guardar</Button>);
    const btn = screen.getByRole('button', { name: 'Guardar' });

    await user.click(btn);
    btn.focus();
    await user.keyboard('{Enter}');
    expect(onClick).toHaveBeenCalledTimes(2);

    rerender(
      <Button onClick={onClick} disabled>
        Guardar
      </Button>
    );
    expect(btn).toBeDisabled();
    await user.click(btn);
    expect(onClick).toHaveBeenCalledTimes(2);
  });

  it('sets aria-busy="true", disables double clicks, renders reduced-motion spinner, and supports loadingText', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();

    const { rerender } = render(
      <Button loading loadingText="Guardando..." onClick={onClick}>
        Guardar
      </Button>
    );

    const btn = screen.getByRole('button', { name: 'Guardando...' });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute('aria-busy', 'true');

    const spinner = btn.querySelector('[data-testid="button-spinner"]');
    expect(spinner).toBeInTheDocument();
    expect(spinner).toHaveAttribute('aria-hidden', 'true');
    expect(spinner?.getAttribute('class') ?? '').toContain('motion-reduce:animate-none');

    await user.click(btn);
    await user.click(btn);
    expect(onClick).not.toHaveBeenCalled();

    rerender(
      <Button loading onClick={onClick}>
        Guardar
      </Button>
    );
    expect(screen.getByRole('button', { name: 'Guardar' })).toHaveAttribute('aria-busy', 'true');
  });
});
