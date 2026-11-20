import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { FinancialHealthChart } from '../../src/components/analytics/FinancialHealthChart';
import type { FinancialHealthResponse } from '../../src/types/analytics';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="recharts-container">{children}</div>
  ),
  BarChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="recharts-barchart">{children}</div>
  ),
  Bar: () => <div data-testid="recharts-bar" />,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
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

  it('renders Recharts bar chart by default and toggles to tabular view', () => {
    render(<FinancialHealthChart data={sampleData} />);

    expect(screen.getByTestId('recharts-container')).toBeInTheDocument();

    const toggleBtn = screen.getByRole('button', { name: /tabla|tabular/i });
    fireEvent.click(toggleBtn);

    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('renders empty state message when revenue and orderCount are 0', () => {
    render(
      <FinancialHealthChart
        data={{
          ...sampleData,
          revenue: 0,
          orderCount: 0,
        }}
      />
    );

    expect(screen.getByText(/sin pedidos/i)).toBeInTheDocument();
  });
});
