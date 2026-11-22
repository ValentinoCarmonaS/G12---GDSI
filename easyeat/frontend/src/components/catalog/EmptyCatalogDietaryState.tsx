import React from 'react';
import { SearchX } from 'lucide-react';
import { BentoCard } from '@/components/diner/BentoCard';
import { Button } from '@/components/ui/Button';

export interface EmptyCatalogDietaryStateProps {
  onViewFullMenu: () => void;
}

export const EmptyCatalogDietaryState: React.FC<EmptyCatalogDietaryStateProps> = ({
  onViewFullMenu,
}) => {
  return (
    <BentoCard
      variant="elevated"
      padding="lg"
      radius="lg"
      data-testid="empty-catalog-dietary-state"
      className="text-center max-w-lg mx-auto my-8"
    >
      <div className="w-14 h-14 rounded-full bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] flex items-center justify-center mx-auto mb-4">
        <SearchX className="h-7 w-7" aria-hidden="true" />
      </div>

      <h3 className="text-lg font-bold text-[var(--ee-text)] mb-2">
        No encontramos platos que coincidan con tu dieta
      </h3>

      <p className="text-sm text-[var(--ee-text-secondary)] mb-6 leading-relaxed">
        Este local no dispone de platos que satisfagan simultáneamente todas las restricciones de tu
        perfil. Podés desactivar temporalmente el filtro automático para ver la carta completa con
        advertencias visuales.
      </p>

      <Button
        type="button"
        variant="primary"
        onClick={onViewFullMenu}
        data-testid="view-full-menu-button"
        className="min-h-[44px]"
      >
        Ver menú completo sin filtros de perfil
      </Button>
    </BentoCard>
  );
};

export default EmptyCatalogDietaryState;
