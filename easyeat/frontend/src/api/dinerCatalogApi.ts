import { apiClient } from './apiClient';
import { EstablishmentSummary } from '@/types/dinerCatalog';

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
};
