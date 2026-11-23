import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { FinancialHealthResponse } from '../../types/analytics';
import { useRoleTheme } from '../../theme/useRoleTheme';
import { GlassCard } from '../admin/GlassCard';
import { Button, EmptyState } from '../primitives';

interface FinancialHealthChartProps {
  data: FinancialHealthResponse;
}

const fullCurrencyFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

function formatFullCurrency(value: number): string {
  return `$ ${fullCurrencyFormatter.format(value)}`;
}

export function formatCompactCurrency(value: number): string {
  if (!Number.isFinite(value)) return '$ 0';
  const sign = value < 0 ? '-' : '';
  const abs = Math.abs(value);

  const formatScaled = (scaled: number, suffix: string) => {
    const decimals = scaled >= 100 || Number.isInteger(scaled) ? 0 : 1;
    const formatted = scaled.toLocaleString('es-AR', {
      minimumFractionDigits: 0,
      maximumFractionDigits: decimals,
    });
    return `${sign}$ ${formatted}${suffix}`;
  };

  if (abs >= 1_000_000_000_000) {
    return formatScaled(abs / 1_000_000_000_000, 'T');
  }
  if (abs >= 1_000_000_000) {
    return formatScaled(abs / 1_000_000_000, 'B');
  }
  if (abs >= 1_000_000) {
    return formatScaled(abs / 1_000_000, 'M');
  }
  if (abs >= 1_000) {
    return formatScaled(abs / 1_000, 'k');
  }

  return `${sign}$ ${abs.toLocaleString('es-AR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })}`;
}

export function FinancialHealthChart({ data }: FinancialHealthChartProps) {
  const [viewMode, setViewMode] = useState<'chart' | 'table'>('chart');
  const { dataViz } = useRoleTheme();

  const isEmpty = data.revenue === 0 && data.orderCount === 0;
  const periodLabel =
    data.granularity === 'day' ? 'Hoy' : data.granularity === 'week' ? 'Semana' : 'Mes';

  const chartData = [
    {
      name: periodLabel,
      Ingresos: data.revenue,
      Pedidos: data.orderCount,
    },
  ];

  return (
    <GlassCard as="section" variant="elevated" padding="lg" radius="lg" aria-label="Visualización de ingresos y pedidos">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-ee-text">Visualización de ingresos y pedidos</h3>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => setViewMode((prev) => (prev === 'chart' ? 'table' : 'chart'))}
        >
          {viewMode === 'chart' ? 'Ver tabla' : 'Ver gráfico'}
        </Button>
      </div>

      {isEmpty ? (
        <EmptyState
          title="Sin pedidos concretados"
          description="No se registraron pedidos concretados en este período."
        />
      ) : viewMode === 'chart' ? (
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={dataViz.gridStroke} />
              <XAxis dataKey="name" stroke={dataViz.axisStroke} />
              <YAxis
                yAxisId="revenue"
                stroke={dataViz.axisStroke}
                tickFormatter={formatCompactCurrency}
                width={76}
              />
              <YAxis
                yAxisId="orders"
                orientation="right"
                stroke={dataViz.axisStroke}
                allowDecimals={false}
                width={44}
              />
              <Tooltip
                formatter={(value, name) => {
                  const numericValue = Number(value);
                  if (name === 'Ingresos') {
                    return [formatFullCurrency(numericValue), 'Ingresos'];
                  }
                  return [`${numericValue} pedidos`, 'Pedidos'];
                }}
                contentStyle={{
                  backgroundColor: dataViz.tooltip.bg,
                  borderColor: dataViz.tooltip.border,
                  color: dataViz.tooltip.fg,
                  borderRadius: '8px',
                }}
              />
              <Legend />
              <Bar
                yAxisId="revenue"
                dataKey="Ingresos"
                fill={dataViz.series[0]}
                radius={[6, 6, 0, 0]}
                maxBarSize={72}
              />
              <Bar
                yAxisId="orders"
                dataKey="Pedidos"
                fill={dataViz.series[1]}
                radius={[6, 6, 0, 0]}
                maxBarSize={72}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-ee-text">
            <thead>
              <tr className="border-b border-ee-border-subtle text-xs uppercase tracking-wider text-ee-text-muted">
                <th scope="col" className="py-2.5 pr-4 font-medium">Período</th>
                <th scope="col" className="py-2.5 pr-4 font-medium text-right">Ingresos</th>
                <th scope="col" className="py-2.5 font-medium text-right">Pedidos</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-ee-border-subtle/60">
                <td className="py-3 pr-4">{periodLabel}</td>
                <td className="py-3 pr-4 text-right font-ee-mono tabular-nums">
                  {formatFullCurrency(data.revenue)}
                </td>
                <td className="py-3 text-right font-ee-mono tabular-nums">
                  {data.orderCount}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </GlassCard>
  );
}
