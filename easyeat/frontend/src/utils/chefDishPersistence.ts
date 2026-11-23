import { dishApi } from '@/api/dishApi';
import type {
  DishCategory,
  DishDetailResponse,
  DishStatus,
  RecipeIngredientItem,
} from '@/types/dish';

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
  readonly persistedDishId: string | null;
  readonly failedStage: 'identity' | 'photo' | 'recipe' | 'allergens' | 'submit-review';
  readonly message: string;
}

function buildFailure(
  persistedDishId: string | null,
  failedStage: DishPersistenceFailureState['failedStage'],
  err: unknown
): DishPersistenceFailureState {
  const message =
    err instanceof Error && err.message
      ? err.message
      : 'No se pudo guardar el plato. Intentá nuevamente.';
  return {
    persistedDishId,
    failedStage,
    message,
  };
}

/**
 * Orquesta el guardado unificado del borrador del plato sobre dishApi:
 * createDishDraft/updateDish -> foto -> receta -> alérgenos -> opcional submitForReview.
 * Si falla una etapa posterior a la creación, retiene persistedDishId para reintentos idempotentes.
 */
export async function persistUnifiedDishDraft(
  draft: UnifiedDishDraftState,
  mode: DishPersistenceMode
): Promise<DishPersistenceOrchestrationResult> {
  let dishId = draft.persistedDishId;
  let latestDish: DishDetailResponse;

  try {
    if (!dishId) {
      latestDish = await dishApi.createDishDraft({
        name: draft.name.trim(),
        category: draft.category,
        description: draft.description.trim() || undefined,
      });
      dishId = latestDish.id;
    } else {
      latestDish = await dishApi.updateDish(dishId, {
        name: draft.name.trim(),
        category: draft.category,
        description: draft.description.trim() || undefined,
      });
    }
  } catch (err) {
    throw buildFailure(dishId, 'identity', err);
  }

  try {
    if (draft.pendingPhotoFile) {
      await dishApi.uploadPhoto(dishId, draft.pendingPhotoFile);
    } else if (draft.photoMarkedForDeletion && draft.existingPhotoUrl) {
      await dishApi.deletePhoto(dishId);
    }
  } catch (err) {
    throw buildFailure(dishId, 'photo', err);
  }

  try {
    await dishApi.updateRecipe(dishId, {
      ingredients: draft.recipe.map((item) => ({
        ingredientId: item.ingredientId,
        quantity: item.quantity,
      })),
    });
  } catch (err) {
    throw buildFailure(dishId, 'recipe', err);
  }

  try {
    await dishApi.updateDishAllergens(dishId, {
      restrictionCodes: [...draft.restrictionCodes],
      confirmed: draft.allergensConfirmed,
    });
  } catch (err) {
    throw buildFailure(dishId, 'allergens', err);
  }

  if (mode === 'submit-review') {
    try {
      latestDish = await dishApi.submitForReview(dishId);
    } catch (err) {
      throw buildFailure(dishId, 'submit-review', err);
    }
  }

  return {
    dishId,
    dish: latestDish,
    submittedForReview: mode === 'submit-review',
  };
}
