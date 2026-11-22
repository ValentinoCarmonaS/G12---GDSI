import React from 'react';
import { Calendar } from 'lucide-react';

export interface OperatingDayItem {
  date: string; // "YYYY-MM-DD"
  dayName: string; // e.g. "Lun", "Mar"
  dayNumber: number; // e.g. 2
  isOperating: boolean;
}

interface OperatingDatePickerProps {
  operatingDays: OperatingDayItem[];
  selectedDate: string;
  onSelectDate: (date: string) => void;
}

export const OperatingDatePicker: React.FC<OperatingDatePickerProps> = ({
  operatingDays,
  selectedDate,
  onSelectDate,
}) => {
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm font-semibold text-ee-text">
        <Calendar className="w-4 h-4 text-ee-primary-border" aria-hidden="true" />
        <span>Seleccioná el día de retiro</span>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-2">
        {operatingDays.map((item) => {
          const isSelected = item.date === selectedDate;
          const isDisabled = !item.isOperating;

          return (
            <button
              key={item.date}
              type="button"
              disabled={isDisabled}
              onClick={() => onSelectDate(item.date)}
              aria-selected={isSelected}
              className={`flex-1 min-w-[70px] min-h-[44px] py-3 px-2 rounded-ee-md border flex flex-col items-center justify-center transition-colors duration-150 motion-reduce:transition-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring ${
                isDisabled
                  ? 'border-ee-border-subtle bg-ee-surface-subtle opacity-50 cursor-not-allowed text-ee-text-secondary'
                  : isSelected
                  ? 'border-ee-primary-border bg-ee-primary text-ee-on-primary font-semibold shadow-ee-elevated'
                  : 'border-ee-border-subtle bg-ee-surface-elevated hover:border-ee-border-strong hover:bg-ee-surface-subtle text-ee-text'
              }`}
            >
              <span className="font-ee-mono text-xs uppercase tracking-wider">{item.dayName}</span>
              <span className="font-ee-mono tabular-nums text-lg font-bold mt-0.5">{item.dayNumber}</span>
              {isDisabled && (
                <span className="text-[11px] text-ee-text-secondary mt-0.5">Cerrado</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
};
