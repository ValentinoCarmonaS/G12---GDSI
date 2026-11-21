import { render, screen, waitFor } from '@testing-library/react';
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
      averageTicket: 1250,
    });
    vi.mocked(analyticsApi.getDishRanking).mockResolvedValue([
      {
        dishId: 1,
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
});
