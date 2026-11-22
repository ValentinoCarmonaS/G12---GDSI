import { useCallback, useEffect, useRef, useState } from 'react';
import { getFinancialHealth } from '../../api/analyticsApi';
import type { FinancialHealthGranularity, FinancialHealthResponse } from '../../types/analytics';
import { GlassCard } from '../admin/GlassCard';
import { FeedbackState, LoadingSkeleton } from '../primitives';

interface FinancialHealthPanelProps {
  onDataChange?: (
    data: FinancialHealthResponse | null,
    granularity: FinancialHealthGranularity
  ) => void;
}

const GRANULARITY_TABS: { key: FinancialHealthGranularity; label: string }[] = [
  { key: 'day', label: 'Día' },
  { key: 'week', label: 'Semana' },
  { key: 'month', label: 'Mes' },
];

const currencyFormatter = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function FinancialHealthPanel({ onDataChange }: FinancialHealthPanelProps = {}) {
  const [granularity, setGranularity] = useState<FinancialHealthGranularity>('day');
  const [data, setData] = useState<FinancialHealthResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const onDataChangeRef = useRef(onDataChange);
  onDataChangeRef.current = onDataChange;

  const fetchHealth = useCallback((targetGranularity: FinancialHealthGranularity) => {
    let active = true;
    setLoading(true);
    setError(null);
    setData(null);
    onDataChangeRef.current?.(null, targetGranularity);

    getFinancialHealth(targetGranularity)
      .then((res) => {
        if (active) {
          setData(res);
          onDataChangeRef.current?.(res, targetGranularity);
        }
      })
      .catch(() => {
        if (active) {
          setError('No se pudieron cargar las métricas financieras.');
          onDataChangeRef.current?.(null, targetGranularity);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    return fetchHealth(granularity);
  }, [granularity, fetchHealth]);

  const revenueDisplay = currencyFormatter.format(data?.revenue ?? 0);
  const orderCountDisplay = data?.orderCount ?? 0;

  return (
    <GlassCard as="section" variant="elevated" padding="lg" radius="lg" aria-label="Salud financiera del local">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-ee-text">Salud financiera del local</h2>
          <p className="text-xs text-ee-text-muted mt-0.5">
            Ingresos acumulados y pedidos concretados por período
          </p>
        </div>
        <div
          role="tablist"
          aria-label="Período financiero"
          className="inline-flex gap-1 rounded-ee-md border border-ee-border-subtle bg-ee-surface-subtle p-1"
        >
          {GRANULARITY_TABS.map((tab) => {
            const isSelected = granularity === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={isSelected}
                onClick={() => setGranularity(tab.key)}
                className={`min-h-[36px] rounded-ee-sm px-3.5 py-1.5 text-sm font-medium transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
                  isSelected
                    ? 'bg-ee-primary text-white shadow-sm'
                    : 'text-ee-text-muted hover:text-ee-text hover:bg-white/60'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {error && (
        <div className="mb-4">
          <FeedbackState
            variant="danger"
            title="Error al cargar métricas"
            description={error}
            actionLabel="Reintentar"
            onAction={() => fetchHealth(granularity)}
          />
        </div>
      )}

      {loading && !data ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <LoadingSkeleton variant="card" count={2} />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <GlassCard variant="subtle" padding="md" radius="md">
            <p className="text-xs font-medium uppercase tracking-wider text-ee-text-muted">
              Ingresos totales
            </p>
            <p
              data-testid="financial-kpi-revenue"
              className="mt-2 font-ee-mono tabular-nums text-2xl font-bold text-ee-text"
            >
              {revenueDisplay}
            </p>
          </GlassCard>

          <GlassCard variant="subtle" padding="md" radius="md">
            <p className="text-xs font-medium uppercase tracking-wider text-ee-text-muted">
              Pedidos concretados
            </p>
            <p
              data-testid="financial-kpi-orders"
              className="mt-2 font-ee-mono tabular-nums text-2xl font-bold text-ee-text"
            >
              {orderCountDisplay}
            </p>
          </GlassCard>
        </div>
      )}
    </GlassCard>
  );
}
