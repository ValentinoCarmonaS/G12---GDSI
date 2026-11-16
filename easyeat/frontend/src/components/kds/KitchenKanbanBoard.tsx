import React, { useState } from 'react';
import type { KdsTicket } from '../../types/kds';
import { formatDishItem } from '../../utils/kdsTimeUtils';

export interface KitchenKanbanBoardProps {
  tickets: KdsTicket[];
  onStartPreparation: (ticketId: string) => Promise<void> | void;
}

export const KitchenKanbanBoard: React.FC<KitchenKanbanBoardProps> = ({
  tickets,
  onStartPreparation,
}) => {
  const [loadingTicketIds, setLoadingTicketIds] = useState<Record<string, boolean>>({});

  const queuedTickets = tickets.filter((t) => t.status === 'QUEUED');
  const inPreparationTickets = tickets.filter((t) => t.status === 'IN_PREPARATION');

  const handleStart = async (ticketId: string) => {
    if (loadingTicketIds[ticketId]) return;
    setLoadingTicketIds((prev) => ({ ...prev, [ticketId]: true }));
    try {
      await onStartPreparation(ticketId);
    } finally {
      setLoadingTicketIds((prev) => ({ ...prev, [ticketId]: false }));
    }
  };

  const formatSlot = (start?: string, end?: string) => {
    if (!start) return '';
    const cleanStart = start.slice(0, 5);
    const cleanEnd = end ? end.slice(0, 5) : '';
    return cleanEnd ? `${cleanStart} - ${cleanEnd}` : cleanStart;
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
      {/* Columna 1: Pendientes */}
      <div
        data-testid="queued-column"
        className="flex flex-col bg-slate-900 border border-slate-800 rounded-xl p-4 min-h-[500px]"
      >
        <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
          <h2 className="text-lg font-bold text-slate-100 flex items-center gap-2">
            <span>Pendientes</span>
            <span className="text-xs bg-slate-800 text-slate-300 px-2 py-0.5 rounded-full font-mono">
              {queuedTickets.length}
            </span>
          </h2>
        </div>

        <div className="flex flex-col gap-3 overflow-y-auto max-h-[70vh] pr-1">
          {queuedTickets.length === 0 ? (
            <div className="text-center py-12 text-slate-400 text-sm italic">
              Sin pedidos pendientes
            </div>
          ) : (
            queuedTickets.map((ticket) => {
              const isLoading = !!loadingTicketIds[ticket.ticketId];
              return (
                <div
                  key={ticket.ticketId}
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
                    onClick={() => handleStart(ticket.ticketId)}
                    disabled={isLoading}
                    className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-2 shadow-md uppercase tracking-wider text-sm"
                  >
                    {isLoading ? 'INICIANDO...' : 'EMPEZAR'}
                  </button>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Columna 2: En preparación */}
      <div
        data-testid="in-preparation-column"
        className="flex flex-col bg-slate-900 border border-slate-800 rounded-xl p-4 min-h-[500px]"
      >
        <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
          <h2 className="text-lg font-bold text-slate-100 flex items-center gap-2">
            <span>En preparación</span>
            <span className="text-xs bg-slate-800 text-slate-300 px-2 py-0.5 rounded-full font-mono">
              {inPreparationTickets.length}
            </span>
          </h2>
        </div>

        <div className="flex flex-col gap-3 overflow-y-auto max-h-[70vh] pr-1">
          {inPreparationTickets.length === 0 ? (
            <div className="text-center py-12 text-slate-400 text-sm italic">
              Sin pedidos en preparación
            </div>
          ) : (
            inPreparationTickets.map((ticket) => (
              <div
                key={ticket.ticketId}
                className="bg-slate-800/90 border border-slate-700 rounded-lg p-4 flex flex-col gap-3 shadow-md"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xl font-black text-amber-400 tracking-wide">
                    #{ticket.ticketNumber}
                  </span>
                  <span className="text-xs font-semibold px-2 py-1 bg-slate-700 text-slate-200 rounded">
                    {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
                  </span>
                </div>

                <div className="border-t border-slate-700/60 pt-2 flex flex-col gap-1.5">
                  {ticket.items && ticket.items.length > 0 ? (
                    ticket.items.map((item, idx) => (
                      <div
                        key={`${item.dishId}-${idx}`}
                        className="text-sm text-slate-100 font-medium"
                      >
                        {formatDishItem(item.quantity, item.dishName)}
                      </div>
                    ))
                  ) : (
                    <div className="text-xs text-slate-400 italic">Sin platos detallados</div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
