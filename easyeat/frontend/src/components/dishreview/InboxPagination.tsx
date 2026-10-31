import React from 'react';

export interface InboxPaginationProps {
  /** Zero-based, as the API counts. Displayed one-based, as people count. */
  page: number;
  totalPages: number;
  hasNext: boolean;
  onPageChange: (page: number) => void;
}

/**
 * Page controls for the 20-per-page inbox (FR-003).
 *
 * Hidden when everything fits on one page: controls that cannot do anything are noise. The ends
 * are disabled rather than permitted-and-corrected, so the Administrator is never taken to a page
 * that does not exist — and FR-036's accurate metadata means the position shown is always real,
 * even on a page the server returned empty.
 */
export const InboxPagination: React.FC<InboxPaginationProps> = ({
  page,
  totalPages,
  hasNext,
  onPageChange,
}) => {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav
      data-testid="inbox-pagination"
      aria-label="Paginación de la bandeja"
      className="flex items-center justify-between px-4 py-3 border-t border-gray-200"
    >
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        className="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-700 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50"
      >
        Anterior
      </button>

      <span className="text-sm text-gray-600" aria-live="polite">
        Página {page + 1} de {totalPages}
      </span>

      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={!hasNext}
        className="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-700 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50"
      >
        Siguiente
      </button>
    </nav>
  );
};

export default InboxPagination;
