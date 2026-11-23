import React, { useState } from 'react';
import type { KdsTicket } from '../../types/kds';
import { KdsQueuedCard } from './KdsQueuedCard';
import { KdsInPreparationCard } from './KdsInPreparationCard';
import { LoadingSkeleton } from '../ui/LoadingSkeleton';
import { EmptyState } from '../ui/EmptyState';

export interface KitchenKanbanBoardProps {
  tickets: KdsTicket[];
  isLoading?: boolean;
  onStartPreparation: (ticketId: string) => Promise<void> | void;
  onReadyForPickup?: (ticketId: string) => Promise<void> | void;
  onDelayUpdated?: (ticketId: string, newDelayMinutes: number) => void;
  onDelayError?: (message: string) => void;
}

export const KitchenKanbanBoard: React.FC<KitchenKanbanBoardProps> = ({
  tickets,
  isLoading = false,
  onStartPreparation,
  onReadyForPickup,
  onDelayUpdated,
  onDelayError,
}) => {
  const [loadingTicketIds, setLoadingTicketIds] = useState<Record<string, boolean>>({});
  const [loadingReadyTicketIds, setLoadingReadyTicketIds] = useState<Record<string, boolean>>({});
  const [activeMobileColumn, setActiveMobileColumn] = useState<'queued' | 'in-preparation'>('queued');

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

  const handleReady = async (ticketId: string) => {
    if (!onReadyForPickup || loadingReadyTicketIds[ticketId]) return;
    setLoadingReadyTicketIds((prev) => ({ ...prev, [ticketId]: true }));
    try {
      await onReadyForPickup(ticketId);
    } finally {
      setLoadingReadyTicketIds((prev) => ({ ...prev, [ticketId]: false }));
    }
  };

  if (isLoading) {
    return (
      <div data-testid="kds-loading-skeleton" className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
        <div className="bg-ee-surface border-2 border-ee-text rounded-sm p-4 shadow-ee-elevated space-y-4">
          <LoadingSkeleton className="h-8 w-40" />
          <LoadingSkeleton className="h-36 w-full" />
          <LoadingSkeleton className="h-36 w-full" />
        </div>
        <div className="bg-ee-surface border-2 border-ee-text rounded-sm p-4 shadow-ee-elevated space-y-4">
          <LoadingSkeleton className="h-8 w-48" />
          <LoadingSkeleton className="h-36 w-full" />
          <LoadingSkeleton className="h-36 w-full" />
        </div>
      </div>
    );
  }

  return (
    <div className="w-full space-y-4">
      {/* Selector rápido móvil Brutalism (>= 56px) */}
      <div
        data-testid="kds-mobile-switcher"
        className="grid grid-cols-2 gap-3 md:hidden"
        role="group"
        aria-label="Cambiar columna KDS en móvil"
      >
        <button
          type="button"
          aria-pressed={activeMobileColumn === 'queued'}
          onClick={() => setActiveMobileColumn('queued')}
          className={`min-h-[56px] px-3 py-2 border-2 border-ee-text rounded-sm font-bold text-sm uppercase tracking-wider flex items-center justify-center gap-2 transition-colors ${
            activeMobileColumn === 'queued'
              ? 'bg-ee-text text-ee-surface shadow-ee-elevated'
              : 'bg-ee-surface text-ee-text'
          }`}
        >
          <span>Pendientes</span>
          <span className="font-mono font-black">({queuedTickets.length})</span>
        </button>
        <button
          type="button"
          aria-pressed={activeMobileColumn === 'in-preparation'}
          onClick={() => setActiveMobileColumn('in-preparation')}
          className={`min-h-[56px] px-3 py-2 border-2 border-ee-text rounded-sm font-bold text-sm uppercase tracking-wider flex items-center justify-center gap-2 transition-colors ${
            activeMobileColumn === 'in-preparation'
              ? 'bg-ee-text text-ee-surface shadow-ee-elevated'
              : 'bg-ee-surface text-ee-text'
          }`}
        >
          <span>En preparación</span>
          <span className="font-mono font-black">({inPreparationTickets.length})</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
        {/* Columna 1: Pendientes */}
        <div
          data-testid="queued-column"
          className={`${
            activeMobileColumn === 'queued' ? 'flex' : 'hidden md:flex'
          } flex-col bg-ee-surface-muted border-2 border-ee-text rounded-sm p-4 min-h-[500px] shadow-ee-elevated`}
        >
          <div className="flex items-center justify-between pb-3 border-b-2 border-ee-text mb-4">
            <h2 className="text-xl font-black text-ee-text uppercase tracking-tight flex items-center gap-2">
              <span>Pendientes</span>
              <span className="text-sm bg-ee-surface text-ee-text border-2 border-ee-text px-2.5 py-0.5 rounded-sm font-mono font-black">
                {queuedTickets.length}
              </span>
            </h2>
          </div>

          <div className="flex flex-col gap-4">
            {queuedTickets.length === 0 ? (
              <EmptyState
                title="Sin pedidos pendientes"
                description="Las nuevas comandas aparecerán automáticamente aquí."
              />
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
          className={`${
            activeMobileColumn === 'in-preparation' ? 'flex' : 'hidden md:flex'
          } flex-col bg-ee-surface-muted border-2 border-ee-text rounded-sm p-4 min-h-[500px] shadow-ee-elevated`}
        >
          <div
            data-testid="kds-column-in-preparation"
            aria-label="Columna 2: En preparación activa"
            className="flex flex-col h-full"
          >
            <div className="flex items-center justify-between pb-3 border-b-2 border-ee-text mb-4">
              <h2 className="text-xl font-black text-ee-text uppercase tracking-tight flex items-center gap-2">
                <span>En preparación</span>
                <span className="text-sm bg-ee-surface text-ee-text border-2 border-ee-text px-2.5 py-0.5 rounded-sm font-mono font-black">
                  {inPreparationTickets.length}
                </span>
              </h2>
            </div>

            <div className="flex flex-col gap-4">
              {inPreparationTickets.length === 0 ? (
                <EmptyState
                  title="Sin pedidos en preparación"
                  description="Tocá EMPEZAR en una comanda pendiente para pasarla a cocina."
                />
              ) : (
                inPreparationTickets.map((ticket) => (
                  <KdsInPreparationCard
                    key={ticket.ticketId}
                    ticket={ticket}
                    onReadyForPickup={onReadyForPickup ? handleReady : undefined}
                    isReadyLoading={!!loadingReadyTicketIds[ticket.ticketId]}
                    onDelayUpdated={(mins) => onDelayUpdated?.(ticket.ticketId, mins)}
                    onDelayError={onDelayError}
                  />
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
