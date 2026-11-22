import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { GlassCard } from '../components/admin/GlassCard';
import { LoadingSkeleton } from '../components/ui/LoadingSkeleton';
import { FeedbackState } from '../components/ui/FeedbackState';
import { WeeklyScheduleManager } from '../components/operations/WeeklyScheduleManager';
import { SlotCapacityGrid } from '../components/operations/SlotCapacityGrid';
import { PaymentMethodToggles } from '../components/operations/PaymentMethodToggles';
import { operationsApi } from '../api/operationsApi';
import type {
  AdminPaymentMethod,
  OperatingSchedule,
  PaymentMethod,
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
 * Restaurant operation configuration: the weekly schedule (US1), the slot capacity (US2) and the
 * online payment method switches (US3).
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

  const [paymentMethods, setPaymentMethods] = useState<AdminPaymentMethod[]>([]);
  const [isLoadingPayments, setIsLoadingPayments] = useState(true);
  const [paymentLoadError, setPaymentLoadError] = useState<string | null>(null);

  const fetchPaymentMethods = useCallback(async () => {
    setIsLoadingPayments(true);
    setPaymentLoadError(null);
    try {
      setPaymentMethods(await operationsApi.getAdminPaymentMethods());
    } catch {
      setPaymentLoadError('No se pudieron cargar los métodos de pago. Intente nuevamente.');
    } finally {
      setIsLoadingPayments(false);
    }
  }, []);

  useEffect(() => {
    fetchPaymentMethods();
  }, [fetchPaymentMethods]);

  const handleTogglePayment = async (method: PaymentMethod, enabled: boolean, version: number | null) => {
    await operationsApi.togglePaymentMethod(method, { isEnabled: enabled, expectedVersion: version });
    await fetchPaymentMethods();
  };

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
        <Link to="/admin" className="inline-flex items-center min-h-[44px] text-xs font-semibold text-[var(--ee-primary)] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] rounded-[var(--ee-radius-sm)]">
          ← Volver al Panel de Administración
        </Link>

        <GlassCard variant="elevated" padding="lg" className="space-y-4">
          <h2 className="text-lg font-semibold text-[var(--ee-text)]">Horarios comerciales</h2>
          {loadError && (
            <FeedbackState variant="error" title="Error al cargar horarios" description={loadError} onRetry={fetchSchedules} />
          )}
          {isLoading ? (
            <LoadingSkeleton lines={3} />
          ) : (
            !loadError && (
              <WeeklyScheduleManager schedules={schedules} onSave={handleSaveSchedules} onReload={fetchSchedules} />
            )
          )}
        </GlassCard>

        <GlassCard variant="elevated" padding="lg" className="space-y-4">
          <h2 className="text-lg font-semibold text-[var(--ee-text)]">Capacidad por franja</h2>
          {capacityLoadError && (
            <FeedbackState variant="error" title="Error al cargar capacidad" description={capacityLoadError} onRetry={fetchCapacities} />
          )}
          {isLoadingCapacities ? (
            <LoadingSkeleton lines={3} />
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
        </GlassCard>

        <GlassCard variant="elevated" padding="lg" className="space-y-4">
          <h2 className="text-lg font-semibold text-[var(--ee-text)]">Métodos de pago online</h2>
          {paymentLoadError && (
            <FeedbackState variant="error" title="Error al cargar métodos de pago" description={paymentLoadError} onRetry={fetchPaymentMethods} />
          )}
          {isLoadingPayments ? (
            <LoadingSkeleton lines={2} />
          ) : (
            !paymentLoadError && (
              <PaymentMethodToggles
                methods={paymentMethods}
                onToggle={handleTogglePayment}
                onReload={fetchPaymentMethods}
              />
            )
          )}
        </GlassCard>
      </div>
    </WorkspaceLayout>
  );
};
