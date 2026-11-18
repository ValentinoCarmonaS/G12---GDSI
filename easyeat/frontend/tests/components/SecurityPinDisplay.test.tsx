import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SecurityPinDisplay } from '@/components/common/SecurityPinDisplay';

/**
 * Spec 022 T008: shared pickup PIN display (research D-12).
 * - RF-003/RF-004: monospace, wide tracking, digits shown as `d d d d`.
 * - Edge case 10 / invariant 4: a missing or malformed PIN renders the neutral `- - - -`,
 *   never a value that could not exist in `orders.security_code` (`^[1-9][0-9]{3}$`).
 */
describe('SecurityPinDisplay Component (Spec 022 T008)', () => {
  it('renders a valid PIN with its digits separated by spaces', () => {
    render(<SecurityPinDisplay pin="4821" />);

    expect(screen.getByTestId('security-pin-display')).toHaveTextContent(/^4 8 2 1$/);
  });

  it.each([
    ['1000', '1 0 0 0'],
    ['9999', '9 9 9 9'],
  ])('renders the range boundary %s as %s', (pin, expected) => {
    render(<SecurityPinDisplay pin={pin} />);

    expect(screen.getByTestId('security-pin-display').textContent).toBe(expected);
  });

  it.each([
    ['null', null],
    ['undefined', undefined],
  ])('renders the neutral placeholder when the PIN is %s (edge case 10)', (_label, pin) => {
    render(<SecurityPinDisplay pin={pin} />);

    expect(screen.getByTestId('security-pin-display').textContent).toBe('- - - -');
  });

  it.each(['', '   ', '12', '999', '0999', '10000', 'abcd', '12a4', ' 4821'])(
    'renders the neutral placeholder for the malformed PIN "%s"',
    (pin) => {
      render(<SecurityPinDisplay pin={pin} />);

      expect(screen.getByTestId('security-pin-display').textContent).toBe('- - - -');
    },
  );

  it('applies monospace typography with wide tracking to a valid PIN', () => {
    render(<SecurityPinDisplay pin="4821" />);

    const display = screen.getByTestId('security-pin-display');
    expect(display).toHaveClass('font-mono', 'font-bold', 'tracking-[0.25em]');
  });

  it('applies the same typography to the neutral placeholder', () => {
    render(<SecurityPinDisplay pin={null} />);

    const display = screen.getByTestId('security-pin-display');
    expect(display).toHaveClass('font-mono', 'font-bold', 'tracking-[0.25em]');
  });

  it('merges a custom className so each screen can size and color the PIN', () => {
    render(<SecurityPinDisplay pin="4821" className="text-3xl text-white" />);

    const display = screen.getByTestId('security-pin-display');
    expect(display).toHaveClass('font-mono', 'text-3xl', 'text-white');
  });
});
