import React, { useMemo, useState } from 'react';
import { dishReviewApi } from '@/api/dishReviewApi';
import { MarginPriceCalculator } from './MarginPriceCalculator';
import { PublicationConfirmModal } from './PublicationConfirmModal';
import { RejectionDialog, type RejectionIngredientOption } from './RejectionDialog';
import { readAuditQuantity } from '@/utils/foodCostAudit';
import type {
  ApproveDishRequest,
  DishProposedChanges,
  DishReviewDetail,
} from '@/types/dishReview';

// Import Dialogs or create a simple modal for cancellation
// Assuming a structure similar to RejectionDialog might exist or can be created

export interface DishDecisionActionsProps {
  detail: DishReviewDetail;
  onDecided: () => void;
  onCancelled?: () => void;
}

const FALLBACK_PRICE_ERROR = 'No se pudo guardar el precio. Probá de nuevo.';
const FALLBACK_APPROVAL_ERROR = 'No se pudo aprobar el plato. Probá de nuevo.';
const FALLBACK_REJECTION_ERROR = 'No se pudo rechazar el plato. Probá de nuevo.';
const FALLBACK_CANCELLATION_ERROR = 'No se pudo cancelar el plato. Probá de nuevo.';

function describeActionError(err: unknown, fallback: string): string {
  const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
  return detail ?? fallback;
}

function isErrorType(err: unknown, type: string): boolean {
  const responseType = (err as { response?: { data?: { type?: string } } })?.response?.data?.type;
  return responseType === `https://easyeat.com/errors/${type}`;
}

