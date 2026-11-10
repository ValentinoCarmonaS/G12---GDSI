import React from 'react';
import { Clock } from 'lucide-react';
import { TimeSlotAvailability } from '../../types/order';

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
      <div className="py-8 text-center text-gray-500 text-sm animate-pulse">
        Cargando franjas horarias disponibles...
      </div>
    );
  }

  // C-12: Filter out any non-selectable or sold-out slots to guarantee clean UX without grayed-out slots
  const activeSlots = (slots || []).filter(
    (slot) => slot.isSelectable && slot.status !== 'SOLD_OUT' && slot.status !== 'EXPIRED_OR_LEAD_TIME'
  );

  if (activeSlots.length === 0) {
    return (
      <div className="py-8 text-center text-gray-500 text-sm" data-testid="empty-slots-message">
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
        <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
          <Clock className="w-4 h-4 text-emerald-600" />
          <span>Seleccioná el horario de retiro</span>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
        {activeSlots.map((slot) => {
          const isSelected =
            selectedSlot?.startTime === slot.startTime &&
            selectedSlot?.date === slot.date;
          const isHighDemand = slot.status === 'HIGH_DEMAND';

          let buttonClasses = 'relative p-3 rounded-xl border text-sm font-medium transition-all flex flex-col items-center justify-center ';

          if (isSelected) {
            buttonClasses += 'border-emerald-600 bg-emerald-50 text-emerald-900 shadow-sm ring-2 ring-emerald-500/20';
          } else if (isHighDemand) {
            buttonClasses += 'border-amber-300 bg-amber-50/50 text-amber-900 hover:border-amber-400 hover:bg-amber-50';
          } else {
            buttonClasses += 'border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:bg-gray-50';
          }

          return (
            <button
              key={`${slot.date}-${slot.startTime}`}
              type="button"
              onClick={() => onSelectSlot(slot)}
              aria-selected={isSelected}
              className={buttonClasses}
            >
              <span className="font-semibold">
                {formatTime(slot.startTime)} - {formatTime(slot.endTime)}
              </span>

              {isHighDemand && (
                <span className="mt-1 px-1.5 py-0.5 text-[10px] font-semibold bg-amber-100 text-amber-800 rounded-md">
                  Alta Demanda
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
};
