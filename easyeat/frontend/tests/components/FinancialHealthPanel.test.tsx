import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { FinancialHealthPanel } from '../../src/components/analytics/FinancialHealthPanel';
import { ClientCacheProvider } from '../../src/context/ClientCacheContext';
import * as analyticsApi from '../../src/api/analyticsApi';

vi.mock('../../src/api/analyticsApi');

describe('FinancialHealthPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders formatted revenue and order count for default day granularity', async () => {
    vi.mocked(analyticsApi.getFinancialHealth).mockResolvedValueOnce({
      granularity: 'day',
      revenue: 24500.5,
      orderCount: 14,
      periodStart: '2026-11-20T00:00:00-03:00',
      periodEnd: '2026-11-20T23:59:59.999999999-03:00',
    });

    render(<FinancialHealthPanel />);

    await waitFor(() => {
      expect(screen.getByText(/24\.500,50|24,500\.50/)).toBeInTheDocument();
      expect(screen.getByText('14')).toBeInTheDocument();
    });
  });

  it('switches granularity reactively when clicking week and month tabs', async () => {
    vi.mocked(analyticsApi.getFinancialHealth)
      .mockResolvedValueOnce({
        granularity: 'day',
        revenue: 100,
        orderCount: 1,
        periodStart: '2026-11-20T00:00:00-03:00',
        periodEnd: '2026-11-20T23:59:59-03:00',
      })
      .mockResolvedValueOnce({
        granularity: 'week',
        revenue: 750,
        orderCount: 5,
        periodStart: '2026-11-16T00:00:00-03:00',
        periodEnd: '2026-11-22T23:59:59-03:00',
      });

    render(<FinancialHealthPanel />);
    await waitFor(() => expect(screen.getByText('1')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('tab', { name: /semana/i }));

    await waitFor(() => {
      expect(analyticsApi.getFinancialHealth).toHaveBeenCalledWith('week');
      expect(screen.getByText('5')).toBeInTheDocument();
    });
  });

  it('renders empty state $0,00 and 0 when no orders exist', async () => {
    vi.mocked(analyticsApi.getFinancialHealth).mockResolvedValueOnce({
      granularity: 'day',
      revenue: 0,
      orderCount: 0,
      periodStart: '2026-11-20T00:00:00-03:00',
      periodEnd: '2026-11-20T23:59:59-03:00',
    });

    render(<FinancialHealthPanel />);

    await waitFor(() => {
      expect(screen.getByText(/0,00|0\.00/)).toBeInTheDocument();
      expect(screen.getByText('0')).toBeInTheDocument();
    });
  });

  describe('Spec 028 US5 (T077): SWR cache and silent background revalidation', () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    it('renders cached metrics immediately across mounts in ClientCacheProvider while revalidating silently', async () => {
      let resolveSecond!: (value: any) => void;
      const secondPromise = new Promise((resolve) => {
        resolveSecond = resolve;
      });

      vi.mocked(analyticsApi.getFinancialHealth)
        .mockResolvedValueOnce({
          granularity: 'day',
          revenue: 12000,
          orderCount: 3,
          periodStart: '2026-11-25T00:00:00-03:00',
          periodEnd: '2026-11-25T23:59:59-03:00',
        })
        .mockReturnValueOnce(secondPromise as any);

      const { rerender } = render(
        <ClientCacheProvider>
          <FinancialHealthPanel key="admin-home" />
        </ClientCacheProvider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('financial-kpi-orders')).toHaveTextContent('3');
      });

      // Remount inside the same ClientCacheProvider (simulates /admin -> /admin/analytics)
      rerender(
        <ClientCacheProvider>
          <FinancialHealthPanel key="admin-analytics" />
        </ClientCacheProvider>
      );

      // Immediate render from SWR store without skeleton while second fetch is still pending
      expect(screen.getByTestId('financial-kpi-orders')).toHaveTextContent('3');

      await act(async () => {
        resolveSecond({
          granularity: 'day',
          revenue: 16500,
          orderCount: 4,
          periodStart: '2026-11-25T00:00:00-03:00',
          periodEnd: '2026-11-25T23:59:59-03:00',
        });
        await secondPromise;
      });

      await waitFor(() => {
        expect(screen.getByTestId('financial-kpi-orders')).toHaveTextContent('4');
      });
    });

    it('silently revalidates on 10s interval and visibilitychange without clearing data or showing skeletons', async () => {
      vi.useFakeTimers();
      const onDataChange = vi.fn();

      vi.mocked(analyticsApi.getFinancialHealth)
        .mockResolvedValueOnce({
          granularity: 'day',
          revenue: 4500,
          orderCount: 1,
          periodStart: '2026-11-25T00:00:00-03:00',
          periodEnd: '2026-11-25T23:59:59-03:00',
        })
        .mockResolvedValueOnce({
          granularity: 'day',
          revenue: 9000,
          orderCount: 2,
          periodStart: '2026-11-25T00:00:00-03:00',
          periodEnd: '2026-11-25T23:59:59-03:00',
        })
        .mockResolvedValueOnce({
          granularity: 'day',
          revenue: 13500,
          orderCount: 3,
          periodStart: '2026-11-25T00:00:00-03:00',
          periodEnd: '2026-11-25T23:59:59-03:00',
        });

      render(<FinancialHealthPanel onDataChange={onDataChange} />);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(screen.getByTestId('financial-kpi-orders')).toHaveTextContent('1');
      onDataChange.mockClear();

      // 10s silent background revalidation
      await act(async () => {
        await vi.advanceTimersByTimeAsync(10000);
      });
      expect(screen.getByTestId('financial-kpi-orders')).toHaveTextContent('2');
      // Must NOT have cleared data to null during silent revalidation
      expect(onDataChange).not.toHaveBeenCalledWith(null, 'day');

      // visibilitychange silent revalidation
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        value: 'visible',
      });
      await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'));
        await Promise.resolve();
      });
      expect(screen.getByTestId('financial-kpi-orders')).toHaveTextContent('3');
      expect(onDataChange).not.toHaveBeenCalledWith(null, 'day');
    });
  });
});
