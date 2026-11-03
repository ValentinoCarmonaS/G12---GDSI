import type { UnitOfMeasure } from './inventory';

/**
 * Closed list of dish categories supported by EasyEat (CHF-2.2.1, FR-003).
 */
export const DISH_CATEGORIES = [
  'Entradas',
  'Platos principales',
  'Postres',
  'Bebidas',
  'Ensaladas',
] as const;

export type DishCategory = (typeof DISH_CATEGORIES)[number];

export function isDishCategory(val: unknown): val is DishCategory {
  return typeof val === 'string' && DISH_CATEGORIES.includes(val as DishCategory);
}

/**
 * Chef dish lifecycle statuses (CHF-2.2.1, CHF-2.2.4, spec 007's DishStatus).
 *
 * PUBLISHED added by T079a: the backend enum admitted it under T005's D1 amendment before this
 * union did, which made a published dish render as the raw token 'PUBLISHED' through
 * DishStatusBadge's fallback once T076 could set it.
 */
export type DishStatus = 'DRAFT' | 'PENDING_PRICE' | 'PUBLISHED' | 'CANCELLED';

export function isDishStatus(val: unknown): val is DishStatus {
  return (
    val === 'DRAFT' ||
    val === 'PENDING_PRICE' ||
    val === 'PUBLISHED' ||
    val === 'CANCELLED'
  );
}

/**
 * Bill of Materials (BOM) recipe line item representation.
 */
export interface RecipeIngredientItem {
  ingredientId: string;
  ingredientName: string;
  quantity: number;
  unitOfMeasure: UnitOfMeasure;
  isIngredientActive?: boolean;
}

/**
 * Payload for creating a new dish draft (Step 1).
 */
export interface CreateDishRequest {
  name: string;
  category: DishCategory;
  description?: string | null;
}

/**
 * Payload for updating basic dish draft identity (Step 1).
 */
export interface UpdateDishRequest {
  name: string;
  category: DishCategory;
  description?: string | null;
}

/**
 * Individual input line for recipe composition.
 */
export interface RecipeItemInput {
  ingredientId: string;
  quantity: number;
}

/**
 * Payload for updating full recipe Bill of Materials (Step 2).
 */
export interface RecipeUpdateRequest {
  ingredients?: RecipeItemInput[];
  recipe?: RecipeItemInput[];
}

/**
 * Response for recipe Bill of Materials updates.
 */
export interface RecipeResponse {
  dishId: string;
  recipe: RecipeIngredientItem[];
  ingredients?: RecipeIngredientItem[];
  totalIngredientsCount?: number;
  updatedAt?: string;
}

/**
 * Payload for updating dish dietary restrictions and allergen confirmation (Step 3).
 */
export interface DishAllergensUpdateRequest {
  restrictionCodes: string[];
  confirmed: boolean;
}

/**
 * Response for allergen section updates.
 */
export interface DishAllergensResponse {
  dishId: string;
  restrictionCodes: string[];
  allergensConfirmed: boolean;
  updatedAt: string;
}

/**
 * Compact summary representation for paginated dish listing.
 */
export interface DishSummaryResponse {
  id: string;
  tenantId: string;
  name: string;
  category: DishCategory;
  status: DishStatus;
  photoUrl: string | null;
  allergensConfirmed: boolean;
  recipeIngredientsCount: number;
  updatedAt: string;
  createdAt: string;
}

/**
 * Full detail technical file representation for Stepper review and edit.
 */
export interface DishDetailResponse {
  id: string;
  tenantId: string;
  name: string;
  category: DishCategory;
  description: string | null;
  photoUrl: string | null;
  status: DishStatus;
  allergensConfirmed: boolean;
  restrictionCodes: string[];
  recipe: RecipeIngredientItem[];
  isCompleteForReview: boolean;
  missingConditions: string[];
  createdById: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Paginated container for dish summaries.
 */
export interface DishPageResponse {
  content: DishSummaryResponse[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
}

/**
 * Response after uploading or updating a dish photo.
 */
export interface PhotoUploadResponse {
  dishId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  photoUrl: string;
}

/**
 * RFC 7807/9457 ProblemDetail response for dish-related errors.
 */
export interface DishProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  invalidParams?: Array<{ field: string; reason: string }>;
  missingConditions?: string[];
  dishName?: string;
  dishId?: string;
  currentStatus?: string;
  ingredientId?: string;
  quantity?: number;
  unitOfMeasure?: string;
}

export interface RecipeItemAuditResponse {
  ingredientId: string;
  ingredientName: string;
  recipeQuantity: number;
  unitOfMeasure: string;
  unitPurchaseCost: number;
  lineSubtotal: number;
  unpricedWarning: boolean;
}

export interface FoodCostAuditResponse {
  dishId: string;
  dishName: string;
  totalFoodCost: number;
  hasUnpricedIngredients: boolean;
  warningNotes: string[];
  requiredDietaryRestrictions: string[];
  items: RecipeItemAuditResponse[];
}

