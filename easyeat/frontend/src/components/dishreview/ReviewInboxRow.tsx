import React from 'react';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import type { DishReviewInboxItem } from '@/types/dishReview';

export interface ReviewInboxRowProps {
  item: DishReviewInboxItem;
  onSelect: (dishId: string) => void;
}

/** FR-004's fields: the submission instant in the reader's locale, or nothing to show. */
function formatInstant(value: string | null): string {
  if (!value) {
    return 'Sin fecha de envío';
  }
  return new Date(value).toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * One inbox row: every field FR-004 names, and the two degraded cases spelled out.
 *
 * An unattributed dish reads "Autor no registrado" rather than leaving the column blank, because
 * a blank cell is indistinguishable from a rendering fault (FR-027). A suspended author is marked
 * and the row stays fully interactive, because FR-026 is explicit that the flag is informational
 * and never blocks an administrative action.
 */
export const ReviewInboxRow: React.FC<ReviewInboxRowProps> = ({ item, onSelect }) => (
  <button
    type="button"
    data-testid="inbox-row"
    onClick={() => onSelect(item.dishId)}
    className="w-full text-left px-4 py-3 border-b border-gray-100 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-inset"
  >
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <p className="font-medium text-gray-900 truncate">{item.name}</p>
        <p className="text-sm text-gray-500">{item.category ?? 'Sin categoría'}</p>

        <p className="mt-1 text-sm text-gray-600 flex items-center gap-2 flex-wrap">
          <span>{item.authorName ?? 'Autor no registrado'}</span>
          {item.authorDeactivated && (
            <span
              data-testid="author-deactivated-badge"
              title="La cuenta del Chef está suspendida. No impide ninguna acción sobre el plato."
              className="inline-flex items-center px-1.5 py-0.5 rounded text-xs bg-zinc-100 text-zinc-600 border border-zinc-300"
            >
              Cuenta suspendida
            </span>
          )}
        </p>
      </div>

      <div className="flex flex-col items-end gap-1 shrink-0">
        <DishStatusBadge status={item.status} />
        <span data-testid="inbox-row-submitted-at" className="text-xs text-gray-500">
          {formatInstant(item.submittedAt)}
        </span>
        <span
          data-testid="resubmission-indicator"
          className={`text-xs font-medium ${
            item.isResubmission ? 'text-orange-700' : 'text-gray-500'
          }`}
        >
          {item.isResubmission
            ? `Reenvío (${item.reviewCycleCount} ${
                item.reviewCycleCount === 1 ? 'rechazo previo' : 'rechazos previos'
              })`
            : 'Envío inicial'}
        </span>
      </div>
    </div>
  </button>
);

export default ReviewInboxRow;
