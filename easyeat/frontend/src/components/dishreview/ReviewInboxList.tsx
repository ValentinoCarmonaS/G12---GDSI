import React from 'react';
import { ReviewInboxRow } from './ReviewInboxRow';
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
  onSelect: (dishId: string) => void;
  onRetry: () => void;
}

/**
 * Per-view empty wording (FR-005, FR-028). The three views answer different questions, so an
 * empty one means something different each time and a shared "no hay nada" would be vaguer than
 * the truth.
 */
const EMPTY_COPY: Record<InboxTab, { title: string; body: string }> = {
  PENDING: {
    title: 'No hay platos esperando decisión',
    body: 'Cuando un Chef envíe una ficha a revisión, va a aparecer acá, con el más antiguo primero.',
  },
  PUBLISHED: {
    title: 'Todavía no hay platos publicados',
    body: 'Los platos aprobados aparecen acá y quedan visibles en el catálogo del comensal.',
  },
  HISTORY: {
    title: 'Todavía no se revisó ningún plato',
    body: 'Acá quedan los platos que ya pasaron por una decisión, aprobados o rechazados, en cualquier estado actual.',
  },
};

/**
 * The FIFO list, with its three mutually exclusive states.
 *
 * The order of the branches is the point. Loading wins over empty, because a skeleton and an
 * empty state make opposite claims about whether anything awaits review. An error wins over the
 * rows, because rows left on screen beside an error still look like the server's answer when they
 * are not. Items are rendered in the order received and never re-sorted here: FR-003's FIFO is
 * established by the query, and a second ordering in the view could only disagree with it.
 */
export const ReviewInboxList: React.FC<ReviewInboxListProps> = ({
  items,
  tab,
  isLoading,
  error,
  onSelect,
  onRetry,
}) => {
  if (isLoading) {
    return (
      <div data-testid="inbox-loading-skeleton" aria-busy="true" aria-live="polite" className="divide-y divide-gray-100">
        <span className="sr-only">Cargando la bandeja de revisión…</span>
        {[0, 1, 2, 3].map((row) => (
          <div key={row} className="px-4 py-3 animate-pulse">
            <div className="h-4 w-1/3 bg-gray-200 rounded" />
            <div className="mt-2 h-3 w-1/5 bg-gray-100 rounded" />
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div
        data-testid="inbox-error-state"
        role="alert"
        className="px-4 py-8 text-center border border-red-200 bg-red-50 rounded"
      >
        <p className="text-sm text-red-800">{error.message}</p>
        {error.retryable && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-3 px-3 py-1.5 text-sm rounded border border-red-300 text-red-800 hover:bg-red-100"
          >
            Reintentar
          </button>
        )}
      </div>
    );
  }

  if (items.length === 0) {
    const copy = EMPTY_COPY[tab];
    return (
      <div data-testid="inbox-empty-state" className="px-4 py-12 text-center">
        <p className="font-medium text-gray-900">{copy.title}</p>
        <p className="mt-1 text-sm text-gray-500 max-w-md mx-auto">{copy.body}</p>
      </div>
    );
  }

  return (
    <ul className="divide-y divide-gray-100">
      {items.map((item) => (
        <li key={item.dishId}>
          <ReviewInboxRow item={item} onSelect={onSelect} />
        </li>
      ))}
    </ul>
  );
};

export default ReviewInboxList;
