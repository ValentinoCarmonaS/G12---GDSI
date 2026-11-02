import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { WeeklyScheduleManager } from '../components/operations/WeeklyScheduleManager';
import { operationsApi } from '../api/operationsApi';
import type { OperatingSchedule, UpdateScheduleRequest } from '../types/operations';

/**
 * Restaurant operation configuration. Hosts the weekly schedule (US1); the slot capacity (US2)
 * and payment method (US3) sections are added to this page by their own phases.
 */
export const AdminOperationsPage: React.FC = () => {
  const [schedules, setSchedules] = useState<OperatingSchedule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const fetchSchedules = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      setSchedules(await operationsApi.getSchedules());
    } catch {
      setLoadError('No se pudieron cargar los horarios. Intente nuevamente.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSchedules();
  }, [fetchSchedules]);

  const handleSaveSchedules = async (changes: UpdateScheduleRequest[]) => {
    await operationsApi.updateSchedules(changes);
    await fetchSchedules();
  };

  return (
    <WorkspaceLayout
      title="Operación del Restaurante"
      subtitle="Horarios comerciales, capacidad por franja y medios de pago"
    >
      <div className="space-y-6">
        <Link to="/admin" className="inline-flex items-center text-xs font-semibold text-blue-600 hover:text-blue-800">
          ← Volver al Panel de Administración
        </Link>

        <section className="bg-white p-6 rounded-lg shadow-sm border border-gray-200 space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Horarios comerciales</h2>
          {loadError && (
            <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
              {loadError}
            </div>
          )}
          {isLoading ? (
            <p className="text-sm text-gray-500">Cargando horarios...</p>
          ) : (
            !loadError && (
              <WeeklyScheduleManager schedules={schedules} onSave={handleSaveSchedules} onReload={fetchSchedules} />
            )
          )}
        </section>
      </div>
    </WorkspaceLayout>
  );
};
