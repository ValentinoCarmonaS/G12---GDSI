import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoadingSkeleton } from '@/components/primitives/LoadingSkeleton';

describe('LoadingSkeleton primitive (FR-014, FR-016, FR-017)', () => {
  it('renders role="status" container with sr-only default label and hidden reduced-motion text shape', () => {
    render(<LoadingSkeleton />);

    const status = screen.getByRole('status');
    expect(status).toHaveAttribute('aria-busy', 'true');
    const srText = screen.getByText('Cargando contenido...');
    expect(srText.className).toContain('sr-only');

    const shapes = status.querySelectorAll('[data-testid="skeleton-shape"]');
    expect(shapes).toHaveLength(1);
    expect(shapes[0]).toHaveAttribute('aria-hidden', 'true');
    expect(shapes[0].className).toContain('bg-ee-surface-subtle');
    expect(shapes[0].className).toContain('rounded-ee-sm');
    expect(shapes[0].className).toContain('motion-reduce:animate-none');
  });

  it('renders multiple decorative text lines when variant="text" and lines > 1', () => {
    render(<LoadingSkeleton variant="text" lines={3} label="Cargando menú..." />);

    const status = screen.getByRole('status');
    expect(screen.getByText('Cargando menú...').className).toContain('sr-only');

    const shapes = status.querySelectorAll('[data-testid="skeleton-shape"]');
    expect(shapes).toHaveLength(3);
    shapes.forEach((shape) => {
      expect(shape).toHaveAttribute('aria-hidden', 'true');
      expect(shape.className).toContain('motion-reduce:animate-none');
    });
  });

  it('renders block and circle variants with theme tokens and custom dimensions', () => {
    const { rerender } = render(
      <LoadingSkeleton variant="block" width="240px" height="120px" />
    );

    let shape = screen.getByRole('status').querySelector('[data-testid="skeleton-shape"]') as HTMLElement;
    expect(shape).toHaveAttribute('aria-hidden', 'true');
    expect(shape.className).toContain('rounded-ee-md');
    expect(shape.className).toContain('bg-ee-surface-subtle');
    expect(shape.style.width).toBe('240px');
    expect(shape.style.height).toBe('120px');

    rerender(<LoadingSkeleton variant="circle" width="48px" height="48px" />);
    shape = screen.getByRole('status').querySelector('[data-testid="skeleton-shape"]') as HTMLElement;
    expect(shape).toHaveAttribute('aria-hidden', 'true');
    expect(shape.className).toContain('rounded-full');
    expect(shape.style.width).toBe('48px');
    expect(shape.style.height).toBe('48px');
  });

  it('omits status role and sr-only announcement when announce={false}', () => {
    const { container } = render(<LoadingSkeleton variant="block" announce={false} />);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.queryByText('Cargando contenido...')).not.toBeInTheDocument();
    const shape = container.querySelector('[data-testid="skeleton-shape"]');
    expect(shape).toBeInTheDocument();
    expect(shape).toHaveAttribute('aria-hidden', 'true');
  });
});
