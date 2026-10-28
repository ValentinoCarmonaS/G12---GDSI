import { apiClient } from './apiClient';
import type {
  CreateDishRequest,
  UpdateDishRequest,
  DishDetailResponse,
  DishPageResponse,
  PhotoUploadResponse,
  DishAllergensUpdateRequest,
  DishAllergensResponse,
} from '@/types/dish';

export const dishApi = {
  /**
   * Retrieves paginated establishment dishes with optional status filtering.
   */
  async listDishes(params?: { status?: string; page?: number; size?: number }): Promise<DishPageResponse> {
    const response = await apiClient.get<DishPageResponse>('/dishes', { params });
    return response.data;
  },

  /**
   * Creates a new dish draft with basic identity.
   */
  async createDishDraft(data: CreateDishRequest): Promise<DishDetailResponse> {
    const response = await apiClient.post<DishDetailResponse>('/dishes', data);
    return response.data;
  },

  /**
   * Retrieves full dish details by ID.
   */
  async getDishDetail(id: string): Promise<DishDetailResponse> {
    const response = await apiClient.get<DishDetailResponse>(`/dishes/${id}`);
    return response.data;
  },

  /**
   * Updates basic identity fields of an existing draft.
   */
  async updateDish(id: string, data: UpdateDishRequest): Promise<DishDetailResponse> {
    const response = await apiClient.put<DishDetailResponse>(`/dishes/${id}`, data);
    return response.data;
  },

  /**
   * Permanently deletes a dish draft and its photo.
   */
  async deleteDish(id: string): Promise<void> {
    await apiClient.delete(`/dishes/${id}`);
  },

  /**
   * Uploads an image photo for the specified dish draft.
   */
  async uploadPhoto(id: string, file: File): Promise<PhotoUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post<PhotoUploadResponse>(`/dishes/${id}/photo`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  /**
   * Deletes the photo of the specified dish draft.
   */
  async deletePhoto(id: string): Promise<void> {
    await apiClient.delete(`/dishes/${id}/photo`);
  },

  /**
   * Updates dish Bill of Materials recipe lines.
   */
  async updateRecipe(id: string, data: import('@/types/dish').RecipeUpdateRequest): Promise<import('@/types/dish').RecipeResponse> {
    const response = await apiClient.put<import('@/types/dish').RecipeResponse>(`/dishes/${id}/recipe`, data);
    return response.data;
  },

  /**
   * Removes a specific ingredient line from the dish recipe.
   */
  async removeRecipeIngredient(id: string, ingredientId: string): Promise<void> {
    await apiClient.delete(`/dishes/${id}/recipe/ingredients/${ingredientId}`);
  },

  /**
   * Retrieves the current recipe Bill of Materials for the dish.
   */
  async getRecipe(id: string): Promise<import('@/types/dish').RecipeResponse> {
    const response = await apiClient.get<import('@/types/dish').RecipeResponse>(`/dishes/${id}/recipe`);
    return response.data;
  },

  /**
   * Retrieves food cost audit and declared dietary labels for an admin evaluating a dish in PENDING_PRICE.
   */
  async getFoodCostAudit(id: string): Promise<import('@/types/dish').FoodCostAuditResponse> {
    const response = await apiClient.get<import('@/types/dish').FoodCostAuditResponse>(`/dishes/${id}/cost-audit`);
    return response.data;
  },

  /**
   * Updates dish dietary restrictions and allergen confirmation state (CHF-2.2.3).
   */
  async updateDishAllergens(id: string, data: DishAllergensUpdateRequest): Promise<DishAllergensResponse> {
    const response = await apiClient.put<DishAllergensResponse>(`/dishes/${id}/allergens`, data);
    return response.data;
  },

  /**
   * Submits a dish for administrator review (CHF-2.2.4).
   * Atomically transitions status to PENDING_PRICE and locks the dish against chef mutations.
   */
  async submitForReview(id: string): Promise<DishDetailResponse> {
    const response = await apiClient.post<DishDetailResponse>(`/dishes/${id}/submit-review`);
    return response.data;
  },
};

export default dishApi;
