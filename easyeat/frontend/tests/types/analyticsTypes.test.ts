import { describe, it, expect } from 'vitest';
import type { StaffStatusUpdateRequest } from '../../src/types/staff';
import type {
  FinancialHealthGranularity,
  FinancialHealthResponse,
  DishRankingGranularity,
  DishRankingItemResponse,
} from '../../src/types/analytics';

describe('Spec 023 - Staff & Analytics Types (T002)', () => {
  it('defines StaffStatusUpdateRequest with StaffAccountStatus', () => {
    const req: StaffStatusUpdateRequest = { status: 'SUSPENDED' };
    expect(req.status).toBe('SUSPENDED');
  });

  it('defines FinancialHealthGranularity and FinancialHealthResponse', () => {
    const granularity: FinancialHealthGranularity = 'day';
    const res: FinancialHealthResponse = {
      granularity,
      revenue: 12500.5,
      orderCount: 15,
      periodStart: '2026-11-20T00:00:00-03:00',
      periodEnd: '2026-11-20T23:59:59-03:00',
    };
    expect(res.granularity).toBe('day');
    expect(res.revenue).toBe(12500.5);
    expect(res.orderCount).toBe(15);
  });

  it('defines DishRankingGranularity and DishRankingItemResponse', () => {
    const granularity: DishRankingGranularity = 'all';
    const item: DishRankingItemResponse = {
      dishId: 'dish-1',
      dishName: 'Milanesa Napolitana',
      unitsSold: 42,
    };
    expect(granularity).toBe('all');
    expect(item.dishName).toBe('Milanesa Napolitana');
    expect(item.unitsSold).toBe(42);
  });
});
