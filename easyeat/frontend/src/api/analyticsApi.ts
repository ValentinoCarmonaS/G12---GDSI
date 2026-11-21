import { apiClient } from './apiClient';
import type {
  DishRankingGranularity,
  DishRankingItemResponse,
  FinancialHealthGranularity,
  FinancialHealthResponse,
} from '../types/analytics';

export async function getFinancialHealth(
  granularity: FinancialHealthGranularity
): Promise<FinancialHealthResponse> {
  const response = await apiClient.get<FinancialHealthResponse>(
    '/admin/analytics/financial-health',
    {
      params: { granularity },
      headers: { 'Cache-Control': 'no-cache' },
    }
  );
  return response.data;
}

export async function getDishRanking(
  granularity: DishRankingGranularity
): Promise<DishRankingItemResponse[]> {
  const response = await apiClient.get<DishRankingItemResponse[]>(
    '/admin/analytics/dish-ranking',
    {
      params: { granularity },
      headers: { 'Cache-Control': 'no-cache' },
    }
  );
  return response.data;
}
