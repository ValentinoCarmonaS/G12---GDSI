import React, { useState } from 'react';
import { dishReviewApi } from '@/api/dishReviewApi';
import { MarginPriceCalculator } from './MarginPriceCalculator';
import { PublicationConfirmModal } from './PublicationConfirmModal';
import { RejectionDialog } from './RejectionDialog';
import type { ApproveDishRequest, DishReviewDetail } from '@/types/dishReview';

export interface DishDecisionActionsProps {
  detail: DishReviewDetail;
  /**
   * Called after any decision that changed the dish (price saved, approved, rejected), and also
   * after a 409 that reveals this panel's own read was stale (`dish-stale-price`,
   * `dish-already-decided`): in both cases the caller re-fetches the detail and the inbox, so the
   * Administrator is shown the server's current truth rather than left looking at a decision that
   * silently failed to apply.
   */
  onDecided: () => void;
}

const FALLBACK_PRICE_ERROR = 'No se pudo guardar el precio. Probá de nuevo.';
const FALLBACK_APPROVAL_ERROR = 'No se pudo aprobar el plato. Probá de nuevo.';
const FALLBACK_REJECTION_ERROR = 'No se pudo rechazar el plato. Probá de nuevo.';

function describeActionError(err: unknown, fallback: string): string {
  const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
  return detail ?? fallback;
}

function isErrorType(err: unknown, type: string): boolean {
  const responseType = (err as { response?: { data?: { type?: string } } })?.response?.data?.type;
  return responseType === `https://easyeat.com/errors/${type}`;
}

/**
 * The controls `DishAuditPanel` never carried (FR-009 to FR-022): setting the retail price,
 * approving/publishing and rejecting. Kept apart from `DishAuditPanel`, which stays the read-only
 * culinary file FR-006 asks for — the publication gate it displays is drawn as **status**, never
 * as enforcement, and this component is exactly where that enforcement actually happens, against
 * the server.
 *
 * Which controls render follows the state gates the contract declares (`contracts/openapi.yaml`):
 * price (re)pricing is legal from `PENDING_PRICE` or `PUBLISHED`; approval and rejection only from
 * `PENDING_PRICE`; nothing is shown for `DRAFT`, where none of the three transitions is legal.
 */
export const DishDecisionActions: React.FC<DishDecisionActionsProps> = ({ detail, onDecided }) => {
  const [priceError, setPriceError] = useState<string | null>(null);
  const [isSavingPrice, setIsSavingPrice] = useState(false);

  const [approvalError, setApprovalError] = useState<string | null>(null);
  const [isApproving, setIsApproving] = useState(false);
  const [showApproveModal, setShowApproveModal] = useState(false);

  const [rejectionError, setRejectionError] = useState<string | null>(null);
  const [isRejecting, setIsRejecting] = useState(false);
  const [showRejectDialog, setShowRejectDialog] = useState(false);

  const canSetPrice = detail.status === 'PENDING_PRICE' || detail.status === 'PUBLISHED';
  const canDecide = detail.status === 'PENDING_PRICE';

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
      // FR-025: another administrator saved first. The current price travels in the error body,
      // but re-fetching the whole detail is simpler and gives the up-to-date margin alongside it.
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
      // Someone else decided this dish first; the status this panel read no longer holds.
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

  async function handleRejectConfirm(comment: string) {
    setRejectionError(null);
    setIsRejecting(true);
    try {
      await dishReviewApi.reject(detail.dishId, { comment });
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
          {/*
            Remounted whenever the dish or its current price changes, so the calculator reseeds
            from the new values: its own seed effect runs once per mount by design (re-seeding on
            every prop change would overwrite what the Administrator is actively typing), which
            means a dish switch needs a fresh instance rather than a prop update.
          */}
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
        </div>
      )}

      <PublicationConfirmModal
        isOpen={showApproveModal}
        requiredAcknowledgements={detail.publicationGate.requiredAcknowledgements}
        onConfirm={handleApproveModalConfirm}
        onCancel={() => setShowApproveModal(false)}
      />
      {/* Keyed by dish: RejectionDialog keeps its typed comment across its own close/reopen by
          design, so switching to a different dish needs a fresh instance rather than carrying the
          previous dish's draft comment into this one. */}
      <RejectionDialog
        key={detail.dishId}
        isOpen={showRejectDialog}
        onConfirm={handleRejectConfirm}
        onCancel={() => setShowRejectDialog(false)}
      />
    </div>
  );
};

export default DishDecisionActions;
