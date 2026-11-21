export type FinancialHealthGranularity = 'day' | 'week' | 'month';

export interface FinancialHealthResponse {
  granularity: FinancialHealthGranularity;
  revenue: number;
  orderCount: number;
  periodStart: string;
  periodEnd: string;
}

export type DishRankingGranularity = 'all' | 'day' | 'week' | 'month';

export interface DishRankingItemResponse {
  dishId: string;
  dishName: string;
  unitsSold: number;
}
