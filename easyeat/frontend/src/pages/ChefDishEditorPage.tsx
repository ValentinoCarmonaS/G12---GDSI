import React, { useState, useEffect, useMemo } from 'react';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { dishApi } from '@/api/dishApi';
import { chefReviewFeedbackApi } from '@/api/chefReviewFeedbackApi';
import { BasicIdentityStep } from '@/components/dish/BasicIdentityStep';
import { RecipeBOMStep } from '@/components/dish/RecipeBOMStep';
import { AllergenStep } from '@/components/dish/AllergenStep';
import { ReviewSubmitStep } from '@/components/dish/ReviewSubmitStep';
import { DishStepper, type DishEditorTab } from '@/components/dish/DishStepper';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { DishRejectionFeedbackBanner } from '@/components/chef/DishRejectionFeedbackBanner';
import { BrutalistModal } from '@/components/chef/BrutalistModal';
import {
  persistUnifiedDishDraft,
  type UnifiedDishDraftState,
  type DishPersistenceFailureState,
} from '@/utils/chefDishPersistence';
import type { DishDetailResponse } from '@/types/dish';
import type { ChefReviewFeedback, DishProposedChanges } from '@/types/dishReview';

const INITIAL_DRAFT: UnifiedDishDraftState = {
  persistedDishId: null,
  status: 'DRAFT',
  name: '',
  category: 'Platos principales',
  description: '',
  existingPhotoUrl: null,
  pendingPhotoFile: null,
  photoMarkedForDeletion: false,
  recipe: [],
  restrictionCodes: [],
  allergensConfirmed: false,
  isDirty: false,
};

