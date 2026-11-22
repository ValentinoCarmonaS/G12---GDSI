import React from 'react';
import { Calendar, Clock } from 'lucide-react';
import { BentoCard } from '../diner';
import { StatusBadge } from '../ui';

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
      <BentoCard variant="elevated" padding="md" radius="lg" className="text-center space-y-3">
        <div className="flex justify-center">
          <StatusBadge variant="info" className="gap-1.5">
            <Calendar className="w-4 h-4" aria-hidden="true" />
            Reserva programada
          </StatusBadge>
        </div>
        <h3 className="text-lg font-bold text-ee-text">Horario de retiro programado</h3>
        <p className="text-2xl sm:text-3xl font-ee-mono tabular-nums font-bold text-ee-info-fg">
          {startFormatted} - {endFormatted} hs
        </p>
        <p className="text-xs text-ee-text-secondary max-w-md mx-auto">
          El contador dinámico en vivo se activará 15 minutos antes de tu turno pactado.
        </p>
      </BentoCard>
    );
  }

  const isInSlot = estimatedMinutesRemaining != null && estimatedMinutesRemaining <= 0;

  return (
    <BentoCard
      variant="elevated"
      padding="md"
      radius="lg"
      data-testid="active-proximity-countdown"
      className="text-center space-y-4 border-ee-warning-border"
    >
      <div className="flex justify-center">
        <StatusBadge variant="warning" className="gap-1.5">
          <Clock className="w-4 h-4" aria-hidden="true" />
          Cuenta regresiva activa
        </StatusBadge>
      </div>
      <div className="space-y-1">
        <h3 className="text-xl font-bold text-ee-text">
          {isInSlot ? '¡Tu turno ha comenzado!' : '¡Tu turno se acerca!'}
        </h3>
        <p className="text-sm text-ee-text-secondary">
          {message ||
            (isInSlot
              ? 'La cocina está dando los últimos toques a tu pedido.'
              : 'La cocina está comenzando la preparación de tu orden.')}
        </p>
      </div>
      <div className="inline-block bg-ee-warning-bg border border-ee-warning-border rounded-ee-md px-6 py-3">
        <span
          className={`${
            isInSlot ? 'text-2xl sm:text-3xl' : 'text-4xl'
          } font-ee-mono tabular-nums font-bold text-ee-warning-fg tracking-tight`}
        >
          {isInSlot ? 'Unos instantes' : `${estimatedMinutesRemaining ?? 15} min`}
        </span>
      </div>
      <p className="text-xs text-ee-text-secondary">
        Franja de retiro acordada:{' '}
        <span className="font-ee-mono tabular-nums font-medium text-ee-text">
          {startFormatted} - {endFormatted} hs
        </span>
      </p>
    </BentoCard>
  );
};
