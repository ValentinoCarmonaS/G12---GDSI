import { apiClient } from './apiClient';
import { EnrichedDish } from '../types/catalog';
import { CartComplianceRequest, CartComplianceResponse } from '../types/cart';

export const catalogApi = {
  getEstablishmentDishes: async (tenantId: string): Promise<EnrichedDish[]> => {
    const response = await apiClient.get<EnrichedDish[]>(`/establishments/${tenantId}/dishes`);
    return response.data;
  },

  evaluateCartCompliance: async (request: CartComplianceRequest): Promise<CartComplianceResponse> => {
    const response = await apiClient.post<CartComplianceResponse>('/cart/evaluate-compliance', request);
    return response.data;
  },
};

export default catalogApi;
