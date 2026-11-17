import React from 'react';
import type { KdsTicket } from '../../types/kds';
import { formatDishItem } from '../../utils/kdsFormatters';
import { useKdsCountUp } from '../../hooks/useKdsCountUp';
import { isTicketDelayed } from '../../utils/kdsAlertThreshold';
import { KitchenTicketDelayControl } from '../kitchen/KitchenTicketDelayControl';

export interface KdsInPreparationCardProps {
  ticket: KdsTicket;
  onReadyForPickup?: (ticketId: string) => Promise<void> | void;
  isReadyLoading?: boolean;
  onDelayUpdated?: (newDelayMinutes: number) => void;
}

export const KdsInPreparationCard: React.FC<KdsInPreparationCardProps> = ({
  ticket,
  onReadyForPickup,
  isReadyLoading,
  onDelayUpdated,
}) => {
  const { elapsedMinutes, formattedTime } = useKdsCountUp(ticket.startedAt);
  const delayed = isTicketDelayed(elapsedMinutes);

  const formatSlot = (start?: string, end?: string) => {
    if (!start) return '';
    const cleanStart = start.slice(0, 5);
    const cleanEnd = end ? end.slice(0, 5) : '';
    return cleanEnd ? `${cleanStart} - ${cleanEnd}` : cleanStart;
  };

  return (
    <div
      data-testid={`in-preparation-card-${ticket.ticketId}`}
      className={`rounded-xl p-4 flex flex-col gap-3.5 shadow-md transition-colors select-none ${
        delayed
          ? 'bg-slate-800/90 border border-amber-500 ring-2 ring-amber-500'
          : 'bg-slate-800/90 border border-slate-700/80'
      }`}
    >
      <div
        data-testid={`kds-ticket-card-${ticket.ticketId}`}
        className="flex flex-col gap-3.5"
      >
        <div className="flex items-center justify-between">
          <span className="text-2xl font-black text-amber-400 tracking-wide font-mono">
            #{ticket.ticketNumber}
          </span>
          <div className="flex items-center gap-2">
            {ticket.startedAt && (
              <span
                data-testid={`countup-badge-${ticket.ticketId}`}
                className={`text-sm font-black px-2.5 py-1 rounded-md font-mono transition-colors shadow-sm ${
                  delayed
                    ? 'bg-amber-100 text-amber-800'
                    : 'bg-slate-700 text-slate-100'
                }`}
              >
                {formattedTime}
              </span>
            )}
            <span className="text-sm font-bold px-2.5 py-1 bg-slate-700/90 text-slate-100 rounded-md font-mono">
              {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
            </span>
            <KitchenTicketDelayControl
              ticketId={ticket.ticketId}
              initialDelayMinutes={ticket.specificDelayMinutes ?? 0}
              onDelayUpdated={onDelayUpdated}
            />
          </div>
        </div>

        <div className="border-t border-slate-700/60 pt-2.5 flex flex-col gap-2">
          {ticket.items && ticket.items.length > 0 ? (
            ticket.items.map((item, idx) => (
              <div
                key={`${item.dishId}-${idx}`}
                className="text-base text-slate-100 font-bold tracking-normal leading-snug"
              >
                {formatDishItem(item.quantity, item.dishName)}
              </div>
            ))
          ) : (
            <div className="text-xs text-slate-400 italic">Sin platos detallados</div>
          )}
        </div>

        {onReadyForPickup && (
          <button
            type="button"
            disabled={isReadyLoading}
            onClick={() => onReadyForPickup(ticket.ticketId)}
            className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-sm font-extrabold uppercase tracking-wide rounded-xl shadow-sm transition-colors mt-2"
          >
            LISTO
          </button>
        )}
      </div>
    </div>
  );
};
