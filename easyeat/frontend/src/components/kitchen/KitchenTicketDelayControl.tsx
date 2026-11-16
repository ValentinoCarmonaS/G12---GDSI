import React, { useState, useEffect } from 'react';
import { addTicketDelay } from '../../api/kitchenKdsApi';

export interface KitchenTicketDelayControlProps {
  ticketId: string;
  initialDelayMinutes?: number;
  onDelayUpdated?: (newDelayMinutes: number) => void;
  className?: string;
}

export const KitchenTicketDelayControl: React.FC<KitchenTicketDelayControlProps> = ({
  ticketId,
  initialDelayMinutes = 0,
  onDelayUpdated,
  className = '',
}) => {
  const [delayMinutes, setDelayMinutes] = useState<number>(initialDelayMinutes);
  const [isUpdating, setIsUpdating] = useState<boolean>(false);

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
      onDelayUpdated?.(response.specificDelayMinutes);
    } catch (error) {
      console.error('Error adding ticket delay:', error);
    } finally {
      setIsUpdating(false);
    }
  };

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <button
        type="button"
        disabled={isUpdating}
        onClick={handleAddDelay}
        className="px-2.5 py-1 bg-amber-600 hover:bg-amber-500 disabled:opacity-50 text-white text-xs font-bold rounded-md shadow-sm transition-all flex items-center gap-1 active:scale-95"
        title="Añadir 5 minutos de demora"
      >
        +5
      </button>

      {delayMinutes > 0 && (
        <span
          data-testid="delay-badge"
          className="text-xs font-bold px-2 py-0.5 bg-amber-500/20 text-amber-400 border border-amber-500/40 rounded font-mono"
        >
          +{delayMinutes} min
        </span>
      )}
    </div>
  );
};
