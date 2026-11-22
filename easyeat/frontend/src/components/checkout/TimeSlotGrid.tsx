import React from 'react';
import { Clock } from 'lucide-react';
import { TimeSlotAvailability } from '../../types/order';
import { LoadingSkeleton } from '../primitives/LoadingSkeleton';
import { StatusBadge } from '../primitives/StatusBadge';

interface TimeSlotGridProps {
  slots: TimeSlotAvailability[];
  selectedSlot: TimeSlotAvailability | null;
  onSelectSlot: (slot: TimeSlotAvailability) => void;
  isLoading?: boolean;
}

export const TimeSlotGrid: React.FC<TimeSlotGridProps> = ({
  slots,
  selectedSlot,
  onSelectSlot,
  isLoading = false,
}) => {
  if (isLoading) {
    return (
      <div className="py-4 space-y-3">
        <p className="text-sm font-medium text-ee-text-secondary">
          Cargando franjas horarias disponibles...
        </p>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
          <LoadingSkeleton variant="block" height="52px" announce={false} />
          <LoadingSkeleton variant="block" height="52px" announce={false} />
          <LoadingSkeleton variant="block" height="52px" announce={false} />
          <LoadingSkeleton variant="block" height="52px" announce={false} />
        </div>
      </div>
    );
  }

  // C-12: Filter out any non-selectable or sold-out slots to guarantee clean UX without grayed-out slots
  const activeSlots = (slots || []).filter(
    (slot) => slot.isSelectable && slot.status !== 'SOLD_OUT' && slot.status !== 'EXPIRED_OR_LEAD_TIME'
  );

  if (activeSlots.length === 0) {
    return (
      <div
        className="py-6 px-4 rounded-ee-md bg-ee-surface-subtle border border-ee-border-subtle text-center text-ee-text-secondary text-sm"
        data-testid="empty-slots-message"
      >
        No hay horarios de retiro disponibles para el día seleccionado.
      </div>
    );
  }

  const formatTime = (timeStr: string) => {
    if (!timeStr) return '';
    return timeStr.slice(0, 5); // "HH:mm"
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-semibold text-ee-text">
          <Clock className="w-4 h-4 text-ee-primary-border" aria-hidden="true" />
          <span>Seleccioná el horario de retiro</span>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
        {activeSlots.map((slot) => {
          const isSelected =
            selectedSlot?.startTime === slot.startTime &&
            selectedSlot?.date === slot.date;
          const isHighDemand = slot.status === 'HIGH_DEMAND';

          let buttonClasses =
            'relative min-h-[44px] p-3 rounded-ee-md border text-sm font-medium transition-colors duration-150 motion-reduce:transition-none flex flex-col items-center justify-center focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring ';

          if (isSelected) {
            buttonClasses += 'border-ee-primary-border bg-ee-primary text-ee-on-primary shadow-ee-elevated';
          } else if (isHighDemand) {
            buttonClasses +=
              'border-ee-warning-border bg-ee-warning-bg text-ee-warning-fg hover:border-ee-border-strong';
          } else {
            buttonClasses +=
              'border-ee-border-subtle bg-ee-surface-elevated text-ee-text hover:border-ee-border-strong hover:bg-ee-surface-subtle';
          }

          return (
            <button
              key={`${slot.date}-${slot.startTime}`}
              type="button"
              onClick={() => onSelectSlot(slot)}
              aria-selected={isSelected}
              className={buttonClasses}
            >
              <span className="font-ee-mono tabular-nums font-semibold">
                {formatTime(slot.startTime)} - {formatTime(slot.endTime)}
              </span>

              {isHighDemand && (
                <StatusBadge variant="warning" className="mt-1 px-1.5 py-0.5 text-[10px]">
                  Alta Demanda
                </StatusBadge>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
};
