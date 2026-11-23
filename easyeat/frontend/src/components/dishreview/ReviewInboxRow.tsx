import React from 'react';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import { StatusBadge } from '@/components/primitives';
import type { DishReviewInboxItem } from '@/types/dishReview';

export interface ReviewInboxRowProps {
  item: DishReviewInboxItem;
  isSelected?: boolean;
  onSelect: (dishId: string) => void;
}

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

export const ReviewInboxRow: React.FC<ReviewInboxRowProps> = ({
  item,
  isSelected = false,
  onSelect,
}) => (
  <button
    type="button"
    data-testid="inbox-row"
    aria-pressed={isSelected}
    onClick={() => onSelect(item.dishId)}
    className={`w-full min-h-[44px] text-left rounded-ee-md border p-3.5 backdrop-blur-[12px] [@media(prefers-reduced-transparency:reduce)]:bg-ee-surface-elevated [@media(prefers-reduced-transparency:reduce)]:backdrop-blur-none transition-[border-color,box-shadow,background-color] duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
      isSelected
        ? 'bg-[rgba(255,255,255,0.92)] border-ee-primary shadow-ee-elevated ring-1 ring-ee-primary/30'
        : 'bg-[rgba(255,255,255,0.82)] border-ee-border-subtle hover:border-ee-border-strong hover:shadow-ee-elevated'
    }`}
  >
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-sm text-ee-text truncate">{item.name}</p>
        <p className="mt-0.5 text-xs text-ee-text-secondary truncate">
          {item.category ?? 'Sin categoría'}
        </p>

        <div className="mt-2 flex items-center gap-2 flex-wrap text-xs text-ee-text-secondary">
          <span className="truncate">{item.authorName ?? 'Autor no registrado'}</span>
          {item.authorDeactivated && (
            <span
              data-testid="author-deactivated-badge"
              title="La cuenta del Chef está suspendida. No impide ninguna acción sobre el plato."
            >
              <StatusBadge variant="neutral">Cuenta suspendida</StatusBadge>
            </span>
          )}
        </div>
      </div>

      <div className="flex flex-col items-end gap-1.5 shrink-0">
        <DishStatusBadge status={item.status} />
        <span
          data-testid="inbox-row-submitted-at"
          className="font-ee-mono tabular-nums text-[11px] text-ee-text-secondary"
        >
          {formatInstant(item.submittedAt)}
        </span>
        <span
          data-testid="resubmission-indicator"
          className={`text-xs font-medium ${
            item.isResubmission ? 'text-ee-warning-fg' : 'text-ee-text-secondary'
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
