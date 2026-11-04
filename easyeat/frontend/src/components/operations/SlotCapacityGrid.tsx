import React, { useEffect, useMemo, useState } from 'react';
import type { SlotCapacity, UpdateSlotCapacityRequest } from '../../types/operations';

export interface SlotCapacityGridProps {
  /** Day being edited, `YYYY-MM-DD`. */
  date: string;
  /** The 15-minute matrix of `date`; unconfigured slots arrive with capacity 0 and no version. */
  slots: SlotCapacity[];
  onDateChange: (date: string) => void;
  /** One call carries every changed slot, also for a bulk apply (SC-005). */
  onSave: (changes: UpdateSlotCapacityRequest[]) => Promise<void>;
  /** Matrix of another date, used to apply one value over a date range with the right versions. */
  loadDay: (date: string) => Promise<SlotCapacity[]>;
  onReload?: () => void;
}

const INVALID_CAPACITY = 'La capacidad debe ser un número entero mayor o igual a 0.';
const INVALID_RANGE = 'La fecha final debe ser igual o posterior a la fecha inicial.';
const CONFLICT_MESSAGE =
  'La configuración fue modificada por otro administrador. Recargue la pantalla para ver los cambios.';
const GENERIC_ERROR_MESSAGE = 'No se pudo guardar la capacidad. Intente nuevamente.';

const hhmm = (time: string): string => time.slice(0, 5);
const WHOLE_NUMBER = /^\d+$/;

const isConflict = (error: unknown): boolean =>
  typeof error === 'object' && error !== null && (error as { response?: { status?: number } }).response?.status === 409;

