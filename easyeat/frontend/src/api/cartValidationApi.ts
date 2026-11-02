import apiClient from './apiClient';

export interface CartStockValidationItemRequest {
  dishId: string;
  quantity: number;
}

export interface CartStockValidationRequest {
  tenantId: string;
  items: CartStockValidationItemRequest[];
}

export interface CartStockValidationItemResponse {
  dishId: string;
  isAvailable: boolean;
  availableStock: number;
  requestedQuantity: number;
}

export interface CartStockValidationResponse {
  isValid: boolean;
  items: CartStockValidationItemResponse[];
}

/**
 * Cart validation API client for stock availability synchronization (T036, US3, COM-6.1.3).
 * Connects frontend debounced quantity adjustments with POST /api/v1/cart/validate-stock.
 */
export const cartValidationApi = {
  validateStock: async (request: CartStockValidationRequest): Promise<CartStockValidationResponse> => {
    const response = await apiClient.post<CartStockValidationResponse>('/cart/validate-stock', request);
    return response.data;
  },
};

export default cartValidationApi;
