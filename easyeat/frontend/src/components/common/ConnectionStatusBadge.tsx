import React from 'react';
import { WifiOff } from 'lucide-react';

export interface ConnectionStatusBadgeProps {
  isDisconnected: boolean;
  className?: string;
}

export const ConnectionStatusBadge: React.FC<ConnectionStatusBadgeProps> = ({
  isDisconnected,
  className = '',
}) => {
  if (!isDisconnected) {
    return null;
  }

  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="connection-status-badge"
      className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-800 border border-amber-300 dark:bg-amber-950/70 dark:text-amber-300 dark:border-amber-800 animate-pulse transition-opacity duration-200 ${className}`}
    >
      <WifiOff className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
      <span>Sin conexión — Reintentando...</span>
    </div>
  );
};

export default ConnectionStatusBadge;
