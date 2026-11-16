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
      className="bg-slate-800/80 border border-slate-700/60 rounded-xl p-4 flex flex-col gap-4 shadow-sm hover:border-slate-600 transition-colors select-none"
    >
      <div className="flex items-center justify-between">
        <span className="text-2xl font-black text-amber-400 tracking-wide font-mono">
          #{ticket.ticketNumber}
        </span>
        <span className="text-sm font-bold px-2.5 py-1 bg-slate-700/90 text-slate-100 rounded-md font-mono">
          {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
        </span>
      </div>

      <button
        type="button"
        onClick={() => onStartPreparation(ticket.ticketId)}
        disabled={isLoading}
        className="w-full min-h-[56px] py-3.5 px-4 bg-emerald-600 hover:bg-emerald-500 active:scale-[0.98] disabled:bg-slate-700 disabled:cursor-not-allowed disabled:transform-none text-white font-black rounded-xl transition-all flex items-center justify-center gap-2 shadow-lg uppercase tracking-wider text-lg"
      >
        {isLoading ? 'INICIANDO...' : 'EMPEZAR'}
      </button>
    </div>
  );
};
