import { useCallback, useEffect, useState } from 'react';
import { getDishRanking } from '../../api/analyticsApi';
import type { DishRankingGranularity, DishRankingItemResponse } from '../../types/analytics';
import { GlassCard } from '../admin/GlassCard';
import { EmptyState, FeedbackState, LoadingSkeleton } from '../primitives';

const GRANULARITY_TABS: { key: DishRankingGranularity; label: string }[] = [
  { key: 'all', label: 'Histórico global' },
  { key: 'day', label: 'Día' },
  { key: 'week', label: 'Semana' },
  { key: 'month', label: 'Mes' },
];

export function DishRankingTable() {
  const [granularity, setGranularity] = useState<DishRankingGranularity>('all');
  const [items, setItems] = useState<DishRankingItemResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRanking = useCallback((targetGranularity: DishRankingGranularity) => {
    let active = true;
    setLoading(true);
    setError(null);

    getDishRanking(targetGranularity)
      .then((res) => {
        if (active) {
          setItems(res);
        }
      })
      .catch(() => {
        if (active) {
          setError('No se pudo cargar el ranking de platos.');
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
    return fetchRanking(granularity);
  }, [granularity, fetchRanking]);

  return (
    <GlassCard as="section" variant="elevated" padding="lg" radius="lg" aria-label="Ranking de platos más vendidos">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-ee-text">Ranking de platos más vendidos</h2>
        <div
          role="tablist"
          aria-label="Período del ranking"
          className="flex flex-wrap gap-1 rounded-ee-md border border-ee-border-subtle bg-ee-surface-subtle p-1"
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
                className={`min-h-[36px] rounded-ee-sm px-3 py-1.5 text-xs font-medium transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
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
            title="Error al cargar el ranking"
            description={error}
            actionLabel="Reintentar"
            onAction={() => fetchRanking(granularity)}
          />
        </div>
      )}

      {loading && items.length === 0 ? (
        <LoadingSkeleton variant="card" count={3} />
      ) : items.length === 0 && !error ? (
        <EmptyState
          title="Sin ventas registradas"
          description="No hay ventas registradas para el período seleccionado."
        />
      ) : (
        <ul className="space-y-2.5" aria-label="Listado de platos">
          {items.map((item, index) => (
            <li key={item.dishId}>
              <GlassCard variant="subtle" padding="sm" radius="md" className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="flex items-center gap-3 min-w-0">
                  <span className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ee-primary/10 font-ee-mono tabular-nums text-xs font-semibold text-ee-primary">
                    {index + 1}
                  </span>
                  <span className="truncate text-sm font-medium text-ee-text">
                    {item.dishName}
                  </span>
                </div>
                <div className="shrink-0 text-right">
                  <span className="font-ee-mono tabular-nums text-sm font-bold text-ee-text">
                    {item.unitsSold}
                  </span>
                  <span className="ml-1 text-xs text-ee-text-muted">uds.</span>
                </div>
              </GlassCard>
            </li>
          ))}
        </ul>
      )}
    </GlassCard>
  );
}
