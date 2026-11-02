import React, { useEffect, useMemo, useState } from 'react';
import type { DayOfWeek, OperatingSchedule, UpdateScheduleRequest } from '../../types/operations';

export interface WeeklyScheduleManagerProps {
  /** Only configured days; a day that is missing is closed (EC-008). */
  schedules: OperatingSchedule[];
  onSave: (changes: UpdateScheduleRequest[]) => Promise<void>;
  onReload?: () => void;
}

interface DayDraft {
  opening: string;
  closing: string;
  isClosed: boolean;
}

const WEEK: { day: DayOfWeek; label: string }[] = [
  { day: 'MONDAY', label: 'Lunes' },
  { day: 'TUESDAY', label: 'Martes' },
  { day: 'WEDNESDAY', label: 'Miércoles' },
  { day: 'THURSDAY', label: 'Jueves' },
  { day: 'FRIDAY', label: 'Viernes' },
  { day: 'SATURDAY', label: 'Sábado' },
  { day: 'SUNDAY', label: 'Domingo' },
];

const DEFAULT_OPENING = '09:00';
const DEFAULT_CLOSING = '17:00';
const CONFLICT_MESSAGE =
  'La configuración fue modificada por otro administrador. Recargue la pantalla para ver los cambios.';
const GENERIC_ERROR_MESSAGE = 'No se pudo guardar el horario. Intente nuevamente.';

const toInputTime = (time: string): string => time.slice(0, 5);
const toApiTime = (time: string): string => (time.length === 5 ? `${time}:00` : time);

const buildDrafts = (schedules: OperatingSchedule[]): Record<DayOfWeek, DayDraft> => {
  const drafts = {} as Record<DayOfWeek, DayDraft>;
  WEEK.forEach(({ day }) => {
    const configured = schedules.find((schedule) => schedule.dayOfWeek === day);
    drafts[day] = configured
      ? { opening: toInputTime(configured.openingTime), closing: toInputTime(configured.closingTime), isClosed: configured.isClosed }
      : { opening: DEFAULT_OPENING, closing: DEFAULT_CLOSING, isClosed: true };
  });
  return drafts;
};

const isConflict = (error: unknown): boolean =>
  typeof error === 'object' && error !== null && (error as { response?: { status?: number } }).response?.status === 409;

export const WeeklyScheduleManager: React.FC<WeeklyScheduleManagerProps> = ({ schedules, onSave, onReload }) => {
  const initial = useMemo(() => buildDrafts(schedules), [schedules]);
  const [drafts, setDrafts] = useState<Record<DayOfWeek, DayDraft>>(initial);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    setDrafts(initial);
    setError(null);
    setConflict(false);
  }, [initial]);

  const changedDays = WEEK.filter(({ day }) => {
    const draft = drafts[day];
    const original = initial[day];
    return (
      draft.isClosed !== original.isClosed ||
      draft.opening !== original.opening ||
      draft.closing !== original.closing
    );
  });

  const update = (day: DayOfWeek, patch: Partial<DayDraft>) => {
    setDrafts((current) => ({ ...current, [day]: { ...current[day], ...patch } }));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setConflict(false);

    const invalid = changedDays.find(({ day }) => !drafts[day].isClosed && drafts[day].opening >= drafts[day].closing);
    if (invalid) {
      setError(`La hora de apertura de ${invalid.label} debe ser anterior a la de cierre.`);
      return;
    }

    const changes: UpdateScheduleRequest[] = changedDays.map(({ day }) => {
      const draft = drafts[day];
      const configured = schedules.find((schedule) => schedule.dayOfWeek === day);
      return {
        dayOfWeek: day,
        openingTime: toApiTime(draft.opening),
        closingTime: toApiTime(draft.closing),
        isClosed: draft.isClosed,
        expectedVersion: configured ? configured.version : null,
      };
    });

    setError(null);
    setIsSaving(true);
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
      setIsSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4" aria-label="Horarios comerciales semanales">
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="py-2 pr-4 font-medium">Día</th>
              <th className="py-2 pr-4 font-medium">Apertura</th>
              <th className="py-2 pr-4 font-medium">Cierre</th>
              <th className="py-2 font-medium">Cerrado</th>
            </tr>
          </thead>
          <tbody>
            {WEEK.map(({ day, label }) => {
              const draft = drafts[day];
              return (
                <tr key={day} className="border-t border-gray-100">
                  <td className="py-2 pr-4 font-medium text-gray-800">{label}</td>
                  <td className="py-2 pr-4">
                    <input
                      type="time"
                      aria-label={`Apertura ${label}`}
                      value={draft.opening}
                      disabled={draft.isClosed}
                      onChange={(event) => update(day, { opening: event.target.value })}
                      className="px-2 py-1 border border-gray-300 rounded-md disabled:bg-gray-100 disabled:text-gray-400"
                    />
                  </td>
                  <td className="py-2 pr-4">
                    <input
                      type="time"
                      aria-label={`Cierre ${label}`}
                      value={draft.closing}
                      disabled={draft.isClosed}
                      onChange={(event) => update(day, { closing: event.target.value })}
                      className="px-2 py-1 border border-gray-300 rounded-md disabled:bg-gray-100 disabled:text-gray-400"
                    />
                  </td>
                  <td className="py-2">
                    <input
                      type="checkbox"
                      aria-label={`Cerrado ${label}`}
                      checked={draft.isClosed}
                      onChange={(event) => update(day, { isClosed: event.target.checked })}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

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
        disabled={isSaving || changedDays.length === 0}
        className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium disabled:opacity-50"
      >
        {isSaving ? 'Guardando...' : 'Guardar horarios'}
      </button>
    </form>
  );
};
