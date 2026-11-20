import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { FinancialHealthResponse } from '../../types/analytics';

interface FinancialHealthChartProps {
  data: FinancialHealthResponse;
}

export function FinancialHealthChart({ data }: FinancialHealthChartProps) {
  const [viewMode, setViewMode] = useState<'chart' | 'table'>('chart');

  const isEmpty = data.revenue === 0 && data.orderCount === 0;

  const chartData = [
    {
      name: data.granularity === 'day' ? 'Hoy' : data.granularity === 'week' ? 'Semana' : 'Mes',
      Ingresos: data.revenue,
      Pedidos: data.orderCount,
    },
  ];

  return (
    <section className="rounded-xl border border-neutral-800 bg-neutral-950 p-6 text-neutral-100">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold">Visualización de ingresos y pedidos</h3>
        <button
          type="button"
          onClick={() => setViewMode((prev) => (prev === 'chart' ? 'table' : 'chart'))}
          className="rounded-lg border border-neutral-700 bg-neutral-900 px-3 py-1.5 text-xs font-medium text-neutral-200 hover:bg-neutral-800"
        >
          {viewMode === 'chart' ? 'Ver tabla' : 'Ver gráfico'}
        </button>
      </div>

      {isEmpty ? (
        <p className="py-8 text-center text-sm text-neutral-400">
          Sin pedidos concretados en este período.
        </p>
      ) : viewMode === 'chart' ? (
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#262626" />
              <XAxis dataKey="name" stroke="#a3a3a3" />
              <YAxis stroke="#a3a3a3" />
              <Tooltip />
              <Bar dataKey="Ingresos" fill="#c9a96e" radius={[4, 4, 0, 0]} />
              <Bar dataKey="Pedidos" fill="#525252" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-neutral-800 text-neutral-400">
              <th className="py-2">Período</th>
              <th className="py-2">Ingresos</th>
              <th className="py-2">Pedidos</th>
            </tr>
          </thead>
          <tbody>
            <tr className="border-b border-neutral-900">
              <td className="py-2 capitalize">{data.granularity}</td>
              <td className="py-2">{data.revenue.toFixed(2)}</td>
              <td className="py-2">{data.orderCount}</td>
            </tr>
          </tbody>
        </table>
      )}
    </section>
  );
}
