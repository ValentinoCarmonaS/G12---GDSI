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
      <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
        <Calendar className="w-4 h-4 text-emerald-600" />
        <span>Seleccioná el día de retiro</span>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-thin">
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
              className={`flex-1 min-w-[70px] py-3 px-2 rounded-xl border flex flex-col items-center justify-center transition-all ${
                isDisabled
                  ? 'border-gray-200 bg-gray-50 opacity-40 cursor-not-allowed text-gray-400'
                  : isSelected
                  ? 'border-emerald-600 bg-emerald-50 text-emerald-900 font-semibold shadow-sm ring-2 ring-emerald-500/20'
                  : 'border-gray-200 bg-white hover:border-gray-300 text-gray-700'
              }`}
            >
              <span className="text-xs uppercase tracking-wider">{item.dayName}</span>
              <span className="text-lg font-bold mt-0.5">{item.dayNumber}</span>
              {isDisabled && (
                <span className="text-[10px] text-gray-400 mt-0.5">Cerrado</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
};
