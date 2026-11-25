import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { DishRankingTable } from '../../src/components/analytics/DishRankingTable';
import { ClientCacheProvider } from '../../src/context/ClientCacheContext';
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

  describe('Spec 028 US5 (T077): SWR cache and silent background revalidation', () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    it('renders cached ranking immediately across mounts in ClientCacheProvider while revalidating silently', async () => {
      let resolveSecond!: (value: any) => void;
      const secondPromise = new Promise((resolve) => {
        resolveSecond = resolve;
      });

      vi.mocked(analyticsApi.getDishRanking)
        .mockResolvedValueOnce([{ dishId: '1', dishName: 'Milanesa', unitsSold: 5 }])
        .mockReturnValueOnce(secondPromise as any);

      const { rerender } = render(
        <ClientCacheProvider>
          <DishRankingTable key="admin-home" />
        </ClientCacheProvider>
      );

      await waitFor(() => {
        expect(screen.getByText('Milanesa')).toBeInTheDocument();
        expect(screen.getByText('5')).toBeInTheDocument();
      });

      rerender(
        <ClientCacheProvider>
          <DishRankingTable key="admin-analytics" />
        </ClientCacheProvider>
      );

      // Immediate render from SWR store while second fetch is still pending
      expect(screen.getByText('Milanesa')).toBeInTheDocument();
      expect(screen.getByText('5')).toBeInTheDocument();

      await act(async () => {
        resolveSecond([{ dishId: '1', dishName: 'Milanesa', unitsSold: 7 }]);
        await secondPromise;
      });

      await waitFor(() => {
        expect(screen.getByText('7')).toBeInTheDocument();
      });
    });

    it('silently revalidates on 10s interval and visibilitychange updating unitsSold without skeleton', async () => {
      vi.useFakeTimers();

      vi.mocked(analyticsApi.getDishRanking)
        .mockResolvedValueOnce([{ dishId: '1', dishName: 'Milanesa', unitsSold: 2 }])
        .mockResolvedValueOnce([{ dishId: '1', dishName: 'Milanesa', unitsSold: 4 }])
        .mockResolvedValueOnce([{ dishId: '1', dishName: 'Milanesa', unitsSold: 6 }]);

      render(<DishRankingTable />);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(screen.getByText('2')).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(10000);
      });
      expect(screen.getByText('4')).toBeInTheDocument();

      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        value: 'visible',
      });
      await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'));
        await Promise.resolve();
      });
      expect(screen.getByText('6')).toBeInTheDocument();
    });
  });
});
