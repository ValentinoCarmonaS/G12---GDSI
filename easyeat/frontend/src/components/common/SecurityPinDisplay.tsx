import React from 'react';

/** Same rule as the backend's `chk_orders_security_code`: four digits in [1000, 9999]. */
const SECURITY_PIN_FORMAT = /^[1-9][0-9]{3}$/;

const NEUTRAL_PLACEHOLDER = '- - - -';

export interface SecurityPinDisplayProps {
  pin: string | null | undefined;
  className?: string;
}

/**
 * Shared pickup PIN display for the counter card and the diner's tracking screen (spec 022, research D-12).
 * A missing or malformed PIN renders the neutral placeholder, never a code that could not exist in the
 * order (edge case 10, invariant 4).
 */
export const SecurityPinDisplay: React.FC<SecurityPinDisplayProps> = ({ pin, className = '' }) => {
  const text = pin != null && SECURITY_PIN_FORMAT.test(pin) ? pin.split('').join(' ') : NEUTRAL_PLACEHOLDER;

  return (
    <span data-testid="security-pin-display" className={`font-mono font-bold tracking-[0.25em] ${className}`.trim()}>
      {text}
    </span>
  );
};
