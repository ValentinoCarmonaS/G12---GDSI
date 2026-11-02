export type IngredientCategory = 'Produce' | 'Meat' | 'Dairy' | 'Dry Goods' | 'Spices';

export type UnitOfMeasure = 'g' | 'kg' | 'ml' | 'l' | 'u';

export type IngredientStatus = 'ACTIVE' | 'DISCONTINUED';

export type StockAvailabilityStatus = 'In Stock' | 'Out of Stock';

export type StockAdjustmentAction = 'ADD' | 'SUBTRACT';

export type StockAdjustmentMode = 'ADD' | 'SUBTRACT' | 'ABSOLUTE' | 'RELATIVE';

export interface IngredientBatch {
  id: string;
  ingredientId: string;
  initialQuantity: number;
  remainingQuantity: number;
  unitCost: number;
  createdAt: string;
}

export interface CreateIngredientRequest {
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  unitPurchaseCost?: number;
  /** Total cost of the purchase presentation (package). Paired with unitsPerPackage. */
  packageCost?: number;
  /** Base units contained in one package. Paired with packageCost. */
  unitsPerPackage?: number;
}

export interface UpdateIngredientRequest {
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  unitPurchaseCost?: number;
  /** Total cost of the purchase presentation (package). Paired with unitsPerPackage. */
  packageCost?: number;
  /** Base units contained in one package. Paired with packageCost. */
  unitsPerPackage?: number;
}

export interface AdminIngredientDetailResponse {
  id: string;
  tenantId: string;
  name: string;
  category: IngredientCategory;
  unitOfMeasure: UnitOfMeasure;
  unitPurchaseCost: number;
  packageCost?: number;
  unitsPerPackage?: number;
  status: IngredientStatus;
  currentStock: number;
  createdAt: string;
  updatedAt: string;
}

export interface IngredientStatusUpdateRequest {
  targetStatus: IngredientStatus;
}

export interface StockAdjustmentRequest {
  action?: StockAdjustmentAction;
  mode?: StockAdjustmentMode;
  quantity?: number;
  value?: number;
  unitCost?: number;
  batchId?: string;
  justification: string;
}

export interface StockAdjustmentResponse {
  ingredientId: string;
  ingredientName: string;
  unitOfMeasure: UnitOfMeasure;
  previousStock: number;
  newStock: number;
  netDelta: number;
  batchId?: string;
  unitCost?: number;
  financialLoss?: number;
  auditEntryId: string;
  adjustedAt: string;
}

export interface StockAuditEntryResponse {
  id: string;
  ingredientId: string;
  userId: string;
  adminName: string;
  adjustmentMode: StockAdjustmentMode | StockAdjustmentAction;
  previousStock: number;
  newStock: number;
  netDelta: number;
  batchId?: string;
  unitCost?: number;
  financialLoss?: number;
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
