import React from 'react';
import { CheckCircle2, Clock, Store, Ticket } from 'lucide-react';
import type { FulfillmentMode } from '../../types/orderTracking';

export interface OrderSuccessGreenBannerProps {
  ticketNumber: number | null;
  pickupDate: string;
  timeSlotStart: string;
  timeSlotEnd: string;
  establishmentName: string;
  fulfillmentMode: FulfillmentMode;
}

export const OrderSuccessGreenBanner: React.FC<OrderSuccessGreenBannerProps> = ({
  ticketNumber,
  pickupDate,
  timeSlotStart,
  timeSlotEnd,
  establishmentName,
  fulfillmentMode,
}) => {
  const formattedStart = timeSlotStart ? timeSlotStart.slice(0, 5) : '';
  const formattedEnd = timeSlotEnd ? timeSlotEnd.slice(0, 5) : '';

  return (
    <section
      data-testid="order-success-green-banner"
      data-brand-slot="confirmation"
      style={{
        backgroundColor: 'var(--brand-primary)',
        color: 'var(--brand-primary-contrast)',
      }}
      className="rounded-ee-lg border border-ee-border-strong p-6 sm:p-8 shadow-ee-elevated"
    >
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
        {/* Left column: Verification Icon + Confirmation Header */}
        <div className="flex items-start sm:items-center gap-4">
          <div
            className="flex-shrink-0 w-14 h-14 sm:w-16 sm:h-16 rounded-ee-md flex items-center justify-center border border-current/25"
            aria-hidden="true"
          >
            <CheckCircle2 className="w-8 h-8 sm:w-9 sm:h-9" />
          </div>
          <div className="space-y-1.5 min-w-0">
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full border border-current/30 text-xs font-ee-mono font-semibold uppercase tracking-wider">
              Pago Confirmado
            </span>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight leading-tight">
              ¡Tu pedido está en marcha!
            </h1>
            <div className="flex items-center gap-2 text-sm opacity-90">
              <Store className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span className="font-medium truncate">{establishmentName}</span>
            </div>
          </div>
        </div>

        {/* Right column: Prominent Ticket Badge & Pickup Slot Detail */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 w-full md:w-auto">
          {ticketNumber != null ? (
            <div className="text-center px-5 py-3 bg-ee-surface-elevated text-ee-text rounded-ee-md border border-ee-border-subtle shadow-ee-elevated min-w-[132px]">
              <span className="text-xs font-ee-mono font-semibold tracking-wider uppercase text-ee-text-secondary flex items-center justify-center gap-1">
                <Ticket className="w-3.5 h-3.5" aria-hidden="true" />
                Ticket
              </span>
              <span className="mt-0.5 block text-3xl sm:text-4xl font-ee-mono tabular-nums font-bold tracking-tight text-ee-text">
                #{ticketNumber}
              </span>
            </div>
          ) : (
            <div className="text-center px-5 py-3 bg-ee-surface-elevated text-ee-text rounded-ee-md border border-ee-border-subtle shadow-ee-elevated min-w-[132px]">
              <span className="text-xs font-ee-mono font-semibold tracking-wider uppercase text-ee-text-secondary flex items-center justify-center gap-1">
                <Ticket className="w-3.5 h-3.5" aria-hidden="true" />
                Comanda
              </span>
              <span className="mt-0.5 block text-base sm:text-lg font-bold tracking-tight text-ee-text">
                En asignación
              </span>
            </div>
          )}

          {/* Time Slot and Pickup Date */}
          <div className="text-left space-y-1 text-xs sm:text-sm">
            <div className="flex items-center gap-2 font-semibold">
              <Clock className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span>
                {fulfillmentMode === 'IMMEDIATE' ? 'Retiro inmediato' : 'Reserva programada'}
              </span>
            </div>
            <p className="opacity-95">
              <strong>Fecha:</strong>{' '}
              <span className="font-ee-mono tabular-nums">{pickupDate}</span>
            </p>
            <p className="opacity-95">
              <strong>Turno:</strong>{' '}
              <span className="font-ee-mono tabular-nums">
                {formattedStart} a {formattedEnd} hs
              </span>
            </p>
          </div>
        </div>
      </div>
    </section>
  );
};
