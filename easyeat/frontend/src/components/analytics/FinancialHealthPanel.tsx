import { useEffect, useState } from 'react';
import { getFinancialHealth } from '../../api/analyticsApi';
import type { FinancialHealthGranularity, FinancialHealthResponse } from '../../types/analytics';

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

export function FinancialHealthPanel() {
  const [granularity, setGranularity] = useState<FinancialHealthGranularity>('day');
  const [data, setData] = useState<FinancialHealthResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    getFinancialHealth(granularity)
      .then((res) => {
        if (active) {
          setData(res);
        }
      })
      .catch(() => {
        if (active) {
          setError('No se pudieron cargar las métricas financieras.');
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
  }, [granularity]);

  const revenueDisplay = currencyFormatter.format(data?.revenue ?? 0);
  const orderCountDisplay = data?.orderCount ?? 0;

  return (
    <section className="rounded-xl border border-neutral-800 bg-neutral-950 p-6 text-neutral-100">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-lg font-semibold">Salud financiera del local</h2>
        <div role="tablist" aria-label="Período financiero" className="flex gap-2">
          {GRANULARITY_TABS.map((tab) => {
            const isSelected = granularity === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={isSelected}
                onClick={() => setGranularity(tab.key)}
                className={`rounded-lg px-4 py-1.5 text-sm font-medium transition-colors ${
                  isSelected
                    ? 'bg-amber-500 text-neutral-950'
                    : 'bg-neutral-900 text-neutral-300 hover:bg-neutral-800'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {error && (
        <p role="alert" className="mb-4 rounded-lg bg-red-950/50 p-3 text-sm text-red-300">
          {error}
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-neutral-800 bg-neutral-900/60 p-4">
          <p className="text-xs uppercase tracking-wider text-neutral-400">Ingresos totales</p>
          <p className="mt-2 text-2xl font-bold text-amber-400">
            {loading && !data ? '...' : revenueDisplay}
          </p>
        </div>
        <div className="rounded-lg border border-neutral-800 bg-neutral-900/60 p-4">
          <p className="text-xs uppercase tracking-wider text-neutral-400">Pedidos concretados</p>
          <p className="mt-2 text-2xl font-bold text-neutral-100">
            {loading && !data ? '...' : orderCountDisplay}
          </p>
        </div>
      </div>
    </section>
  );
}