/** Every date after `from` up to and including `to`, as `YYYY-MM-DD`. */
const datesAfter = (from: string, to: string): string[] => {
  const dates: string[] = [];
  const cursor = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  cursor.setUTCDate(cursor.getUTCDate() + 1);
  while (cursor <= end) {
    dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return dates;
};

export const SlotCapacityGrid: React.FC<SlotCapacityGridProps> = ({
  date,
  slots,
  onDateChange,
  onSave,
  loadDay,
  onReload,
}) => {
  const original = useMemo(() => {
    const values: Record<string, string> = {};
    slots.forEach((slot) => {
      values[slot.startTime] = String(slot.maxCapacity);
    });
    return values;
  }, [slots]);

  const [drafts, setDrafts] = useState<Record<string, string>>(original);
  const [rangeChanges, setRangeChanges] = useState<UpdateSlotCapacityRequest[]>([]);
  const [bulkValue, setBulkValue] = useState('');
  const [rangeEnd, setRangeEnd] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    setDrafts(original);
    setRangeChanges([]);
    setError(null);
    setConflict(false);
  }, [original, date]);

  const editedSlots = slots.filter((slot) => drafts[slot.startTime] !== original[slot.startTime]);
  const hasChanges = editedSlots.length > 0 || rangeChanges.length > 0;

  const handleApplyToAll = async () => {
    setConflict(false);
    if (!WHOLE_NUMBER.test(bulkValue.trim())) {
      setError(INVALID_CAPACITY);
      return;
    }
    if (rangeEnd && rangeEnd < date) {
      setError(INVALID_RANGE);
      return;
    }
    const value = bulkValue.trim();
    setError(null);
    setDrafts(Object.fromEntries(slots.map((slot) => [slot.startTime, value])));

    if (!rangeEnd || rangeEnd === date) {
      setRangeChanges([]);
      return;
    }
    setIsBusy(true);
    try {
      const days = await Promise.all(datesAfter(date, rangeEnd).map((day) => loadDay(day)));
      setRangeChanges(
        days.flat().map((slot) => ({
          slotDate: slot.slotDate,
          startTime: slot.startTime,
          maxCapacity: Number(value),
          expectedVersion: slot.version,
        }))
      );
    } catch {
      setError(GENERIC_ERROR_MESSAGE);
    } finally {
      setIsBusy(false);
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setConflict(false);

    if (editedSlots.some((slot) => !WHOLE_NUMBER.test(drafts[slot.startTime].trim()))) {
      setError(INVALID_CAPACITY);
      return;
    }

    const changes: UpdateSlotCapacityRequest[] = [
      ...editedSlots.map((slot) => ({
        slotDate: slot.slotDate,
        startTime: slot.startTime,
        maxCapacity: Number(drafts[slot.startTime]),
        expectedVersion: slot.version,
      })),
      ...rangeChanges,
    ];

    setError(null);
    setIsBusy(true);
    try {
      await onSave(changes);
    } catch (failure) {
      if (isConflict(failure)) {
        setError(CONFLICT_MESSAGE);
        setConflict(true);
      } else {
        setError(GENERIC_ERROR_MESSAGE);
      }
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit} className="space-y-4" aria-label="Capacidad por franja horaria">
      <div className="flex flex-wrap items-end gap-4">
        <label className="text-sm text-gray-700">
          Fecha
          <input
            type="date"
            value={date}
            onChange={(event) => onDateChange(event.target.value)}
            className="block mt-1 px-2 py-1 border border-gray-300 rounded-md"
          />
        </label>
        <label className="text-sm text-gray-700">
          Capacidad para todas las franjas
          <input
            type="number"
            min={0}
            step={1}
            value={bulkValue}
            onChange={(event) => setBulkValue(event.target.value)}
            className="block mt-1 w-24 px-2 py-1 border border-gray-300 rounded-md"
          />
        </label>
        <label className="text-sm text-gray-700">
          Hasta (opcional)
          <input
            type="date"
            value={rangeEnd}
            onChange={(event) => setRangeEnd(event.target.value)}
            className="block mt-1 px-2 py-1 border border-gray-300 rounded-md"
          />
        </label>
        <button
          type="button"
          onClick={handleApplyToAll}
          disabled={isBusy || slots.length === 0}
          className="px-3 py-2 rounded-md border border-blue-600 text-blue-700 text-sm font-medium disabled:opacity-50"
        >
          Aplicar a todas
        </button>
      </div>

      {slots.length === 0 ? (
        <p className="text-sm text-gray-500">Este día no tiene franjas para configurar.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="py-2 pr-4 font-medium">Franja</th>
                <th className="py-2 pr-4 font-medium">Capacidad</th>
                <th className="py-2 pr-4 font-medium">Aprobados / máx.</th>
                <th className="py-2 font-medium">Estado</th>
              </tr>
            </thead>
            <tbody>
              {slots.map((slot) => (
                <tr key={slot.startTime} className="border-t border-gray-100">
                  <td className="py-2 pr-4 font-medium text-gray-800">
                    {hhmm(slot.startTime)} - {hhmm(slot.endTime)}
                  </td>
                  <td className="py-2 pr-4">
                    <input
                      type="number"
                      min={0}
                      step={1}
                      aria-label={`Capacidad ${hhmm(slot.startTime)}`}
                      value={drafts[slot.startTime] ?? ''}
                      onChange={(event) => setDrafts((current) => ({ ...current, [slot.startTime]: event.target.value }))}
                      className="w-24 px-2 py-1 border border-gray-300 rounded-md"
                    />
                  </td>
                  <td className="py-2 pr-4 text-gray-700">
                    {slot.approvedOrdersCount} / {slot.maxCapacity}
                  </td>
                  <td className="py-2">
                    {slot.isSoldOut && (
                      <span className="px-2 py-0.5 rounded-full text-xs bg-red-100 text-red-800 border border-red-200">
                        Agotada
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {error && (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
          {conflict && onReload && (
            <button type="button" onClick={onReload} className="ml-3 underline font-medium">
              Recargar
            </button>
          )}
        </div>
      )}

      <button
        type="submit"
        disabled={isBusy || !hasChanges}
        className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium disabled:opacity-50"
      >
        {isBusy ? 'Guardando...' : 'Guardar capacidad'}
      </button>
    </form>
  );
};
