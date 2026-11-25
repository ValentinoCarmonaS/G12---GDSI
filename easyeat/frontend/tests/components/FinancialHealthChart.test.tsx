import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import {
  FinancialHealthChart,
  formatCompactCurrency,
} from '../../src/components/analytics/FinancialHealthChart';
import { ThemeScopeProvider } from '../../src/theme/ThemeScopeProvider';
import type { FinancialHealthResponse } from '../../src/types/analytics';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="recharts-container">{children}</div>
  ),
  BarChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="recharts-barchart">{children}</div>
  ),
  Bar: ({ yAxisId, dataKey }: { yAxisId?: string; dataKey?: string }) => (
    <div data-testid={`recharts-bar-${dataKey}`} data-yaxis-id={yAxisId} />
  ),
  XAxis: () => null,
  YAxis: ({ yAxisId, orientation }: { yAxisId?: string; orientation?: string }) => (
    <div data-testid={`recharts-yaxis-${yAxisId}`} data-orientation={orientation ?? 'left'} />
  ),
  Tooltip: () => null,
  Legend: () => <div data-testid="recharts-legend" />,
  CartesianGrid: () => null,
}));

describe('FinancialHealthChart', () => {
  const sampleData: FinancialHealthResponse = {
    granularity: 'day',
    revenue: 12500,
    orderCount: 10,
    periodStart: '2026-11-20T00:00:00-03:00',
    periodEnd: '2026-11-20T23:59:59-03:00',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Recharts bar chart with dual Y axes and toggles to tabular view', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <ThemeScopeProvider>
          <FinancialHealthChart data={sampleData} />
        </ThemeScopeProvider>
      </MemoryRouter>
    );

    expect(screen.getByTestId('recharts-container')).toBeInTheDocument();
    expect(screen.getByTestId('recharts-yaxis-revenue')).toHaveAttribute('data-orientation', 'left');
    expect(screen.getByTestId('recharts-yaxis-orders')).toHaveAttribute('data-orientation', 'right');
    expect(screen.getByTestId('recharts-bar-Ingresos')).toHaveAttribute('data-yaxis-id', 'revenue');
    expect(screen.getByTestId('recharts-bar-Pedidos')).toHaveAttribute('data-yaxis-id', 'orders');

    const toggleBtn = screen.getByRole('button', { name: /tabla|tabular/i });
    fireEvent.click(toggleBtn);

    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('formats Y-axis currency values adaptively across scales', () => {
    expect(formatCompactCurrency(0)).toBe('$ 0');
    expect(formatCompactCurrency(850)).toBe('$ 850');
    expect(formatCompactCurrency(12500)).toBe('$ 12,5k');
    expect(formatCompactCurrency(14691728.86)).toBe('$ 14,7M');
    expect(formatCompactCurrency(1200000000)).toBe('$ 1,2B');
    expect(formatCompactCurrency(2000000000000)).toBe('$ 2T');
  });

  it('renders empty state message when revenue and orderCount are 0', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <ThemeScopeProvider>
          <FinancialHealthChart
            data={{
              ...sampleData,
              revenue: 0,
              orderCount: 0,
            }}
          />
        </ThemeScopeProvider>
      </MemoryRouter>
    );

    expect(screen.getByText(/sin pedidos/i)).toBeInTheDocument();
  });
});
