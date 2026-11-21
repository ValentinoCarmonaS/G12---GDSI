import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '../../src/api/apiClient';
import { getFinancialHealth, getDishRanking } from '../../src/api/analyticsApi';

vi.mock('../../src/api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('Spec 023 - analyticsApi (T003)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls GET /admin/analytics/financial-health with granularity and Cache-Control: no-cache', async () => {
    const mockData = {
      granularity: 'day',
      revenue: 5000,
      orderCount: 10,
      periodStart: '2026-11-20T00:00:00-03:00',
      periodEnd: '2026-11-20T23:59:59-03:00',
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockData });

    const result = await getFinancialHealth('day');

    expect(apiClient.get).toHaveBeenCalledWith('/admin/analytics/financial-health', {
      params: { granularity: 'day' },
      headers: { 'Cache-Control': 'no-cache' },
    });
    expect(result).toEqual(mockData);
  });

  it('calls GET /admin/analytics/dish-ranking with granularity and Cache-Control: no-cache', async () => {
    const mockData = [
      { dishId: 'd1', dishName: 'Pizza Muzza', unitsSold: 25 },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockData });

    const result = await getDishRanking('week');

    expect(apiClient.get).toHaveBeenCalledWith('/admin/analytics/dish-ranking', {
      params: { granularity: 'week' },
      headers: { 'Cache-Control': 'no-cache' },
    });
    expect(result).toEqual(mockData);
  });
});
