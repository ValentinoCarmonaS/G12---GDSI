import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ConnectionStatusBadge } from '@/components/common/ConnectionStatusBadge';

describe('ConnectionStatusBadge Component (T008)', () => {
  it('renders nothing when isDisconnected is false', () => {
    const { container } = render(<ConnectionStatusBadge isDisconnected={false} />);
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('connection-status-badge')).not.toBeInTheDocument();
  });

  it('renders subtle non-blocking badge with expected text when isDisconnected is true', () => {
    render(<ConnectionStatusBadge isDisconnected={true} />);

    const badge = screen.getByTestId('connection-status-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('Sin conexión — Reintentando...');
    expect(badge).toHaveAttribute('role', 'status');
    expect(badge).toHaveAttribute('aria-live', 'polite');
  });

  it('applies custom className when provided', () => {
    render(<ConnectionStatusBadge isDisconnected={true} className="custom-class" />);

    const badge = screen.getByTestId('connection-status-badge');
    expect(badge.className).toContain('custom-class');
  });
});
