import React from 'react';
import { Clock } from 'lucide-react';
import { StatusBadge } from '@/components/primitives/StatusBadge';

export interface OperatingHoursBannerProps {
  isOpen?: boolean;
  nextOpeningSchedule?: string | null;
}

export const OperatingHoursBanner: React.FC<OperatingHoursBannerProps> = ({
  isOpen = true,
  nextOpeningSchedule,
}) => {
  if (isOpen) {
    return null;
  }

  return (
    <div
      data-testid="operating-hours-banner"
      role="status"
      className="bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] p-4 rounded-[var(--ee-radius-md)] flex flex-col sm:flex-row sm:items-center justify-between gap-3"
    >
      <div className="flex items-start sm:items-center gap-3">
        <div className="p-2 bg-[var(--ee-surface-elevated)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] rounded-[var(--ee-radius-sm)] shrink-0 mt-0.5 sm:mt-0">
          <Clock className="w-5 h-5" aria-hidden="true" />
        </div>
        <div>
          <h4 className="font-bold text-sm text-[var(--ee-warning-fg)]">
            Establecimiento fuera de horario de atención
          </h4>
          <p className="text-xs text-[var(--ee-warning-fg)] leading-relaxed mt-0.5">
            {nextOpeningSchedule
              ? `${nextOpeningSchedule}. `
              : 'El local se encuentra cerrado momentáneamente. '}
            Podés explorar la carta en color y consultar recetas, pero los pedidos están temporalmente inhabilitados.
          </p>
        </div>
      </div>
      <div className="self-end sm:self-center shrink-0">
        <StatusBadge variant="warning">Modo Lectura</StatusBadge>
      </div>
    </div>
  );
};

export default OperatingHoursBanner;
