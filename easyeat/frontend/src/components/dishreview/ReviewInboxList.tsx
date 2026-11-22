import React from 'react';
import { ReviewInboxRow } from './ReviewInboxRow';
import { EmptyState, FeedbackState, LoadingSkeleton } from '@/components/primitives';
import type { DishReviewInboxItem, InboxTab } from '@/types/dishReview';

export interface ReviewInboxListError {
  message: string;
  /** False for a permission failure: no retry is offered, because none can succeed. */
  retryable: boolean;
}

export interface ReviewInboxListProps {
  items: DishReviewInboxItem[];
  tab: InboxTab;
  isLoading: boolean;
  error: ReviewInboxListError | null;
  selectedDishId?: string | null;
  onSelect: (dishId: string) => void;
  onRetry: () => void;
}

const EMPTY_COPY: Record<InboxTab, { title: string; body: string }> = {
  PENDING: {
    title: 'No hay platos esperando decisión',
    body: 'Cuando un Chef envíe una ficha a revisión, va a aparecer acá, con el más antiguo primero.',
  },
  FINALIZED: {
    title: 'No hay platos finalizados',
    body: 'Acá aparecerán los platos que ya recibieron una decisión.',
  },
};

export const ReviewInboxList: React.FC<ReviewInboxListProps> = ({
  items,
  tab,
  isLoading,
  error,
  selectedDishId = null,
  onSelect,
  onRetry,
}) => {
  if (isLoading) {
    return (
      <div
        data-testid="inbox-loading-skeleton"
        aria-busy="true"
        aria-live="polite"
        className="p-3"
      >
        <span className="sr-only">Cargando la bandeja de revisión…</span>
        <LoadingSkeleton variant="block" announce={false} />
        <LoadingSkeleton variant="block" announce={false} />
        <LoadingSkeleton variant="block" announce={false} />
      </div>
    );
  }

  if (error) {
    return (
      <div data-testid="inbox-error-state" className="p-3">
        <FeedbackState
          variant="danger"
          title="No se pudo cargar la bandeja"
          message={error.message}
          actionLabel={error.retryable ? 'Reintentar' : undefined}
          onAction={error.retryable ? onRetry : undefined}
        />
      </div>
    );
  }

  if (items.length === 0) {
    const copy = EMPTY_COPY[tab];
    return (
      <div data-testid="inbox-empty-state" className="p-4">
        <EmptyState title={copy.title} description={copy.body} />
      </div>
    );
  }

  return (
    <ul className="space-y-2.5 p-3" aria-label="Listado de platos en revisión">
      {items.map((item) => (
        <li key={item.dishId}>
          <ReviewInboxRow
            item={item}
            isSelected={selectedDishId === item.dishId}
            onSelect={onSelect}
          />
        </li>
      ))}
    </ul>
  );
};

export default ReviewInboxList;
