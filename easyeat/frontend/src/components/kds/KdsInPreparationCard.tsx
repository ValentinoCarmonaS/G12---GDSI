import React from 'react';
import type { KdsTicket } from '../../types/kds';
import { formatDishItem } from '../../utils/kdsFormatters';
import { useKdsCountUp } from '../../hooks/useKdsCountUp';

export interface KdsInPreparationCardProps {
  ticket: KdsTicket;
}

export const KdsInPreparationCard: React.FC<KdsInPreparationCardProps> = ({ ticket }) => {
  const { formattedTime } = useKdsCountUp(ticket.startedAt);

  const formatSlot = (start?: string, end?: string) => {
    if (!start) return '';
    const cleanStart = start.slice(0, 5);
    const cleanEnd = end ? end.slice(0, 5) : '';
    return cleanEnd ? `${cleanStart} - ${cleanEnd}` : cleanStart;
  };

  return (
    <div
      data-testid={`in-preparation-card-${ticket.ticketId}`}
      className="bg-slate-800/90 border border-slate-700 rounded-lg p-4 flex flex-col gap-3 shadow-md"
    >
      <div className="flex items-center justify-between">
        <span className="text-xl font-black text-amber-400 tracking-wide">
          #{ticket.ticketNumber}
        </span>
        <div className="flex items-center gap-2">
          {ticket.startedAt && (
            <span
              data-testid={`countup-badge-${ticket.ticketId}`}
              className="text-xs font-bold px-2 py-0.5 bg-slate-700 text-slate-200 rounded font-mono"
            >
              {formattedTime}
            </span>
          )}
          <span className="text-xs font-semibold px-2 py-1 bg-slate-700 text-slate-200 rounded">
            {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
          </span>
        </div>
      </div>

      <div className="border-t border-slate-700/60 pt-2 flex flex-col gap-1.5">
        {ticket.items && ticket.items.length > 0 ? (
          ticket.items.map((item, idx) => (
            <div
              key={`${item.dishId}-${idx}`}
              className="text-sm text-slate-100 font-medium tracking-normal"
            >
              {formatDishItem(item.quantity, item.dishName)}
            </div>
          ))
        ) : (
          <div className="text-xs text-slate-400 italic">Sin platos detallados</div>
        )}
      </div>
    </div>
  );
};
