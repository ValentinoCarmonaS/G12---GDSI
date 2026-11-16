import React from 'react';
import { WifiOff } from 'lucide-react';

export interface CounterConnectionBannerProps {
  isDisconnected: boolean;
}

/**
 * Subtle, non-blocking header connection status badge for Counter Dispatch (Spec 020 - US2, RF-018).
 * Displays "Sin conexión — Reintentando..." when network synchronization has failed for >15 seconds,
 * without hiding visible orders or collapsing expanded cards.
 */
export const CounterConnectionBanner: React.FC<
  CounterConnectionBannerProps
> = ({ isDisconnected }) => {
  if (!isDisconnected) {
    return null;
  }

  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="counter-connection-banner"
      className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200 shadow-sm"
    >
      <WifiOff className="w-3.5 h-3.5 text-amber-600 shrink-0" />
      <span>Sin conexión — Reintentando...</span>
    </div>
  );
};

export default CounterConnectionBanner;
