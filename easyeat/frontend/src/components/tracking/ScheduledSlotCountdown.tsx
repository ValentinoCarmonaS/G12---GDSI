import React from 'react';
import { Calendar, Clock } from 'lucide-react';

export interface ScheduledSlotCountdownProps {
  isCountdownActive: boolean;
  timeSlotStart?: string | null;
  timeSlotEnd?: string | null;
  estimatedMinutesRemaining?: number | null;
  message?: string | null;
}

export const ScheduledSlotCountdown: React.FC<ScheduledSlotCountdownProps> = ({
  isCountdownActive,
  timeSlotStart,
  timeSlotEnd,
  estimatedMinutesRemaining,
  message,
}) => {
  const formatSlot = (time?: string | null) => {
    if (!time) return '--:--';
    return time.length >= 5 ? time.slice(0, 5) : time;
  };

  const startFormatted = formatSlot(timeSlotStart);
  const endFormatted = formatSlot(timeSlotEnd);

  if (!isCountdownActive) {
    return (
      <div className="bg-blue-50 border border-blue-200 rounded-2xl p-6 text-center shadow-sm">
        <div className="inline-flex p-3 bg-blue-100 rounded-full mb-3 text-blue-600">
          <Calendar className="w-8 h-8" />
        </div>
        <h3 className="text-lg font-semibold text-gray-900">Horario de retiro programado</h3>
        <p className="text-2xl font-bold text-blue-700 mt-2">
          {startFormatted} - {endFormatted} hs
        </p>
        <p className="text-xs text-gray-500 mt-3">
          El contador dinámico en vivo se activará 15 minutos antes de tu turno pactado.
        </p>
      </div>
    );
  }

  const isInSlot = estimatedMinutesRemaining != null && estimatedMinutesRemaining <= 0;

  return (
    <div
      data-testid="active-proximity-countdown"
      className="bg-amber-50 border border-amber-200 rounded-2xl p-6 text-center shadow-sm"
    >
      <div className="inline-flex p-3 bg-amber-100 rounded-full mb-3 text-amber-600">
        <Clock className="w-8 h-8 animate-pulse" />
      </div>
      <h3 className="text-xl font-bold text-gray-900">
        {isInSlot ? '¡Tu turno ha comenzado!' : '¡Tu turno se acerca!'}
      </h3>
      <p className="text-sm text-gray-600 mt-1">
        {message ||
          (isInSlot
            ? 'La cocina está dando los últimos toques a tu pedido.'
            : 'La cocina está comenzando la preparación de tu orden.')}
      </p>
      <div className="mt-4 inline-block bg-white border border-amber-300 rounded-xl px-6 py-3 shadow-sm">
        <span
          className={`${
            isInSlot ? 'text-2xl sm:text-3xl' : 'text-4xl'
          } font-extrabold text-amber-600 tracking-tight`}
        >
          {isInSlot ? 'Unos instantes' : `${estimatedMinutesRemaining ?? 15} min`}
        </span>
      </div>
      <p className="text-xs text-gray-500 mt-3">
        Franja de retiro acordada: {startFormatted} - {endFormatted} hs
      </p>
    </div>
  );
};
