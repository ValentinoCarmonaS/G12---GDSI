import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { FinancialHealthPanel } from '../../src/components/analytics/FinancialHealthPanel';
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
});
