import React from 'react';
import { ShieldCheck } from 'lucide-react';
import { BentoCard } from '@/components/diner/BentoCard';

export interface CatalogMasterToggleProps {
  activeRestrictionsCount: number;
  isFilterActive: boolean;
  onToggle: (active: boolean) => void;
}

export const CatalogMasterToggle: React.FC<CatalogMasterToggleProps> = ({
  activeRestrictionsCount,
  isFilterActive,
  onToggle,
}) => {
  // FR-025: For diners with zero configured restrictions, completely hide the master filter toggle
  if (activeRestrictionsCount === 0) {
    return null;
  }

  return (
    <BentoCard
      variant="elevated"
      padding="sm"
      radius="md"
      data-testid="catalog-master-toggle-container"
      className="flex items-center justify-between gap-4"
    >
      <div className="flex items-start gap-3">
        <div className="p-2 rounded-[var(--ee-radius-sm)] bg-[var(--ee-primary)] text-[var(--ee-on-primary)] border border-[var(--ee-primary-border)] shrink-0">
          <ShieldCheck className="w-4 h-4" aria-hidden="true" />
        </div>
        <div className="flex flex-col">
          <span className="text-sm font-semibold text-[var(--ee-text)]">
            Filtrar por mis restricciones de perfil ({activeRestrictionsCount})
          </span>
          <span className="text-xs text-[var(--ee-text-secondary)]">
            {isFilterActive
              ? 'Mostrando únicamente platos aptos para tu dieta'
              : 'Mostrando menú completo con advertencias visuales'}
          </span>
        </div>
      </div>

      <button
        type="button"
        role="switch"
        aria-checked={isFilterActive}
        onClick={() => onToggle(!isFilterActive)}
        data-testid="catalog-master-toggle-button"
        className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full border transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] focus-visible:ring-offset-2 ${
          isFilterActive
            ? 'bg-[var(--ee-primary-border)] border-[var(--ee-primary-border)]'
            : 'bg-[var(--ee-surface-subtle)] border-[var(--ee-border-strong)]'
        }`}
      >
        <span className="sr-only">Activar o desactivar filtro dietario automático</span>
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-[var(--ee-surface-elevated)] border border-[var(--ee-border-subtle)] transition-transform duration-150 ${
            isFilterActive ? 'translate-x-5' : 'translate-x-1'
          }`}
        />
      </button>
    </BentoCard>
  );
};

export default CatalogMasterToggle;
