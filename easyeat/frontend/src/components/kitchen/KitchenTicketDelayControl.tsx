import React, { useState, useEffect } from 'react';
import { addTicketDelay } from '../../api/kitchenKdsApi';
import { StatusBadge } from '../primitives/StatusBadge';

export interface KitchenTicketDelayControlProps {
  ticketId: string;
  initialDelayMinutes?: number;
  onDelayUpdated?: (newDelayMinutes: number) => void;
  onError?: (message: string) => void;
  className?: string;
}

export const KitchenTicketDelayControl: React.FC<KitchenTicketDelayControlProps> = ({
  ticketId,
  initialDelayMinutes = 0,
  onDelayUpdated,
  onError,
  className = '',
}) => {
  const [delayMinutes, setDelayMinutes] = useState<number>(initialDelayMinutes);
  const [isUpdating, setIsUpdating] = useState<boolean>(false);
  const [announcement, setAnnouncement] = useState<string>('');

  useEffect(() => {
    setDelayMinutes(initialDelayMinutes);
  }, [initialDelayMinutes]);

  const handleAddDelay = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isUpdating) return;

    setIsUpdating(true);
    try {
      const response = await addTicketDelay(ticketId);
      setDelayMinutes(response.specificDelayMinutes);
      setAnnouncement(`Demora actualizada a +${response.specificDelayMinutes} minutos`);
      onDelayUpdated?.(response.specificDelayMinutes);
    } catch (error) {
      console.error('Error adding ticket delay:', error);
      onError?.('No se pudo sumar demora al ticket. Intentá nuevamente.');
    } finally {
      setIsUpdating(false);
    }
  };

  return (
    <div className={`flex items-center gap-3 ${className}`}>
      <button
        type="button"
        disabled={isUpdating}
        onClick={handleAddDelay}
        aria-label="+5"
        className="min-h-[56px] min-w-[56px] px-3 py-2 bg-ee-warning-bg text-ee-warning-text border-2 border-ee-text font-mono text-base font-black rounded-sm shadow-ee-elevated transition-transform hover:bg-ee-surface-muted active:translate-x-[2px] active:translate-y-[2px] motion-reduce:transition-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-text"
        title="Añadir 5 minutos de demora"
      >
        +5
      </button>

      {delayMinutes > 0 && (
        <StatusBadge
          variant="warning"
          data-testid="delay-badge"
          className="font-mono font-bold border-2 border-ee-warning-border"
        >
          +{delayMinutes} min
        </StatusBadge>
      )}

      <span className="sr-only" aria-live="polite">
        {announcement}
      </span>
    </div>
  );
};
