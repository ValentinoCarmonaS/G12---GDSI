import { useCallback, useEffect, useState } from 'react';
import { getDishRanking } from '../../api/analyticsApi';
import { useClientCache } from '../../context/ClientCacheContext';
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
  const cache = useClientCache();
  const [granularity, setGranularity] = useState<DishRankingGranularity>('all');
  const initialCached = cache.get<DishRankingItemResponse[]>('analytics:dish-ranking:all')?.data;
  const [items, setItems] = useState<DishRankingItemResponse[]>(initialCached ?? []);
  const [loading, setLoading] = useState<boolean>(initialCached === undefined);
  const [error, setError] = useState<string | null>(null);

  const fetchRanking = useCallback(
    (targetGranularity: DishRankingGranularity, options?: { silent?: boolean }) => {
      const silent = options?.silent ?? false;
      const cacheKey = `analytics:dish-ranking:${targetGranularity}`;
      const cached = cache.get<DishRankingItemResponse[]>(cacheKey)?.data;
      let active = true;

      if (!silent) {
        setError(null);
        if (cached !== undefined) {
          setItems(cached);
          setLoading(false);
        } else {
          setItems([]);
          setLoading(true);
        }
      }

      cache
        .fetchDeduped(cacheKey, () => getDishRanking(targetGranularity))
        .then((res) => {
          if (active) {
            setItems(res);
            setError(null);
          }
        })
        .catch(() => {
          if (active && !silent && cached === undefined) {
            setError('No se pudo cargar el ranking de platos.');
          }
        })
        .finally(() => {
          if (active && !silent) {
            setLoading(false);
          }
        });

      return () => {
        active = false;
      };
    },
    [cache]
  );

  useEffect(() => {
    return fetchRanking(granularity, { silent: false });
  }, [granularity, fetchRanking]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (document.visibilityState === 'visible') {
        fetchRanking(granularity, { silent: true });
      }
    }, 10000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchRanking(granularity, { silent: true });
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
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
            message={error}
            actionLabel="Reintentar"
            onAction={() => fetchRanking(granularity)}
          />
        </div>
      )}

      {loading && items.length === 0 ? (
        <div className="space-y-2.5">
          <LoadingSkeleton variant="block" />
          <LoadingSkeleton variant="block" announce={false} />
          <LoadingSkeleton variant="block" announce={false} />
        </div>
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
