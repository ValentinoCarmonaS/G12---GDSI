/**
 * Payload types for the Administrative Back-Office (spec 007).
 *
 * Mirrors `specs/007-admin-dish-approval-pricing/contracts/openapi.yaml`. Money arrives as a JSON
 * number and is kept as `number` here: these values are displayed and compared, never used to
 * derive a price the backend will store. All arithmetic that produces a stored figure happens on
 * the server with `BigDecimal` (FR-008).
 *
 * Covers all four user stories, because the inbox, the calculator, the history and the Chef-side
 * feedback share one contract and splitting it per story would duplicate the shapes.
 */

/** The three inbox views. Not a partition: HISTORY overlaps both others on purpose (FR-002). */
export type InboxTab = 'PENDING' | 'PUBLISHED' | 'HISTORY';

/**
 * The dish lifecycle as spec 007 sees it.
 *
 * Deliberately separate from `DishStatus` in `types/dish.ts`, which spec 005 owns and which still
 * admits only DRAFT and PENDING_PRICE. Widening that union is T079a's job, in US3, where the
 * mismatch becomes observable.
 */
export type ReviewDishStatus = 'DRAFT' | 'PENDING_PRICE' | 'PUBLISHED';

export type PublicationGateCondition =
  | 'RETAIL_PRICE_MISSING'
  | 'EMPTY_RECIPE'
  | 'ALLERGENS_UNCONFIRMED';

export type AcknowledgementKind = 'UNPRICED_INGREDIENTS' | 'NEGATIVE_MARGIN';

export type ReviewEventType = 'APPROVED' | 'REJECTED';

export interface DishReviewInboxItem {
  dishId: string;
  name: string;
  category: string | null;
  status: ReviewDishStatus;
  /** Null when no author is recorded, or when the recorded one no longer resolves (FR-027). */
  authorName: string | null;
  /** Informational only: it never blocks an administrative action (FR-026). */
  authorDeactivated: boolean;
  submittedAt: string | null;
  publishedAt: string | null;
  retailPrice: number | null;
  isResubmission: boolean;
  /** Past rejections. The cycle the dish is in now is this plus one. */
  reviewCycleCount: number;
}

export interface DishReviewInboxPage {
  items: DishReviewInboxItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  /** Returned on every tab, so the badge never depends on the active view (FR-003). */
  pendingCount: number;
}

export interface RecipeItemAudit {
  ingredientId: string;
  ingredientName: string;
  quantity: number;
  unitOfMeasure: string | null;
  unitPurchaseCost: number;
  lineSubtotal: number;
  unpricedWarning: boolean;
}

/** Spec 006's payload, consumed verbatim. Its shape is owned there, not here. */
export interface FoodCostAudit {
  dishId: string;
  dishName: string;
  totalFoodCost: number;
  hasUnpricedIngredients: boolean;
  warningNotes: string[];
  requiredDietaryRestrictions: string[];
  items: RecipeItemAudit[];
}

export interface MarginSummary {
  /** Null when there is no recipe to cost (FR-037). */
  foodCost: number | null;
  retailPrice: number | null;
  /**
   * Null while the dish is unpriced or uncostable. Zero would claim the dish breaks even, which
   * is a different statement, so the UI must branch on null rather than treat it as 0.
   */
  marginPct: number | null;
  /** Worst-case deviation of a margin re-derived from the stored 2-decimal price (FR-008). */
  marginTolerancePp: number | null;
  /** At or above this price the ±0.01 pp round-trip bound holds; below it, disclose instead. */
  toleranceThresholdPrice: number | null;
  hasUnpricedIngredients: boolean;
  /** False exactly when the Food Cost is zero or absent, which disables margin→price (FR-007). */
  marginDerivable: boolean;
}

export interface PublicationGateStatus {
  /** Stays true while acknowledgements are outstanding: those are not gate failures (FR-017). */
  satisfied: boolean;
  unmetConditions: PublicationGateCondition[];
  requiredAcknowledgements: AcknowledgementKind[];
}

export interface DishReviewDetail {
  dishId: string;
  name: string;
  description: string | null;
  category: string | null;
  status: ReviewDishStatus;
  retailPrice: number | null;
  photoUrl: string | null;
  authorName: string | null;
  authorDeactivated: boolean;
  allergensConfirmed: boolean;
  submittedAt: string | null;
  publishedAt: string | null;
  reviewCycleCount: number;
  isResubmission: boolean;
  margin: MarginSummary;
  /** Advisory: FR-015 re-evaluates the gate when the action arrives, so this never authorises. */
  publicationGate: PublicationGateStatus;
  /** True when the dish holds no recipe lines; `foodCostAudit` is then null (FR-037). */
  recipeAbsent: boolean;
  foodCostAudit: FoodCostAudit | null;
}

/** US2: the previously read price travels with the save, as the compare-and-set of FR-025. */
export interface SetRetailPriceRequest {
  retailPrice: number;
  /** Required, and `null` is a valid value: it asserts the dish was unpriced when read. */
  expectedRetailPrice: number | null;
  acknowledgeNegativeMargin?: boolean;
}

export interface DishPricingResult {
  dishId: string;
  status: ReviewDishStatus;
  margin: MarginSummary;
  priceHistoryEntryId: string;
}

/** US3 */
export interface ApproveDishRequest {
  acknowledgeUnpricedIngredients?: boolean;
  acknowledgeNegativeMargin?: boolean;
}

export interface DishApprovalResult {
  dishId: string;
  status: ReviewDishStatus;
  /** True when the dish was already published: an idempotent no-op, not a fresh one (FR-018). */
  alreadyPublished: boolean;
  publishedAt: string | null;
  reviewHistoryEntryId: string | null;
}

/** US4 */
export interface RejectDishRequest {
  comment: string;
}

export interface DishRejectionResult {
  dishId: string;
  status: ReviewDishStatus;
  cycleNumber: number;
  reviewHistoryEntryId: string;
}

/** The Chef-facing projection: no cost, no margin, no price by construction (FR-023). */
export interface ChefReviewFeedbackEntry {
  cycleNumber: number;
  reviewerName: string | null;
  comment: string | null;
  createdAt: string;
}

export interface ChefReviewFeedback {
  dishId: string;
  entries: ChefReviewFeedbackEntry[];
}
