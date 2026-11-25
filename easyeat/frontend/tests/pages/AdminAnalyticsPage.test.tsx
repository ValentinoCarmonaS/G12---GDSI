import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminAnalyticsPage } from '../../src/pages/AdminAnalyticsPage';
import * as analyticsApi from '../../src/api/analyticsApi';

vi.mock('../../src/api/analyticsApi');

describe('AdminAnalyticsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(analyticsApi.getFinancialHealth).mockResolvedValue({
      granularity: 'day',
      revenue: 15000,
      orderCount: 12,
      periodStart: '2026-11-20T00:00:00-03:00',
      periodEnd: '2026-11-20T23:59:59-03:00',
    });
    vi.mocked(analyticsApi.getDishRanking).mockResolvedValue([
      {
        dishId: '11111111-1111-1111-1111-111111111111',
        dishName: 'Milanesa Napolitana',
        unitsSold: 18,
      },
    ]);
  });

  it('renders financial health panel, chart, and dish ranking table', async () => {
    render(
      <MemoryRouter>
        <AdminAnalyticsPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: /Métricas y Rendimiento/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Visualización de ingresos y pedidos/i)).toBeInTheDocument();
      expect(screen.getByText('Milanesa Napolitana')).toBeInTheDocument();
    });
  });

  it('syncs chart data when switching granularity in financial health panel', async () => {
    vi.mocked(analyticsApi.getFinancialHealth)
      .mockResolvedValueOnce({
        granularity: 'day',
        revenue: 15000,
        orderCount: 12,
        periodStart: '2026-11-20T00:00:00-03:00',
        periodEnd: '2026-11-20T23:59:59-03:00',
      })
      .mockResolvedValueOnce({
        granularity: 'week',
        revenue: 42000,
        orderCount: 30,
        periodStart: '2026-11-16T00:00:00-03:00',
        periodEnd: '2026-11-22T23:59:59-03:00',
      });

    render(
      <MemoryRouter>
        <AdminAnalyticsPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(analyticsApi.getFinancialHealth).toHaveBeenCalledTimes(1);
    });

    const financialTablist = screen.getByRole('tablist', { name: /período financiero/i });
    fireEvent.click(within(financialTablist).getByRole('tab', { name: /semana/i }));

    await waitFor(() => {
      expect(analyticsApi.getFinancialHealth).toHaveBeenCalledWith('week');
    });

    fireEvent.click(screen.getByRole('button', { name: /ver tabla/i }));

    await waitFor(() => {
      expect(screen.getByTestId('financial-kpi-revenue')).toHaveTextContent(/42\.000,00/);
      expect(screen.getAllByText('30').length).toBeGreaterThanOrEqual(2);
    });
  });
});
