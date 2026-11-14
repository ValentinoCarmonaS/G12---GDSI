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
    <div
      data-testid="order-success-green-banner"
      className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-emerald-600 via-emerald-700 to-emerald-800 text-white p-6 sm:p-8 shadow-xl"
    >
      {/* Decorative background glow */}
      <div className="absolute -top-12 -right-12 w-48 h-48 bg-emerald-400 opacity-20 rounded-full blur-2xl pointer-events-none" />

      <div className="relative z-10 flex flex-col md:flex-row items-center justify-between gap-6">
        {/* Left column: Animated Verification Icon + Confirmation Header */}
        <div className="flex items-center gap-5 text-center md:text-left">
          <div className="flex-shrink-0 relative">
            <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-white/20 backdrop-blur-sm flex items-center justify-center shadow-inner border border-white/30 animate-pulse">
              <CheckCircle2 className="w-10 h-10 sm:w-12 sm:h-12 text-white drop-shadow-md transition-transform duration-500 hover:scale-110" />
            </div>
          </div>
          <div className="space-y-1">
            <span className="inline-block px-3 py-1 rounded-full bg-emerald-500/40 border border-emerald-300/30 text-xs font-bold tracking-wider uppercase">
              Pago Confirmado
            </span>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
              ¡Tu pedido está en marcha!
            </h1>
            <div className="flex items-center justify-center md:justify-start gap-2 text-emerald-100 text-sm">
              <Store className="w-4 h-4" />
              <span className="font-medium">{establishmentName}</span>
            </div>
          </div>
        </div>

        {/* Right column: Prominent Ticket Badge & Pickup Slot Detail */}
        <div className="flex flex-col sm:flex-row items-center gap-4 bg-white/10 backdrop-blur-md rounded-2xl p-4 border border-white/20 shadow-sm w-full md:w-auto">
          {/* Ticket Number in Large Format */}
          {ticketNumber != null ? (
            <div className="text-center px-4 py-2 bg-white text-emerald-800 rounded-xl shadow-md min-w-[120px]">
              <span className="text-[11px] font-bold tracking-wider uppercase text-emerald-600 block flex items-center justify-center gap-1">
                <Ticket className="w-3.5 h-3.5" />
                Ticket
              </span>
              <span className="text-3xl sm:text-4xl font-black tracking-tight text-gray-900">
                #{ticketNumber}
              </span>
            </div>
          ) : (
            <div className="text-center px-4 py-2 bg-white text-emerald-800 rounded-xl shadow-md min-w-[120px]">
              <span className="text-[11px] font-bold tracking-wider uppercase text-emerald-600 block flex items-center justify-center gap-1">
                <Ticket className="w-3.5 h-3.5" />
                Comanda
              </span>
              <span className="text-base sm:text-lg font-bold tracking-tight text-gray-900">
                En asignación
              </span>
            </div>
          )}

          {/* Time Slot and Pickup Date */}
          <div className="text-left space-y-1 text-xs sm:text-sm text-emerald-50 pr-2">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-emerald-200" />
              <span className="font-semibold text-white">
                {fulfillmentMode === 'IMMEDIATE' ? 'Retiro inmediato' : 'Reserva programada'}
              </span>
            </div>
            <p className="text-emerald-100">
              <strong className="text-white">Fecha:</strong> {pickupDate}
            </p>
            <p className="text-emerald-100">
              <strong className="text-white">Turno:</strong> {formattedStart} a {formattedEnd} hs
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
