/**
 * Spec 027 — Chef Brutalism Redesign
 * Contract for the unified 4-step dish technical sheet editor
 * (`/cocina/platos/nuevo` and `/cocina/platos/:id`) with Anterior/Siguiente
 * navigation in steps 1-3 and consolidated Step 4 persistence over existing dishApi.
 */

import type {
  DishCategory,
  DishDetailResponse,
  DishStatus,
  RecipeIngredientItem,
} from '../../../easyeat/frontend/src/types/dish';

export type ChefDishEditorTab = 'identity' | 'recipe' | 'allergens' | 'review';

export interface UnifiedDishDraftState {
  readonly persistedDishId: string | null;
  readonly status: DishStatus;
  readonly name: string;
  readonly category: DishCategory;
  readonly description: string;
  readonly existingPhotoUrl: string | null;
  readonly pendingPhotoFile: File | null;
  readonly photoMarkedForDeletion: boolean;
  readonly recipe: readonly RecipeIngredientItem[];
  readonly restrictionCodes: readonly string[];
  readonly allergensConfirmed: boolean;
  readonly isDirty: boolean;
}

export type DishPersistenceMode = 'draft' | 'submit-review';

export interface DishPersistenceOrchestrationResult {
  readonly dishId: string;
  readonly dish: DishDetailResponse;
  readonly submittedForReview: boolean;
}

export interface DishPersistenceFailureState {
  /** Retained when createDishDraft succeeded before a downstream step failed. */
  readonly persistedDishId: string | null;
  readonly failedStage: 'identity' | 'photo' | 'recipe' | 'allergens' | 'submit-review';
  readonly message: string;
}

export interface DishStepNavigationContract {
  readonly stepsOrder: readonly ['identity', 'recipe', 'allergens', 'review'];
  readonly stepsWithPrevNextOnly: readonly ['identity', 'recipe', 'allergens'];
  readonly finalStepActions: readonly ['Guardar borrador', 'Guardar y enviar a revisión'];
  readonly stepperMinButtonHeightPx: 48;
  readonly mobileActionBarStickyAboveBottomNav: true;
  /** Preserved prop names fed from UnifiedDishDraftState so Spec 007 ChefDishEditorPage.test.tsx stays intact. */
  readonly preservedStepPropNames: {
    readonly BasicIdentityStep: 'initialData';
    readonly RecipeBOMStep: 'initialRecipe';
    readonly AllergenStep: 'initialRestrictions';
  };
  readonly unsavedChangesProtection: {
    readonly headerBadgeLabel: 'Cambios sin guardar';
    readonly registerBeforeUnload: true;
    readonly backLinkConfirmationModalOnDirty: true;
    readonly disabledInReadOnlyOrAfterSave: true;
  };
  readonly existingBackendEndpointsOnly: readonly [
    'POST /dishes',
    'PUT /dishes/:id',
    'POST /dishes/:id/photo',
    'DELETE /dishes/:id/photo',
    'PUT /dishes/:id/recipe',
    'PUT /dishes/:id/allergens',
    'POST /dishes/:id/submit-review',
  ];
}
