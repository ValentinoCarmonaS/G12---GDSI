import React, { useState } from 'react';
import type { DinerTimeSlot } from '../../types/operations';

export interface DinerSlotSelectorProps {
  slots: DinerTimeSlot[];
  isClosed: boolean;
  closureReason?: string | null;
  /** Called with the start time of the slot once it was revalidated. */
  onConfirm: (startTime: string) => void | Promise<void>;
  /** Checks the slot again against the server just before the order is created (EC-013). */
  revalidate: (startTime: string) => Promise<void>;
}

const SOLD_OUT_MESSAGE = 'La franja seleccionada se agotó mientras elegías. Por favor elegí otra franja.';
const GENERIC_ERROR_MESSAGE = 'No se pudo verificar la franja. Intente nuevamente.';

const hhmm = (time: string): string => time.slice(0, 5);

const isConflict = (error: unknown): boolean =>
  typeof error === 'object' && error !== null && (error as { response?: { status?: number } }).response?.status === 409;

/**
 * Slot picker of the diner. It receives its data and its revalidation from the caller: no orders
 * module exists yet, so the wire-up lives in the integration document (T048).
 */
export const DinerSlotSelector: React.FC<DinerSlotSelectorProps> = ({
  slots,
  isClosed,
  closureReason,
  onConfirm,
  revalidate,
}) => {
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isChecking, setIsChecking] = useState(false);

  if (isClosed) {
    return <p className="text-sm text-gray-600">{closureReason ?? 'Cocina cerrada para este día.'}</p>;
  }

  const hasAvailable = slots.some((slot) => slot.status === 'AVAILABLE');

  const handleConfirm = async () => {
    if (!selected) {
      return;
    }
    setError(null);
    setIsChecking(true);
    try {
      await revalidate(selected);
    } catch (failure) {
      setError(isConflict(failure) ? SOLD_OUT_MESSAGE : GENERIC_ERROR_MESSAGE);
      if (isConflict(failure)) {
        setSelected(null);
      }
      setIsChecking(false);
      return;
    }
    setIsChecking(false);
    await onConfirm(selected);
  };

  return (
    <div className="space-y-4">
      {!hasAvailable && <p className="text-sm text-gray-600">No quedan franjas disponibles para este día.</p>}

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {slots.map((slot) => {
          const soldOut = slot.status === 'SOLD_OUT';
          const isSelected = selected === slot.startTime;
          return (
            <button
              key={slot.startTime}
              type="button"
              disabled={soldOut}
              aria-pressed={isSelected}
              onClick={() => {
                setSelected(slot.startTime);
                setError(null);
              }}
              className={`px-3 py-2 rounded-md border text-sm ${
                soldOut
                  ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed'
                  : isSelected
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'bg-white text-gray-800 border-gray-300 hover:border-blue-400'
              }`}
            >
              {hhmm(slot.startTime)} - {hhmm(slot.endTime)}
              {soldOut && <span className="block text-xs font-semibold text-red-600">Agotada</span>}
            </button>
          );
        })}
      </div>

      {error && (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
        </div>
      )}

      <button
        type="button"
        onClick={handleConfirm}
        disabled={!selected || isChecking}
        className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium disabled:opacity-50"
      >
        {isChecking ? 'Verificando...' : 'Confirmar franja'}
      </button>
    </div>
  );
};
