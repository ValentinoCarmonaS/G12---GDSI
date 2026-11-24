import React from 'react';
import { Button } from '@/components/primitives';

export interface InboxPaginationProps {
  /** Zero-based, as the API counts. Displayed one-based, as people count. */
  page: number;
  totalPages: number;
  hasNext: boolean;
  onPageChange: (page: number) => void;
}

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
      className="flex items-center justify-between gap-2 border-t border-ee-border-subtle bg-ee-surface-subtle/40 px-3 py-2.5"
    >
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        className="min-h-[44px] min-w-[44px] border border-ee-border-subtle"
      >
        Anterior
      </Button>

      <span className="font-ee-mono tabular-nums text-xs text-ee-text-secondary" aria-live="polite">
        Página {page + 1} de {totalPages}
      </span>

      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={!hasNext}
        className="min-h-[44px] min-w-[44px] border border-ee-border-subtle"
      >
        Siguiente
      </Button>
    </nav>
  );
};

export default InboxPagination;
