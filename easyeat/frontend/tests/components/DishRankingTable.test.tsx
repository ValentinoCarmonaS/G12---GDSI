import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { DishRankingTable } from '../../src/components/analytics/DishRankingTable';
import * as analyticsApi from '../../src/api/analyticsApi';

vi.mock('../../src/api/analyticsApi', () => ({
  getDishRanking: vi.fn(),
}));

describe('DishRankingTable (T032)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders dish ranking table with dish name and units sold columns in descending order with A-Z tie-breaker', async () => {
    vi.mocked(analyticsApi.getDishRanking).mockResolvedValueOnce([
      { dishId: '1', dishName: 'Empanada de Carne Suave', unitsSold: 85 },
      { dishId: '2', dishName: 'Hamburguesa Clasica', unitsSold: 42 },
      { dishId: '3', dishName: 'Pizza Muzzarella', unitsSold: 42 },
    ]);

    render(<DishRankingTable />);

    await waitFor(() => {
      expect(screen.getByText('Empanada de Carne Suave')).toBeInTheDocument();
    });

    expect(analyticsApi.getDishRanking).toHaveBeenCalledWith('all');
    expect(screen.getByText('85')).toBeInTheDocument();
    expect(screen.getByText('Hamburguesa Clasica')).toBeInTheDocument();
    expect(screen.getByText('Pizza Muzzarella')).toBeInTheDocument();
    expect(screen.getAllByText('42')).toHaveLength(2);
  });

  it('renders neutral empty state when no dishes were sold', async () => {
    vi.mocked(analyticsApi.getDishRanking).mockResolvedValueOnce([]);

    render(<DishRankingTable />);

    await waitFor(() => {
      expect(
        screen.getByText(/no hay ventas registradas/i)
      ).toBeInTheDocument();
    });
  });

  it('allows switching granularity between all, day, week, and month', async () => {
    vi.mocked(analyticsApi.getDishRanking)
      .mockResolvedValueOnce([
        { dishId: '1', dishName: 'Empanada de Carne Suave', unitsSold: 85 },
      ])
      .mockResolvedValueOnce([
        { dishId: '2', dishName: 'Pizza Muzzarella', unitsSold: 10 },
      ]);

    render(<DishRankingTable />);

    await waitFor(() => {
      expect(screen.getByText('Empanada de Carne Suave')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('tab', { name: 'Día' }));

    await waitFor(() => {
      expect(analyticsApi.getDishRanking).toHaveBeenCalledWith('day');
      expect(screen.getByText('Pizza Muzzarella')).toBeInTheDocument();
    });
  });
});
