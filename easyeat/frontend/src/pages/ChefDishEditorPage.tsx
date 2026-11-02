import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { dishApi } from '@/api/dishApi';
import { BasicIdentityStep } from '@/components/dish/BasicIdentityStep';
import { RecipeBOMStep } from '@/components/dish/RecipeBOMStep';
import { AllergenStep } from '@/components/dish/AllergenStep';
import { ReviewSubmitStep } from '@/components/dish/ReviewSubmitStep';
import { DishStepper, type DishEditorTab } from '@/components/dish/DishStepper';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { DishRejectionFeedbackBanner } from '@/components/chef/DishRejectionFeedbackBanner';
import type { DishDetailResponse, CreateDishRequest, RecipeIngredientItem, RecipeUpdateRequest } from '@/types/dish';
import type { ChefReviewFeedback } from '@/types/dishReview';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';

export const ChefDishEditorPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [dish, setDish] = useState<DishDetailResponse | null>(null);
  const [feedback, setFeedback] = useState<ChefReviewFeedback | null>(null);
  const [activeTab, setActiveTab] = useState<DishEditorTab>('identity');
  const [isLoading, setIsLoading] = useState<boolean>(!!id);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const isReadOnly = dish != null && dish.status !== 'DRAFT';

  /**
   * FR-042: the newest rejection's proposed edition, when the Administrator sent one. The backend
   * returns entries newest-cycle-first, so the first entry is the current cycle.
   */
  const latestProposal = feedback?.entries[0]?.proposedChanges ?? null;

  const ingredientNames = useMemo<Record<string, string>>(
    () =>
      Object.fromEntries(
        (dish?.recipe ?? []).map((item) => [item.ingredientId, item.ingredientName])
      ),
    [dish?.recipe]
  );

  /** Identity step preloaded with the Administrator's proposed title/description (FR-042, T126). */
  const identityInitialData = useMemo(() => {
    if (!dish) return undefined;
    return {
      name: latestProposal?.title ?? dish.name,
      category: dish.category,
      description: latestProposal?.description ?? dish.description,
      photoUrl: dish.photoUrl,
    };
  }, [dish, latestProposal]);

  /** Recipe step preloaded with the proposed quantities; proposal lines win over current ones. */
  const recipeInitial = useMemo<RecipeIngredientItem[]>(() => {
    if (!dish) return [];
    const proposed = latestProposal?.ingredients;
    if (!proposed || proposed.length === 0) return dish.recipe;

    const currentById = new Map(dish.recipe.map((item) => [item.ingredientId, item]));
    return proposed.map((line) => {
      const current = currentById.get(line.ingredientId);
      return current
        ? { ...current, quantity: line.quantity }
        : {
            ingredientId: line.ingredientId,
            ingredientName:
              ingredientNames[line.ingredientId] ?? `Ingrediente ${line.ingredientId.slice(0, 8)}`,
            quantity: line.quantity,
            unitOfMeasure: 'u' as const,
          };
    });
  }, [dish, latestProposal, ingredientNames]);

  /** Allergen step preloaded with the proposal's codes, or the dish's own when none was sent. */
  const allergenInitialRestrictions = useMemo<string[]>(() => {
    if (!dish) return [];
    if (!latestProposal) return dish.restrictionCodes;
    return [
      ...(latestProposal.allergenCodes ?? []),
      ...(latestProposal.dietaryLifestyleCodes ?? []),
    ];
  }, [dish, latestProposal]);

  const fetchDish = useCallback(async (dishId: string) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await dishApi.getDishDetail(dishId);
      setDish(data);
      if (data.status === 'DRAFT') {
        try {
          const fb = await chefReviewFeedbackApi.getFeedback(dishId);
          setFeedback(fb);
        } catch {
          setFeedback(null);
        }
      } else {
        setFeedback(null);
      }
    } catch (err: unknown) {
      setErrorMessage('No se pudo cargar la información del plato.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (id) {
      fetchDish(id);
    } else {
      setDish(null);
      setIsLoading(false);
    }
  }, [id, fetchDish]);

  const handleSaveIdentity = async (data: CreateDishRequest, photoFile?: File) => {
    setIsSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      if (!id) {
        // Create draft
        const created = await dishApi.createDishDraft(data);
        if (photoFile) {
          await dishApi.uploadPhoto(created.id, photoFile);
        }
        setSuccessMessage('Borrador creado correctamente.');
        navigate(`/cocina/platos/${created.id}`);
      } else {
        // Update draft
        const updated = await dishApi.updateDish(id, data);
        if (photoFile) {
          await dishApi.uploadPhoto(id, photoFile);
        }
        setDish(updated);
        setSuccessMessage('Borrador actualizado correctamente.');
        await fetchDish(id);
      }
    } catch (err: any) {
      const message =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Error al guardar la información del plato.';
      setErrorMessage(message);
    } finally {
      setIsSaving(false);
    }
  };

  const handlePhotoDelete = async () => {
    if (!id) return;
    try {
      await dishApi.deletePhoto(id);
      setSuccessMessage('Foto eliminada correctamente.');
      await fetchDish(id);
    } catch (err: unknown) {
      setErrorMessage('Error al eliminar la foto.');
    }
  };

  const handleSaveRecipe = async (recipeItems: RecipeIngredientItem[]) => {
    if (!id) return;
    setIsSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const payload: RecipeUpdateRequest = {
        ingredients: recipeItems.map((item) => ({
          ingredientId: item.ingredientId,
          quantity: item.quantity,
        })),
      };
      await dishApi.updateRecipe(id, payload);
      setSuccessMessage('Receta actualizada correctamente.');
      await fetchDish(id);
    } catch (err: any) {
      const message =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Error al guardar la receta del plato.';
      setErrorMessage(message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveRecipeIngredient = async (ingredientId: string) => {
    if (!id) return;
    try {
      await dishApi.removeRecipeIngredient(id, ingredientId);
      setSuccessMessage('Ingrediente eliminado de la receta.');
      await fetchDish(id);
    } catch (err: any) {
      const message =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Error al eliminar el ingrediente de la receta.';
      setErrorMessage(message);
    }
  };

  const handleSaveAllergens = async (restrictionCodes: string[], confirmed: boolean) => {
    if (!id) return;
    setIsSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      await dishApi.updateDishAllergens(id, { restrictionCodes, confirmed });
      setSuccessMessage('Alérgenos actualizados correctamente.');
      await fetchDish(id);
    } catch (err: any) {
      const message =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Error al guardar los alérgenos del plato.';
      setErrorMessage(message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleSaveSubmitForReview = async () => {
    if (!id) return;
    setIsSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const updated = await dishApi.submitForReview(id);
      setDish(updated);
      setSuccessMessage('Ficha técnica enviada a revisión exitosamente.');
      await fetchDish(id);
    } catch (err: any) {
      const message =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Error al enviar la ficha técnica a revisión.';
      setErrorMessage(message);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-12 max-w-4xl flex justify-center text-zinc-500">
        Cargando ficha técnica...
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      {/* Navigation & Header */}
      <div className="mb-6">
        <Link
          to="/cocina/platos"
          className="text-xs font-semibold text-amber-600 dark:text-amber-400 hover:underline mb-2 inline-block"
        >
          ← Volver a Platos
        </Link>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">
              {id ? `Ficha Técnica: ${dish?.name || 'Cargando...'}` : 'Nueva Ficha Técnica'}
            </h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1">
              {activeTab === 'identity'
                ? 'Paso 1: Identidad básica del plato y fotografía.'
                : activeTab === 'recipe'
                ? 'Paso 2: Composición de la receta y escandallo de ingredientes (BOM).'
                : activeTab === 'allergens'
                ? 'Paso 3: Etiquetado de alérgenos y preferencias dietarias.'
                : 'Paso 4: Verificación de requisitos y envío para revisión de precios.'}
            </p>
          </div>
          {dish && (
            <div>
              <DishStatusBadge status={dish.status} />
            </div>
          )}
        </div>
      </div>

      {/* Stepper Tabs */}
      <DishStepper
        activeTab={activeTab}
        onTabChange={setActiveTab}
        hasDishId={Boolean(id)}
        status={dish?.status}
        isLocked={isReadOnly}
      />

      {dish?.status === 'CANCELLED' && (
        <div
          data-testid="cancelled-dish-readonly"
          role="status"
          className="mb-6 rounded border border-zinc-300 bg-zinc-100 px-4 py-3 text-sm text-zinc-700"
        >
          Este plato fue cancelado por administración y está disponible solo para consulta.
        </div>
      )}

       {/* Notifications */}
      {feedback && feedback.entries.length > 0 && (
        <div className="mb-6">
          <DishRejectionFeedbackBanner
            entries={feedback.entries}
            ingredientNames={ingredientNames}
          />
        </div>
      )}
      {errorMessage && (
        <div className="p-4 mb-6 rounded-lg bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 text-sm text-red-700 dark:text-red-400">
          {errorMessage}
        </div>
      )}
      {successMessage && (
        <div className="p-4 mb-6 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-900 text-sm text-emerald-700 dark:text-emerald-400">
          {successMessage}
        </div>
      )}

      {/* Tab Panels */}
      {activeTab === 'identity' && (
        <BasicIdentityStep
          initialData={identityInitialData}
          onSave={handleSaveIdentity}
          onPhotoDelete={handlePhotoDelete}
          isReadOnly={isReadOnly}
          isSaving={isSaving}
        />
      )}
      {activeTab === 'recipe' && (
        <RecipeBOMStep
          dishId={id}
          initialRecipe={recipeInitial}
          onSaveRecipe={handleSaveRecipe}
          onRemoveIngredient={handleRemoveRecipeIngredient}
          isReadOnly={isReadOnly}
          isSaving={isSaving}
        />
      )}
      {activeTab === 'allergens' && (
        <AllergenStep
          dishId={id}
          initialRestrictions={allergenInitialRestrictions}
          initialConfirmed={dish?.allergensConfirmed || false}
          onSaveAllergens={handleSaveAllergens}
          isReadOnly={isReadOnly}
          isSaving={isSaving}
        />
      )}
      {activeTab === 'review' && dish && (
        <ReviewSubmitStep
          dish={dish}
          onSubmitForReview={handleSaveSubmitForReview}
          isReadOnly={isReadOnly}
          isSubmitting={isSaving}
        />
      )}
    </div>
  );
};

export default ChefDishEditorPage;
