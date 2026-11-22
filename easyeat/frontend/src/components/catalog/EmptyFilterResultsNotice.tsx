import React from 'react';
import { SlidersHorizontal } from 'lucide-react';
import { BentoCard } from '@/components/diner/BentoCard';
import { Button } from '@/components/ui/Button';

export interface EmptyFilterResultsNoticeProps {
  onClearFilters: () => void;
}

export const EmptyFilterResultsNotice: React.FC<EmptyFilterResultsNoticeProps> = ({
  onClearFilters,
}) => {
  return (
    <BentoCard
      variant="subtle"
      padding="lg"
      radius="lg"
      data-testid="empty-filter-results-notice"
      className="text-center space-y-4 my-6"
    >
      <div className="w-12 h-12 mx-auto rounded-full bg-[var(--ee-surface-elevated)] border border-[var(--ee-border-subtle)] text-[var(--ee-text)] flex items-center justify-center">
        <SlidersHorizontal className="w-5 h-5" aria-hidden="true" />
      </div>
      <div className="max-w-md mx-auto space-y-1">
        <h4 className="text-base font-bold text-[var(--ee-text)]">
          No se encontraron platos
        </h4>
        <p className="text-xs text-[var(--ee-text-secondary)] leading-relaxed">
          No encontramos platos que coincidan con los filtros aplicados. Probá ajustando las categorías, el rango de precios o los términos de búsqueda.
        </p>
      </div>
      <div>
        <Button
          type="button"
          variant="primary"
          data-testid="empty-filter-clear-btn"
          onClick={onClearFilters}
          className="min-h-[44px]"
        >
          Limpiar filtros
        </Button>
      </div>
    </BentoCard>
  );
};
