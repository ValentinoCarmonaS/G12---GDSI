import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { WeeklyScheduleManager } from '../components/operations/WeeklyScheduleManager';
import { SlotCapacityGrid } from '../components/operations/SlotCapacityGrid';
import { operationsApi } from '../api/operationsApi';
import type {
  OperatingSchedule,
  SlotCapacity,
  UpdateScheduleRequest,
  UpdateSlotCapacityRequest,
} from '../types/operations';

const todayAsIsoDate = (): string => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
};

/**
 * Restaurant operation configuration. Hosts the weekly schedule (US1) and the slot capacity
 * (US2); the payment method section (US3) is added to this page by its own phase.
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

  const [capacityDate, setCapacityDate] = useState(todayAsIsoDate);
  const [capacities, setCapacities] = useState<SlotCapacity[]>([]);
  const [isLoadingCapacities, setIsLoadingCapacities] = useState(true);
  const [capacityLoadError, setCapacityLoadError] = useState<string | null>(null);

  const fetchCapacities = useCallback(async () => {
    setIsLoadingCapacities(true);
    setCapacityLoadError(null);
    try {
      setCapacities(await operationsApi.getSlotCapacities(capacityDate));
    } catch {
      setCapacityLoadError('No se pudo cargar la capacidad por franja. Intente nuevamente.');
    } finally {
      setIsLoadingCapacities(false);
    }
  }, [capacityDate]);

  useEffect(() => {
    fetchCapacities();
  }, [fetchCapacities]);

  const handleSaveCapacities = async (changes: UpdateSlotCapacityRequest[]) => {
    await operationsApi.updateSlotCapacities(changes);
    await fetchCapacities();
  };

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

        <section className="bg-white p-6 rounded-lg shadow-sm border border-gray-200 space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Capacidad por franja</h2>
          {capacityLoadError && (
            <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
              {capacityLoadError}
            </div>
          )}
          {isLoadingCapacities ? (
            <p className="text-sm text-gray-500">Cargando capacidad...</p>
          ) : (
            !capacityLoadError && (
              <SlotCapacityGrid
                date={capacityDate}
                slots={capacities}
                onDateChange={setCapacityDate}
                onSave={handleSaveCapacities}
                loadDay={operationsApi.getSlotCapacities}
                onReload={fetchCapacities}
              />
            )
          )}
        </section>
      </div>
    </WorkspaceLayout>
  );
};
