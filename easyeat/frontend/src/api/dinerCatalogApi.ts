import { apiClient } from './apiClient';
import { EstablishmentSummary, CatalogResponse } from '@/types/dinerCatalog';

export const dinerCatalogApi = {
  /**
   * Search active establishments by commercial name autocomplete.
   * Requires at least 3 non-whitespace characters (COM-1.5.1 CA 1, FR-001).
   */
  searchEstablishments: async (query: string): Promise<EstablishmentSummary[]> => {
    const trimmed = query ? query.trim() : '';
    if (trimmed.length < 3) {
      return [];
    }
    const response = await apiClient.get<EstablishmentSummary[]>('/establishments/search', {
      params: { q: trimmed },
    });
    return response.data;
  },

  /**
   * Retrieve live categorized menu catalog for selected establishment (COM-4.1.1, FR-007, FR-026).
   * Path: GET /establishments/{tenantId}/catalog
   */
  getEstablishmentCatalog: async (tenantId: string): Promise<CatalogResponse> => {
    const response = await apiClient.get<CatalogResponse>(`/establishments/${tenantId}/catalog`);
    return response.data;
  },
};
