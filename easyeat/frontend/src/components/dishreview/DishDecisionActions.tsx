import React, { useMemo, useState } from 'react';
import { dishReviewApi } from '@/api/dishReviewApi';
import { AccessibleModal } from '@/components/diner';
import { Button } from '@/components/primitives';
import { MarginPriceCalculator } from './MarginPriceCalculator';
import { PublicationConfirmModal } from './PublicationConfirmModal';
import { RejectionDialog, type RejectionIngredientOption } from './RejectionDialog';
import { readAuditQuantity } from '@/utils/foodCostAudit';
import type {
  ApproveDishRequest,
  DishProposedChanges,
  DishReviewDetail,
} from '@/types/dishReview';

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

  const initialProposal = useMemo<DishProposedChanges>(
    () => ({
      title: detail.name,
      description: detail.description,
      allergenCodes: [],
      dietaryLifestyleCodes: [],
      ingredients: (detail.foodCostAudit?.items ?? []).map((item) => ({
        ingredientId: item.ingredientId,
        quantity: readAuditQuantity(item),
      })),
    }),
    [detail.name, detail.description, detail.foodCostAudit]
  );

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
    <div className="space-y-5 border-t border-ee-border-subtle pt-5">
      {canSetPrice && (
        <div className="space-y-3">
          <h3 className="text-sm font-semibold text-ee-text">
            {detail.status === 'PUBLISHED' ? 'Repreciar' : 'Fijar precio de venta'}
          </h3>
          <MarginPriceCalculator
            key={`${detail.dishId}:${detail.retailPrice ?? 'unset'}`}
            margin={detail.margin}
            onConfirm={handlePriceConfirm}
          />
          {isSavingPrice && (
            <p className="text-xs text-ee-text-secondary" aria-live="polite">
              Guardando…
            </p>
          )}
          {priceError && (
            <p data-testid="price-error" role="alert" className="text-sm text-ee-danger-fg">
              {priceError}
            </p>
          )}
        </div>
      )}

      {canDecide && (
        <div className="flex flex-wrap items-start gap-3">
          <div>
            <Button
              type="button"
              variant="primary"
              size="md"
              disabled={!detail.publicationGate.satisfied || isApproving}
              onClick={handleApproveClick}
            >
              Aprobar y Publicar
            </Button>
            {approvalError && (
              <p
                data-testid="approval-error"
                role="alert"
                className="mt-1.5 text-sm text-ee-danger-fg"
              >
                {approvalError}
              </p>
            )}
          </div>

          <div>
            <Button
              type="button"
              variant="secondary"
              size="md"
              disabled={isRejecting}
              onClick={() => setShowRejectDialog(true)}
            >
              Rechazar / Devolver a cocina
            </Button>
            {rejectionError && (
              <p
                data-testid="rejection-error"
                role="alert"
                className="mt-1.5 text-sm text-ee-danger-fg"
              >
                {rejectionError}
              </p>
            )}
          </div>

          <div>
            <Button
              type="button"
              variant="danger"
              size="md"
              disabled={isCancelling}
              onClick={() => setShowCancelModal(true)}
            >
              Borrar
            </Button>
            {cancellationError && (
              <p
                data-testid="cancellation-error"
                role="alert"
                className="mt-1.5 text-sm text-ee-danger-fg"
              >
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
      <AccessibleModal
        isOpen={showCancelModal}
        onClose={() => setShowCancelModal(false)}
        title="¿Confirmar borrado?"
        subtitle="Acción irreversible"
        size="sm"
        footer={
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              size="md"
              disabled={isCancelling}
              onClick={() => setShowCancelModal(false)}
            >
              Cancelar
            </Button>
            <Button
              type="button"
              variant="danger"
              size="md"
              disabled={isCancelling}
              onClick={handleCancelConfirm}
            >
              {isCancelling ? 'Cancelando…' : 'Confirmar'}
            </Button>
          </div>
        }
      >
        <p className="text-sm text-ee-text-secondary">
          Esta acción cancelará el menú y no se podrá deshacer.
        </p>
      </AccessibleModal>
    </div>
  );
};

export default DishDecisionActions;
