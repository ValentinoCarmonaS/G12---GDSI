import React from 'react';
import type { KdsTicket } from '../../types/kds';
import { formatDishItem } from '../../utils/kdsFormatters';
import { StatusBadge } from '../ui/StatusBadge';

export interface KdsQueuedCardProps {
  ticket: KdsTicket;
  onStartPreparation: (ticketId: string) => Promise<void> | void;
  isLoading?: boolean;
}

export const KdsQueuedCard: React.FC<KdsQueuedCardProps> = ({
  ticket,
  onStartPreparation,
  isLoading = false,
}) => {
  const formatSlot = (start?: string, end?: string) => {
    if (!start) return '';
    const cleanStart = start.slice(0, 5);
    const cleanEnd = end ? end.slice(0, 5) : '';
    return cleanEnd ? `${cleanStart} - ${cleanEnd}` : cleanStart;
  };

  const fulfillmentLabel = ticket.fulfillmentMode === 'IMMEDIATE' ? 'Inmediato' : 'Programado';

  return (
    <div
      data-testid={`queued-card-${ticket.ticketId}`}
      className="bg-ee-surface border-2 border-ee-text rounded-sm p-4 flex flex-col gap-4 shadow-ee-elevated select-none"
    >
      {/* Bloque 1: Cabecera */}
      <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b-2 border-ee-text">
        <div className="flex items-center gap-2">
          <span className="text-2xl md:text-3xl font-black text-ee-text tracking-tight font-mono">
            #{ticket.ticketNumber}
          </span>
          <StatusBadge variant={ticket.fulfillmentMode === 'IMMEDIATE' ? 'warning' : 'neutral'}>
            {fulfillmentLabel}
          </StatusBadge>
        </div>
        <span className="text-sm font-bold px-2.5 py-1 bg-ee-surface-muted text-ee-text border-2 border-ee-text rounded-sm font-mono">
          {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
        </span>
      </div>

      {/* Bloque 2: Platos */}
      {ticket.items && ticket.items.length > 0 && (
        <div className="flex flex-col gap-2">
          {ticket.items.map((item, idx) => (
            <div
              key={`${item.dishId}-${idx}`}
              className="text-lg text-ee-text font-bold tracking-normal leading-snug"
            >
              {formatDishItem(item.quantity, item.dishName)}
            </div>
          ))}
        </div>
      )}

      {/* Bloque 3: Acción primaria >= 64px */}
      <button
        type="button"
        onClick={() => onStartPreparation(ticket.ticketId)}
        disabled={isLoading}
        className="w-full min-h-[64px] py-3.5 px-4 bg-ee-primary hover:bg-ee-primary-hover text-ee-on-primary border-2 border-ee-text font-black rounded-sm shadow-ee-elevated transition-transform active:translate-x-[2px] active:translate-y-[2px] motion-reduce:transition-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 uppercase tracking-wider text-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-text"
      >
        {isLoading ? 'INICIANDO...' : 'EMPEZAR'}
      </button>
    </div>
  );
};