export const ChefDishEditorPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<DishEditorTab>('identity');
  const [draft, setDraft] = useState<UnifiedDishDraftState>(INITIAL_DRAFT);
  const [loadedDish, setLoadedDish] = useState<DishDetailResponse | null>(null);
  const [feedback, setFeedback] = useState<ChefReviewFeedback | null>(null);
  const [loading, setLoading] = useState(Boolean(id));
  const [isSaving, setIsSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [showExitModal, setShowExitModal] = useState(false);

  useEffect(() => {
    if (!id) {
      setDraft(INITIAL_DRAFT);
      setLoadedDish(null);
      setLoading(false);
      return;
    }

    let isMounted = true;
    setLoading(true);
    setErrorMsg(null);

    Promise.all([
      dishApi.getDishDetail(id),
      Promise.resolve(chefReviewFeedbackApi.getFeedback(id)).catch(() => null),
    ])
      .then(([dishData, feedbackData]) => {
        if (!isMounted) return;
        setLoadedDish(dishData);
        setFeedback(feedbackData);
        const proposal: DishProposedChanges | null | undefined =
          feedbackData?.entries?.[0]?.proposedChanges;
        const proposedIngredients = proposal?.ingredients ?? [];
        const mergedRecipe = (dishData.recipe ?? []).map((item) => {
          const match = proposedIngredients.find((p) => p.ingredientId === item.ingredientId);
          return match ? { ...item, quantity: match.quantity } : item;
        });
        setDraft({
          persistedDishId: dishData.id,
          status: dishData.status,
          name: proposal?.title ?? dishData.name,
          category: dishData.category,
          description: proposal?.description ?? dishData.description ?? '',
          existingPhotoUrl: dishData.photoUrl || null,
          pendingPhotoFile: null,
          photoMarkedForDeletion: false,
          recipe: mergedRecipe,
          restrictionCodes: Array.from(
            new Set([
              ...(proposal?.allergenCodes ?? []),
              ...(proposal?.dietaryLifestyleCodes ?? []),
              ...(dishData.restrictionCodes ?? []),
            ])
          ),
          allergensConfirmed: Boolean(dishData.allergensConfirmed),
          isDirty: false,
        });
      })
      .catch(() => {
        if (isMounted) setErrorMsg('No se pudo cargar la ficha técnica del plato.');
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [id]);

  const isReadOnly =
    draft.status === 'PENDING_PRICE' ||
    draft.status === 'PUBLISHED' ||
    draft.status === 'CANCELLED';

  useEffect(() => {
    if (!draft.isDirty || isReadOnly) return;

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [draft.isDirty, isReadOnly]);

  const initialIdentityData = useMemo(
    () => ({
      name: draft.name,
      category: draft.category,
      description: draft.description,
      photoUrl: draft.existingPhotoUrl,
    }),
    [draft.name, draft.category, draft.description, draft.existingPhotoUrl]
  );

  const completedSteps = useMemo(
    () => ({
      identity: Boolean(draft.name.trim() && draft.category),
      recipe:
        draft.recipe.length > 0 &&
        draft.recipe.every((r) => r.quantity > 0 && r.isIngredientActive !== false),
      allergens: draft.allergensConfirmed,
      review: draft.status === 'PENDING_PRICE' || draft.status === 'PUBLISHED',
    }),
    [draft]
  );

  const syntheticDishForReview: DishDetailResponse = useMemo(() => {
    const missingConditions: string[] = [];
    if (!completedSteps.identity) missingConditions.push('IDENTITY_INCOMPLETE');
    if (!completedSteps.recipe) missingConditions.push('EMPTY_RECIPE');
    if (!completedSteps.allergens) missingConditions.push('ALLERGENS_UNCONFIRMED');

    return {
      id: draft.persistedDishId || 'draft',
      tenantId: loadedDish?.tenantId ?? '',
      name: draft.name,
      category: draft.category,
      description: draft.description || null,
      status: draft.status,
      photoUrl: draft.existingPhotoUrl,
      recipe: [...draft.recipe],
      restrictionCodes: [...draft.restrictionCodes],
      allergensConfirmed: draft.allergensConfirmed,
      isCompleteForReview:
        completedSteps.identity && completedSteps.recipe && completedSteps.allergens,
      missingConditions,
      createdById: loadedDish?.createdById ?? null,
      pausedByChef: loadedDish?.pausedByChef,
      createdAt: loadedDish?.createdAt ?? '',
      updatedAt: loadedDish?.updatedAt ?? '',
    };
  }, [loadedDish, draft, completedSteps]);

  const handlePersist = async (mode: 'draft' | 'submit-review') => {
    setIsSaving(true);
    setErrorMsg(null);
    setSuccessMsg(null);

    try {
      const result = await persistUnifiedDishDraft(draft, mode);
      setLoadedDish(result.dish);
      setDraft((prev) => ({
        ...prev,
        persistedDishId: result.dishId,
        status: result.dish?.status ?? prev.status,
        existingPhotoUrl: result.dish?.photoUrl || prev.existingPhotoUrl,
        pendingPhotoFile: null,
        photoMarkedForDeletion: false,
        isDirty: false,
      }));
      setSuccessMsg(
        result.submittedForReview
          ? 'Plato guardado y enviado a revisión.'
          : 'Borrador guardado correctamente.'
      );
      if (!id && result.dishId) {
        navigate(`/cocina/platos/${result.dishId}`, { replace: true });
      }
    } catch (err) {
      const failure = err as DishPersistenceFailureState;
      if (failure?.persistedDishId && !draft.persistedDishId) {
        setDraft((prev) => ({ ...prev, persistedDishId: failure.persistedDishId }));
      }
      setErrorMsg(failure?.message || 'Error al guardar el plato.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleBackClick = (e: React.MouseEvent<HTMLAnchorElement>) => {
    if (draft.isDirty && !isReadOnly) {
      e.preventDefault();
      setShowExitModal(true);
    }
  };

  return (
    <WorkspaceLayout title="Ficha Técnica" fullWidth>
      <div className="max-w-5xl mx-auto px-4 py-6 pb-40 md:pb-6 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b-2 border-[#0A0A0A] pb-4">
          <div className="flex items-center gap-3">
            <Link
              to="/cocina/platos"
              onClick={handleBackClick}
              className="px-3 py-2 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-xs font-bold uppercase hover:bg-[#F5F5F4]"
            >
              ← Volver a Platos
            </Link>
            <h1 className="text-xl font-extrabold uppercase tracking-tight text-[#0A0A0A]">
              {draft.persistedDishId
                ? draft.name
                  ? `Ficha Técnica: ${draft.name}`
                  : 'Editar ficha técnica'
                : 'Nuevo plato'}
            </h1>
          </div>

          <div className="flex items-center gap-2">
            {draft.isDirty && !isReadOnly && (
              <span className="px-2.5 py-1 border-2 border-[#0A0A0A] bg-[#FEF3C7] text-[#0A0A0A] font-mono text-xs font-bold uppercase">
                Cambios sin guardar
              </span>
            )}
            <DishStatusBadge status={draft.status} />
          </div>
        </div>

        {isReadOnly && (
          <div
            role="status"
            data-testid={draft.status === 'CANCELLED' ? 'cancelled-dish-readonly' : undefined}
            className="border-2 border-[#0A0A0A] bg-[#FEF3C7] text-[#0A0A0A] p-4 font-bold text-sm"
          >
            Solo lectura — Este plato se encuentra en estado {draft.status} y no puede editarse.
          </div>
        )}

        {feedback?.entries && feedback.entries.length > 0 && (
          <DishRejectionFeedbackBanner entries={feedback.entries} />
        )}

        {errorMsg && (
          <div
            role="alert"
            className="border-2 border-[#0A0A0A] bg-[#FEE2E2] text-[#DC2626] p-4 font-bold text-sm"
          >
            {errorMsg}
          </div>
        )}

        {successMsg && (
          <div
            role="status"
            className="border-2 border-[#0A0A0A] bg-[#D1FAE5] text-[#065F46] p-4 font-bold text-sm"
          >
            {successMsg}
          </div>
        )}

        <DishStepper
          activeTab={activeTab}
          onTabChange={setActiveTab}
          hasDishId={Boolean(draft.persistedDishId)}
          status={draft.status}
          isLocked={isReadOnly}
          completedSteps={completedSteps}
        />

        {loading ? (
          <p className="font-mono text-sm text-[#52525B] py-12 text-center">
            Cargando ficha técnica...
          </p>
        ) : (
          <>
            {activeTab === 'identity' && (
              <BasicIdentityStep
                initialData={initialIdentityData}
                isReadOnly={isReadOnly}
                isSaving={isSaving}
                onDraftChange={(data, photoFile) =>
                  setDraft((prev) => ({
                    ...prev,
                    name: data.name,
                    category: data.category,
                    description: data.description || '',
                    pendingPhotoFile: photoFile ?? prev.pendingPhotoFile,
                    isDirty: true,
                  }))
                }
                onPhotoDelete={() =>
                  setDraft((prev) => ({
                    ...prev,
                    existingPhotoUrl: null,
                    pendingPhotoFile: null,
                    photoMarkedForDeletion: true,
                    isDirty: true,
                  }))
                }
                onNext={(data, photoFile) => {
                  setDraft((prev) => ({
                    ...prev,
                    name: data.name,
                    category: data.category,
                    description: data.description || '',
                    pendingPhotoFile: photoFile ?? prev.pendingPhotoFile,
                  }));
                  setActiveTab('recipe');
                }}
              />
            )}

            {activeTab === 'recipe' && (
              <RecipeBOMStep
                dishId={draft.persistedDishId || undefined}
                initialRecipe={[...draft.recipe]}
                isReadOnly={isReadOnly}
                isSaving={isSaving}
                onDraftChange={(recipe) =>
                  setDraft((prev) => ({ ...prev, recipe, isDirty: true }))
                }
                onPrev={() => setActiveTab('identity')}
                onNext={(recipe) => {
                  setDraft((prev) => ({ ...prev, recipe }));
                  setActiveTab('allergens');
                }}
              />
            )}

            {activeTab === 'allergens' && (
              <AllergenStep
                dishId={draft.persistedDishId || undefined}
                initialRestrictions={[...draft.restrictionCodes]}
                initialConfirmed={draft.allergensConfirmed}
                isReadOnly={isReadOnly}
                isSaving={isSaving}
                onDraftChange={(restrictionCodes, allergensConfirmed) =>
                  setDraft((prev) => ({
                    ...prev,
                    restrictionCodes,
                    allergensConfirmed,
                    isDirty: true,
                  }))
                }
                onPrev={() => setActiveTab('recipe')}
                onNext={(restrictionCodes, allergensConfirmed) => {
                  setDraft((prev) => ({
                    ...prev,
                    restrictionCodes,
                    allergensConfirmed,
                  }));
                  setActiveTab('review');
                }}
              />
            )}

            {activeTab === 'review' && (
              <ReviewSubmitStep
                dish={syntheticDishForReview}
                isReadOnly={isReadOnly}
                isSubmitting={isSaving}
                onPrev={() => setActiveTab('allergens')}
                onNavigateStep={setActiveTab}
                onSaveDraft={() => handlePersist('draft')}
                onSubmitForReview={() => handlePersist('submit-review')}
              />
            )}
          </>
        )}
      </div>

      {!isReadOnly && (
        <div className="fixed bottom-[68px] left-0 right-0 md:static bg-[#FFFFFF] border-t-2 border-[#0A0A0A] md:border-t-0 p-3 md:hidden z-30 flex items-center justify-between gap-2">
          <span className="font-mono text-xs font-bold text-[#0A0A0A]">
            {draft.isDirty ? 'Cambios sin guardar' : 'Al día'}
          </span>
          <button
            type="button"
            onClick={() => handlePersist('draft')}
            disabled={isSaving || !draft.name.trim()}
            className="px-4 py-2 border-2 border-[#0A0A0A] bg-[#0A0A0A] text-[#FFFFFF] text-xs font-bold uppercase disabled:opacity-40"
          >
            Guardar borrador
          </button>
        </div>
      )}

      <BrutalistModal
        isOpen={showExitModal}
        onClose={() => setShowExitModal(false)}
        title="Salir sin guardar"
      >
        <div className="space-y-4">
          <p className="text-sm font-medium text-[#0A0A0A]">
            Tenés cambios sin guardar en esta ficha técnica. Si salís ahora, se perderán.
          </p>
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setShowExitModal(false)}
              className="px-4 py-2 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-xs font-bold uppercase"
            >
              Seguir editando
            </button>
            <button
              type="button"
              onClick={() => navigate('/cocina/platos')}
              className="px-4 py-2 border-2 border-[#0A0A0A] bg-[#DC2626] text-[#FFFFFF] text-xs font-bold uppercase"
            >
              Salir igualmente
            </button>
          </div>
        </div>
      </BrutalistModal>
    </WorkspaceLayout>
  );
};

export default ChefDishEditorPage;
