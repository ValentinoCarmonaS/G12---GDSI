import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { DishRankingTable } from '../components/analytics/DishRankingTable';
import { FinancialHealthChart } from '../components/analytics/FinancialHealthChart';
import { FinancialHealthPanel } from '../components/analytics/FinancialHealthPanel';
import type { FinancialHealthGranularity, FinancialHealthResponse } from '../types/analytics';

function createEmptyFinancialHealth(
  granularity: FinancialHealthGranularity
): FinancialHealthResponse {
  return {
    granularity,
    revenue: 0,
    orderCount: 0,
    periodStart: '',
    periodEnd: '',
  };
}

export function AdminAnalyticsPage() {
  const [chartData, setChartData] = useState<FinancialHealthResponse>(() =>
    createEmptyFinancialHealth('day')
  );

  const handleFinancialDataChange = useCallback(
    (data: FinancialHealthResponse | null, granularity: FinancialHealthGranularity) => {
      setChartData(data ?? createEmptyFinancialHealth(granularity));
    },
    []
  );

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

      <FinancialHealthPanel onDataChange={handleFinancialDataChange} />
      <FinancialHealthChart data={chartData} />
      <DishRankingTable />
    </main>
  );
}
