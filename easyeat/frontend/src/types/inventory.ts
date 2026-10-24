export type IngredientCategory = 'Produce' | 'Meat' | 'Dairy' | 'Dry Goods' | 'Spices';

export type UnitOfMeasure = 'g' | 'kg' | 'ml' | 'l' | 'u';

export type IngredientStatus = 'ACTIVE' | 'DISCONTINUED' | 'ARCHIVED';

export type StockAvailabilityStatus = 'In Stock' | 'Out of Stock';

export type StockAdjustmentMode = 'ABSOLUTE' | 'RELATIVE';

export interface CreateIngredientRequest {
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  unitPurchaseCost: number;
}

export interface UpdateIngredientRequest {
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  unitPurchaseCost: number;
}

export interface AdminIngredientDetailResponse {
  id: string;
  tenantId: string;
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  unitPurchaseCost: number;
  status: IngredientStatus;
  currentStock: number;
  createdAt: string;
  updatedAt: string;
}

export interface IngredientStatusUpdateRequest {
  targetStatus: IngredientStatus;
}

export interface StockAdjustmentRequest {
  mode: StockAdjustmentMode;
  value: number;
  justification: string;
}

export interface StockAdjustmentResponse {
  ingredientId: string;
  ingredientName: string;
  unitOfMeasure: UnitOfMeasure;
  previousStock: number;
  newStock: number;
  netDelta: number;
  auditEntryId: string;
  adjustedAt: string;
}

export interface StockAuditEntryResponse {
  id: string;
  ingredientId: string;
  userId: string;
  adminName: string;
  adjustmentMode: StockAdjustmentMode;
  previousStock: number;
  newStock: number;
  netDelta: number;
  justification: string;
  createdAt: string;
}

export interface ChefIngredientItemResponse {
  id: string;
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  stockStatus: StockAvailabilityStatus;
  currentStock: number;
}

export interface ChefIngredientPageResponse {
  items: ChefIngredientItemResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface IngredientFilterParams {
  category?: IngredientCategory;
  status?: IngredientStatus;
}

export interface ChefCatalogParams {
  category?: IngredientCategory;
  search?: string;
  page?: number;
  size?: number;
}
