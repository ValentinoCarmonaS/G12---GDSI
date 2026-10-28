import { apiClient } from './apiClient';
import { EstablishmentSummary, CatalogResponse, AvailabilityMap } from '@/types/dinerCatalog';

export interface CatalogAvailabilityResult {
  status: number;
  data: AvailabilityMap | null;
  etag: string | null;
}

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

  /**
   * Fetch dynamic dish availability map with conditional ETag validation (COM-4.1.2, FR-016).
   * Path: GET /establishments/{tenantId}/catalog/availability
   */
  getCatalogAvailability: async (
    tenantId: string,
    etag?: string | null
  ): Promise<CatalogAvailabilityResult> => {
    const headers: Record<string, string> = {};
    if (etag) {
      headers['If-None-Match'] = etag;
    }
    const response = await apiClient.get<AvailabilityMap>(
      `/establishments/${tenantId}/catalog/availability`,
      {
        headers,
        validateStatus: (status) => (status >= 200 && status < 300) || status === 304,
      }
    );
    const receivedEtag = (response.headers?.etag || response.headers?.ETag || null) as string | null;
    return {
      status: response.status,
      data: response.status === 304 ? null : response.data,
      etag: receivedEtag,
    };
  },
};
