import { apiClient } from './apiClient';
import type {
  AdminIngredientDetailResponse,
  CreateIngredientRequest,
  UpdateIngredientRequest,
  IngredientStatus,
  StockAdjustmentRequest,
  StockAdjustmentResponse,
  StockAuditEntryResponse,
  ChefIngredientPageResponse,
  IngredientFilterParams,
  ChefCatalogParams,
} from '../types/inventory';

export const inventoryApi = {
  /**
   * Retrieves the establishment's ingredient inventory with optional category and status filters.
   */
  async listIngredients(filters?: IngredientFilterParams): Promise<AdminIngredientDetailResponse[]> {
    const response = await apiClient.get<AdminIngredientDetailResponse[]>('/admin/ingredients', {
      params: filters,
    });
    return response.data;
  },

  /**
   * Creates a new raw material ingredient in the establishment's master catalog.
   */
  async createIngredient(request: CreateIngredientRequest): Promise<AdminIngredientDetailResponse> {
    const response = await apiClient.post<AdminIngredientDetailResponse>('/admin/ingredients', request);
    return response.data;
  },

  /**
   * Updates attributes of an existing ingredient (name, category, unit, unit purchase cost).
   */
  async updateIngredient(id: string, request: UpdateIngredientRequest): Promise<AdminIngredientDetailResponse> {
    const response = await apiClient.put<AdminIngredientDetailResponse>(`/admin/ingredients/${id}`, request);
    return response.data;
  },

  /**
   * Transitions ingredient lifecycle status (ACTIVE, DISCONTINUED, ARCHIVED).
   */
  async updateStatus(id: string, targetStatus: IngredientStatus): Promise<AdminIngredientDetailResponse> {
    const response = await apiClient.patch<AdminIngredientDetailResponse>(`/admin/ingredients/${id}/status`, {
      targetStatus,
    });
    return response.data;
  },

  /**
   * Applies an Absolute or Relative stock adjustment with mandatory justification note.
   */
  async adjustStock(id: string, request: StockAdjustmentRequest): Promise<StockAdjustmentResponse> {
    const response = await apiClient.post<StockAdjustmentResponse>(
      `/admin/ingredients/${id}/stock-adjustments`,
      request
    );
    return response.data;
  },

  /**
   * Retrieves the immutable, append-only stock audit history for an ingredient.
   */
  async getAuditHistory(id: string): Promise<StockAuditEntryResponse[]> {
    const response = await apiClient.get<StockAuditEntryResponse[]>(`/admin/ingredients/${id}/stock-audit`);
    return response.data;
  },

  /**
   * Explores active kitchen catalog ingredients for Chef role with pagination and category/text filters.
   */
  async getChefCatalog(params?: ChefCatalogParams): Promise<ChefIngredientPageResponse> {
    const response = await apiClient.get<ChefIngredientPageResponse>('/chef/ingredients', {
      params,
    });
    return response.data;
  },
};
