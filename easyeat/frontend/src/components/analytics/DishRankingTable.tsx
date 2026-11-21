import { useEffect, useState } from 'react';
import { getDishRanking } from '../../api/analyticsApi';
import type { DishRankingGranularity, DishRankingItemResponse } from '../../types/analytics';

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

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    getDishRanking(granularity)
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
  }, [granularity]);

  return (
    <section className="rounded-xl border border-neutral-800 bg-neutral-950 p-6 text-neutral-100">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-lg font-semibold">Ranking de platos más vendidos</h2>
        <div role="tablist" aria-label="Período del ranking" className="flex flex-wrap gap-2">
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

      {loading && items.length === 0 ? (
        <p className="py-6 text-center text-sm text-neutral-400">Cargando ranking de platos...</p>
      ) : items.length === 0 && !error ? (
        <p className="rounded-lg border border-neutral-800 bg-neutral-900/40 py-8 text-center text-sm text-neutral-400">
          No hay ventas registradas para el período seleccionado.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-neutral-800 text-xs uppercase tracking-wider text-neutral-400">
                <th scope="col" className="py-3 pr-4 font-medium">
                  #
                </th>
                <th scope="col" className="py-3 pr-4 font-medium">
                  Plato
                </th>
                <th scope="col" className="py-3 text-right font-medium">
                  Unidades vendidas
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-800/70">
              {items.map((item, index) => (
                <tr key={item.dishId} className="hover:bg-neutral-900/50">
                  <td className="py-3 pr-4 text-neutral-400">{index + 1}</td>
                  <td className="py-3 pr-4 font-medium text-neutral-100">{item.dishName}</td>
                  <td className="py-3 text-right font-semibold text-amber-400">
                    {item.unitsSold}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
