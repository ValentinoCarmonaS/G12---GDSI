import React from 'react';
import type { KdsTicket } from '../../types/kds';

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

  return (
    <div
      data-testid={`queued-card-${ticket.ticketId}`}
      className="bg-slate-800/80 border border-slate-700/60 rounded-lg p-4 flex flex-col gap-3 shadow-sm hover:border-slate-600 transition-colors"
    >
      <div className="flex items-center justify-between">
        <span className="text-xl font-black text-amber-400 tracking-wide">
          #{ticket.ticketNumber}
        </span>
        <span className="text-xs font-semibold px-2 py-1 bg-slate-700 text-slate-200 rounded">
          {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
        </span>
      </div>

      <button
        type="button"
        onClick={() => onStartPreparation(ticket.ticketId)}
        disabled={isLoading}
        className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-2 shadow-md uppercase tracking-wider text-sm"
      >
        {isLoading ? 'INICIANDO...' : 'EMPEZAR'}
      </button>
    </div>
  );
};