export const DishDecisionActions: React.FC<DishDecisionActionsProps> = ({
  detail,
  onDecided,
  onCancelled,
}) => {
  const [priceError, setPriceError] = useState<string | null>(null);
  const [isSavingPrice, setIsSavingPrice] = useState(false);

  const [approvalError, setApprovalError] = useState<string | null>(null);
  const [isApproving, setIsApproving] = useState(false);
  const [showApproveModal, setShowApproveModal] = useState(false);

  const [rejectionError, setRejectionError] = useState<string | null>(null);
  const [isRejecting, setIsRejecting] = useState(false);
  const [showRejectDialog, setShowRejectDialog] = useState(false);

  const [cancellationError, setCancellationError] = useState<string | null>(null);
  const [isCancelling, setIsCancelling] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);

  const canSetPrice = detail.status === 'PENDING_PRICE' || detail.status === 'PUBLISHED';
  const canDecide = detail.status === 'PENDING_PRICE';

  /** The proposal dialog opens on the dish's current culinary file, ready to be edited (FR-042). */
  const initialProposal = useMemo<DishProposedChanges>(() => ({
    title: detail.name,
    description: detail.description,
    allergenCodes: [],
    dietaryLifestyleCodes: [],
    ingredients: (detail.foodCostAudit?.items ?? []).map((item) => ({
      ingredientId: item.ingredientId,
      quantity: readAuditQuantity(item),
    })),
  }), [detail.name, detail.description, detail.foodCostAudit]);

  const knownIngredients = useMemo<RejectionIngredientOption[]>(
    () =>
      (detail.foodCostAudit?.items ?? []).map((item) => ({
        ingredientId: item.ingredientId,
        ingredientName: item.ingredientName,
        unitOfMeasure: item.unitOfMeasure ?? undefined,
      })),
    [detail.foodCostAudit]
  );

  async function handlePriceConfirm(retailPrice: number, acknowledgeNegativeMargin: boolean) {
    setPriceError(null);
    setIsSavingPrice(true);
    try {
      await dishReviewApi.setRetailPrice(detail.dishId, {
        retailPrice,
        expectedRetailPrice: detail.retailPrice,
        acknowledgeNegativeMargin,
      });
      onDecided();
    } catch (err) {
      setPriceError(describeActionError(err, FALLBACK_PRICE_ERROR));
      if (isErrorType(err, 'dish-stale-price')) {
        onDecided();
      }
    } finally {
      setIsSavingPrice(false);
    }
  }

  async function submitApproval(request: ApproveDishRequest) {
    setApprovalError(null);
    setIsApproving(true);
    try {
      await dishReviewApi.approve(detail.dishId, request);
      setShowApproveModal(false);
      onDecided();
    } catch (err) {
      setShowApproveModal(false);
      setApprovalError(describeActionError(err, FALLBACK_APPROVAL_ERROR));
      if (isErrorType(err, 'dish-already-decided')) {
        onDecided();
      }
    } finally {
      setIsApproving(false);
    }
  }

  function handleApproveClick() {
    if (!detail.publicationGate.satisfied || isApproving) {
      return;
    }
    if (detail.publicationGate.requiredAcknowledgements.length > 0) {
      setShowApproveModal(true);
      return;
    }
    void submitApproval({});
  }

  function handleApproveModalConfirm() {
    const acks = detail.publicationGate.requiredAcknowledgements;
    void submitApproval({
      acknowledgeUnpricedIngredients: acks.includes('UNPRICED_INGREDIENTS'),
      acknowledgeNegativeMargin: acks.includes('NEGATIVE_MARGIN'),
    });
  }

  async function handleRejectConfirm(
    comment: string,
    proposedChanges: DishProposedChanges | null
  ) {
    setRejectionError(null);
    setIsRejecting(true);
    try {
      await dishReviewApi.reject(detail.dishId, {
        comment,
        ...(proposedChanges ? { proposedChanges } : {}),
      });
      setShowRejectDialog(false);
      onDecided();
    } catch (err) {
      setShowRejectDialog(false);
      setRejectionError(describeActionError(err, FALLBACK_REJECTION_ERROR));
      if (isErrorType(err, 'dish-already-decided')) {
        onDecided();
      }
    } finally {
      setIsRejecting(false);
    }
  }

  async function handleCancelConfirm() {
    setCancellationError(null);
    setIsCancelling(true);
    try {
      await dishReviewApi.cancel(detail.dishId);
      setShowCancelModal(false);
      if (onCancelled) {
        onCancelled();
      } else {
        onDecided();
      }
    } catch (err: unknown) {
      setShowCancelModal(false);
      setCancellationError(describeActionError(err, FALLBACK_CANCELLATION_ERROR));
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) {
        return;
      }
      if (isErrorType(err, 'dish-already-decided')) {
        onDecided();
      }
    } finally {
      setIsCancelling(false);
    }
  }

  if (!canSetPrice && !canDecide) {
    return null;
  }

  return (
    <div className="space-y-4 border-t border-gray-200 pt-4">
      {canSetPrice && (
        <div className="space-y-2">
          <h3 className="text-sm font-semibold text-gray-900">
            {detail.status === 'PUBLISHED' ? 'Repreciar' : 'Fijar precio de venta'}
          </h3>
          <MarginPriceCalculator
            key={`${detail.dishId}:${detail.retailPrice ?? 'unset'}`}
            margin={detail.margin}
            onConfirm={handlePriceConfirm}
          />
          {isSavingPrice && (
            <p className="text-xs text-gray-500" aria-live="polite">
              Guardando…
            </p>
          )}
          {priceError && (
            <p data-testid="price-error" role="alert" className="text-sm text-red-700">
              {priceError}
            </p>
          )}
        </div>
      )}

      {canDecide && (
        <div className="flex flex-wrap items-start gap-4">
          <div>
            <button
              type="button"
              className="rounded bg-emerald-700 px-3 py-1.5 text-sm text-white disabled:bg-gray-300"
              disabled={!detail.publicationGate.satisfied || isApproving}
              onClick={handleApproveClick}
            >
              Aprobar y Publicar
            </button>
            {approvalError && (
              <p data-testid="approval-error" role="alert" className="mt-1 text-sm text-red-700">
                {approvalError}
              </p>
            )}
          </div>

          <div>
            <button
              type="button"
              className="rounded border border-red-700 px-3 py-1.5 text-sm text-red-700 disabled:border-gray-300 disabled:text-gray-400"
              disabled={isRejecting}
              onClick={() => setShowRejectDialog(true)}
            >
              Rechazar / Devolver a cocina
            </button>
            {rejectionError && (
              <p data-testid="rejection-error" role="alert" className="mt-1 text-sm text-red-700">
                {rejectionError}
              </p>
            )}
          </div>

          <div>
            <button
              type="button"
              className="rounded border border-red-700 px-3 py-1.5 text-sm text-red-700 disabled:border-gray-300 disabled:text-gray-400"
              disabled={isCancelling}
              onClick={() => setShowCancelModal(true)}
            >
              Borrar
            </button>
            {cancellationError && (
              <p data-testid="cancellation-error" role="alert" className="mt-1 text-sm text-red-700">
                {cancellationError}
              </p>
            )}
          </div>
        </div>
      )}

      <PublicationConfirmModal
        isOpen={showApproveModal}
        requiredAcknowledgements={detail.publicationGate.requiredAcknowledgements}
        onConfirm={handleApproveModalConfirm}
        onCancel={() => setShowApproveModal(false)}
      />
      <RejectionDialog
        key={detail.dishId}
        isOpen={showRejectDialog}
        initialProposal={initialProposal}
        knownIngredients={knownIngredients}
        onConfirm={handleRejectConfirm}
        onCancel={() => setShowRejectDialog(false)}
      />
      {/* Simple confirmation modal for cancellation */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-dish-title"
            aria-describedby="cancel-dish-description"
            className="bg-white p-6 rounded shadow-lg max-w-sm w-full space-y-4"
          >
            <h3 id="cancel-dish-title" className="text-lg font-bold">¿Confirmar borrado?</h3>
            <p id="cancel-dish-description" className="text-sm text-gray-600">
              Esta acción cancelará el menú y no se podrá deshacer.
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                className="px-4 py-2 border rounded"
                disabled={isCancelling}
                onClick={() => setShowCancelModal(false)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="px-4 py-2 bg-red-700 text-white rounded disabled:opacity-50"
                disabled={isCancelling}
                onClick={handleCancelConfirm}
              >
                {isCancelling ? 'Cancelando…' : 'Confirmar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DishDecisionActions;
