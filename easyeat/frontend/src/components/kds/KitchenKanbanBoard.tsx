import React, { useState } from 'react';
import type { KdsTicket } from '../../types/kds';
import { KdsQueuedCard } from './KdsQueuedCard';
import { KdsInPreparationCard } from './KdsInPreparationCard';

export interface KitchenKanbanBoardProps {
  tickets: KdsTicket[];
  onStartPreparation: (ticketId: string) => Promise<void> | void;
  onDelayUpdated?: (ticketId: string, newDelayMinutes: number) => void;
}

export const KitchenKanbanBoard: React.FC<KitchenKanbanBoardProps> = ({
  tickets,
  onStartPreparation,
  onDelayUpdated,
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
            queuedTickets.map((ticket) => (
              <KdsQueuedCard
                key={ticket.ticketId}
                ticket={ticket}
                isLoading={!!loadingTicketIds[ticket.ticketId]}
                onStartPreparation={handleStart}
              />
            ))
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
              <KdsInPreparationCard
                key={ticket.ticketId}
                ticket={ticket}
                onDelayUpdated={(mins) => onDelayUpdated?.(ticket.ticketId, mins)}
              />
            ))
          )}
        </div>
      </div>
    </div>
  );
};
