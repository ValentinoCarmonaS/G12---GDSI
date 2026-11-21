import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getFinancialHealth } from '../api/analyticsApi';
import { DishRankingTable } from '../components/analytics/DishRankingTable';
import { FinancialHealthChart } from '../components/analytics/FinancialHealthChart';
import { FinancialHealthPanel } from '../components/analytics/FinancialHealthPanel';
import type { FinancialHealthResponse } from '../types/analytics';

export function AdminAnalyticsPage() {
  const [chartData, setChartData] = useState<FinancialHealthResponse>({
    granularity: 'day',
    revenue: 0,
    orderCount: 0,
    averageTicket: 0,
  });

  useEffect(() => {
    let active = true;
    getFinancialHealth('day')
      .then((res) => {
        if (active) {
          setChartData(res);
        }
      })
      .catch(() => {
        // El panel principal maneja y muestra el estado de error de métricas financieras.
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="mx-auto max-w-6xl space-y-6 px-4 py-8 text-neutral-100">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-neutral-800 pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Métricas y Rendimiento</h1>
          <p className="text-sm text-neutral-400">
            Salud financiera operativa y ranking de platos más vendidos.
          </p>
        </div>
        <Link
          to="/admin"
          className="rounded-lg border border-neutral-700 bg-neutral-900 px-3 py-1.5 text-sm font-medium text-neutral-200 hover:bg-neutral-800"
        >
          Volver al panel
        </Link>
      </header>

      <FinancialHealthPanel />
      <FinancialHealthChart data={chartData} />
      <DishRankingTable />
    </main>
  );
}
