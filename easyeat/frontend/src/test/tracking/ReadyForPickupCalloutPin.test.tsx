import { render, screen, within } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ReadyForPickupCallout } from '../../components/tracking/ReadyForPickupCallout';

/**
 * Spec 022 T012 (US1, ENC-3.2.1): the diner's ready callout renders the PIN through the shared
 * <SecurityPinDisplay /> (RF-003, AC-1, research D-12), so it reads exactly like the counter card.
 * Without a PIN the code box is not rendered at all (decision of the 2026-11-17 grill-me session).
 */
describe('ReadyForPickupCallout PIN display (Spec 022 T012)', () => {
  it('renders the PIN through SecurityPinDisplay as spaced monospace digits', () => {
    render(<ReadyForPickupCallout ticketNumber={42} securityCode="4821" />);

    const callout = screen.getByTestId('ready-for-pickup-callout');
    const pin = within(callout).getByTestId('security-pin-display');
    expect(pin.textContent).toBe('4 8 2 1');
    expect(pin).toHaveClass('font-mono', 'tracking-[0.25em]');
  });

  it('no longer prints the PIN as a bare unspaced string', () => {
    render(<ReadyForPickupCallout ticketNumber={42} securityCode="4821" />);

    expect(screen.queryByText('4821')).not.toBeInTheDocument();
  });

  it('keeps the code box label and the dictation hint next to the PIN', () => {
    render(<ReadyForPickupCallout ticketNumber={42} securityCode="4821" />);

    expect(screen.getByText('Código de retiro')).toBeInTheDocument();
    expect(screen.getByText(/Dictale este código de 4 dígitos/)).toBeInTheDocument();
  });

  it.each([
    ['null', null],
    ['undefined', undefined],
  ])('renders no code box when the PIN is %s', (_label, securityCode) => {
    render(<ReadyForPickupCallout ticketNumber={42} securityCode={securityCode} />);

    expect(screen.getByTestId('ready-for-pickup-callout')).toBeInTheDocument();
    expect(screen.queryByTestId('security-pin-display')).not.toBeInTheDocument();
    expect(screen.queryByText('Código de retiro')).not.toBeInTheDocument();
  });
});
