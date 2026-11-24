import React from 'react';
import { AlertTriangle } from 'lucide-react';
import type { KdsTicket } from '../../types/kds';
import { formatDishItem } from '../../utils/kdsFormatters';
import { useKdsCountUp } from '../../hooks/useKdsCountUp';
import { isTicketDelayed } from '../../utils/kdsAlertThreshold';
import { KitchenTicketDelayControl } from '../kitchen/KitchenTicketDelayControl';
import { StatusBadge } from '../primitives/StatusBadge';

export interface KdsInPreparationCardProps {
  ticket: KdsTicket;
  onReadyForPickup?: (ticketId: string) => Promise<void> | void;
  isReadyLoading?: boolean;
  onDelayUpdated?: (newDelayMinutes: number) => void;
  onDelayError?: (message: string) => void;
}

export const KdsInPreparationCard: React.FC<KdsInPreparationCardProps> = ({
  ticket,
  onReadyForPickup,
  isReadyLoading = false,
  onDelayUpdated,
  onDelayError,
}) => {
  const { elapsedMinutes, formattedTime } = useKdsCountUp(ticket.startedAt);
  const delayed = isTicketDelayed(elapsedMinutes);

  const formatSlot = (start?: string, end?: string) => {
    if (!start) return '';
    const cleanStart = start.slice(0, 5);
    const cleanEnd = end ? end.slice(0, 5) : '';
    return cleanEnd ? `${cleanStart} - ${cleanEnd}` : cleanStart;
  };

  const fulfillmentLabel = ticket.fulfillmentMode === 'IMMEDIATE' ? 'Inmediato' : 'Programado';

  return (
    <div
      data-testid={`in-preparation-card-${ticket.ticketId}`}
      data-delayed={delayed ? 'true' : 'false'}
      className={`bg-ee-surface rounded-sm p-4 flex flex-col gap-4 shadow-ee-elevated select-none ${
        delayed
          ? 'border-4 border-ee-warning-border'
          : 'border-2 border-ee-text'
      }`}
    >
      <div
        data-testid={`kds-ticket-card-${ticket.ticketId}`}
        className="flex flex-col gap-4"
      >
        {/* Bloque 1: Cabecera */}
        <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b-2 border-ee-text">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-2xl md:text-3xl font-black text-ee-text tracking-tight font-mono">
              #{ticket.ticketNumber}
            </span>
            <StatusBadge variant={ticket.fulfillmentMode === 'IMMEDIATE' ? 'warning' : 'neutral'}>
              {fulfillmentLabel}
            </StatusBadge>
            {delayed && (
              <StatusBadge variant="warning" className="font-black border-2 border-ee-warning-border">
                <AlertTriangle className="w-3.5 h-3.5 mr-1 inline-block" aria-hidden="true" />
                DEMORA
              </StatusBadge>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {ticket.startedAt && (
              <span
                data-testid={`countup-badge-${ticket.ticketId}`}
                className={`text-sm font-black px-2.5 py-1 rounded-sm font-mono border-2 ${
                  delayed
                    ? 'bg-ee-warning-bg text-ee-warning-text border-ee-warning-border'
                    : 'bg-ee-surface-muted text-ee-text border-ee-text'
                }`}
              >
                {formattedTime}
              </span>
            )}
            <span className="text-sm font-bold px-2.5 py-1 bg-ee-surface-muted text-ee-text border-2 border-ee-text rounded-sm font-mono">
              {formatSlot(ticket.timeSlotStart, ticket.timeSlotEnd)}
            </span>
          </div>
        </div>

        {/* Bloque 2: Platos en tinta neutra */}
        <div className="flex flex-col gap-2">
          {ticket.items && ticket.items.length > 0 ? (
            ticket.items.map((item, idx) => (
              <div
                key={`${item.dishId}-${idx}`}
                className="text-lg text-ee-text font-bold tracking-normal leading-snug"
              >
                {formatDishItem(item.quantity, item.dishName)}
              </div>
            ))
          ) : (
            <div className="text-sm text-ee-text-muted italic">Sin platos detallados</div>
          )}
        </div>

        {/* Bloque 3: Controles físicamente separados (>= 12px) */}
        <div className="pt-2 border-t-2 border-ee-text flex flex-wrap items-center justify-between gap-4">
          <KitchenTicketDelayControl
            ticketId={ticket.ticketId}
            initialDelayMinutes={ticket.specificDelayMinutes ?? 0}
            onDelayUpdated={onDelayUpdated}
            onError={onDelayError}
          />

          {onReadyForPickup && (
            <button
              type="button"
              disabled={isReadyLoading}
              onClick={() => onReadyForPickup(ticket.ticketId)}
              className="flex-1 min-w-[140px] min-h-[64px] py-3.5 px-4 bg-ee-success-bg hover:bg-ee-surface-muted text-ee-success-text border-2 border-ee-text text-lg font-black uppercase tracking-wider rounded-sm shadow-ee-elevated transition-transform active:translate-x-[2px] active:translate-y-[2px] motion-reduce:transition-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-text"
            >
              {isReadyLoading ? 'DESPACHANDO...' : 'LISTO'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
