import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DeliveredOrderCallout } from '../../components/tracking/DeliveredOrderCallout';

/**
 * Spec 022 T022 (US2, RF-014, RF-015, edge case 12, contract 03 §4): the diner's terminal view after
 * delivery shows the final message and the order number, and never the PIN. The full purchase summary is the
 * page's existing block (decision of the 2026-11-18 grill-me session), so the callout does not repeat items.
 */
describe('DeliveredOrderCallout (Spec 022 T022)', () => {
  const FINAL_MESSAGE = 'Pedido Entregado — ¡Que disfrutes tu comida!';

  it('shows the final message received from the server', () => {
    render(<DeliveredOrderCallout ticketNumber={42} message={FINAL_MESSAGE} />);

    expect(screen.getByTestId('delivered-order-callout')).toBeInTheDocument();
    expect(screen.getByText(FINAL_MESSAGE)).toBeInTheDocument();
  });

  it('falls back to the contract 03 final message when none is provided', () => {
    render(<DeliveredOrderCallout ticketNumber={42} />);

    expect(screen.getByText(FINAL_MESSAGE)).toBeInTheDocument();
  });

  it('shows the order number', () => {
    render(<DeliveredOrderCallout ticketNumber={42} message={FINAL_MESSAGE} />);

    expect(screen.getByText('#42')).toBeInTheDocument();
  });

  it('omits the order number when it is unknown', () => {
    render(<DeliveredOrderCallout ticketNumber={null} message={FINAL_MESSAGE} />);

    expect(screen.queryByText(/#\d+/)).not.toBeInTheDocument();
  });

  it('never shows a PIN or the pickup code box (RF-015, edge case 12)', () => {
    render(<DeliveredOrderCallout ticketNumber={42} message={FINAL_MESSAGE} />);

    expect(screen.queryByTestId('security-pin-display')).not.toBeInTheDocument();
    expect(screen.queryByText('Código de retiro')).not.toBeInTheDocument();
    expect(screen.queryByText(/\d\s\d\s\d\s\d/)).not.toBeInTheDocument();
  });
});
