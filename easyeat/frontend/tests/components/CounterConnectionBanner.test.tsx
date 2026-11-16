import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CounterConnectionBanner } from '../../src/components/counter/CounterConnectionBanner';

describe('CounterConnectionBanner (T018, US2, CHF-4.2.3, RF-018, Caso borde 1)', () => {
  it('renders nothing when isDisconnected is false', () => {
    const { container } = render(
      <CounterConnectionBanner isDisconnected={false} />
    );

    expect(container).toBeEmptyDOMElement();
    expect(
      screen.queryByText('Sin conexión — Reintentando...')
    ).not.toBeInTheDocument();
  });

  it('renders subtle non-blocking badge "Sin conexión — Reintentando..." when isDisconnected is true', () => {
    render(<CounterConnectionBanner isDisconnected={true} />);

    const banner = screen.getByRole('status');
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveAttribute('aria-live', 'polite');
    expect(banner).toHaveTextContent('Sin conexión — Reintentando...');
  });
});
