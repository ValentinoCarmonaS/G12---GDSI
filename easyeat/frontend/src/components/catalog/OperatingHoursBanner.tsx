import React from 'react';

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
      className="bg-amber-50 border border-amber-300 text-amber-950 p-4 rounded-2xl flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-xs"
    >
      <div className="flex items-start sm:items-center gap-3">
        <div className="p-2 bg-amber-100 text-amber-800 rounded-xl shrink-0 mt-0.5 sm:mt-0">
          <svg
            className="w-5 h-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>
        <div>
          <h4 className="font-bold text-sm text-amber-900">
            Establecimiento fuera de horario de atención
          </h4>
          <p className="text-xs text-amber-800/90 leading-relaxed mt-0.5">
            {nextOpeningSchedule ? `${nextOpeningSchedule}. ` : 'El local se encuentra cerrado momentáneamente. '}
            Podés explorar la carta en color y consultar recetas, pero los pedidos están temporalmente inhabilitados.
          </p>
        </div>
      </div>
      <div className="self-end sm:self-center shrink-0">
        <span className="inline-flex items-center px-3 py-1 bg-amber-200/70 text-amber-900 text-xs font-bold rounded-lg uppercase tracking-wider">
          Modo Lectura
        </span>
      </div>
    </div>
  );
};

export default OperatingHoursBanner;
