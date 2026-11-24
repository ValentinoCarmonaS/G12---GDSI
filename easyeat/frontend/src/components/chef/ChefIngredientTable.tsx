import React from 'react';
import { AlertTriangle, CheckCircle2, Package } from 'lucide-react';
import { ChefIngredientItemResponse } from '../../types/inventory';
import { BrutalistCard } from './BrutalistCard';
import { StatusBadge } from '../primitives/StatusBadge';
import { LoadingSkeleton } from '../primitives/LoadingSkeleton';
import { EmptyState } from '../primitives/EmptyState';

export interface ChefIngredientTableProps {
  items: ChefIngredientItemResponse[];
  isLoading?: boolean;
}

export const ChefIngredientTable: React.FC<ChefIngredientTableProps> = ({
  items,
  isLoading = false,
}) => {
  if (isLoading) {
    return (
      <div
        data-testid="chef-ingredients-loading"
        className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
      >
        {Array.from({ length: 6 }, (_, index) => (
          <BrutalistCard key={index} variant="default" padding="md">
            <LoadingSkeleton variant="text" lines={3} label="Cargando catálogo de cocina..." />
          </BrutalistCard>
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Package className="h-6 w-6" />}
        title="Sin materias primas"
        description="No hay materias primas disponibles en el catálogo de cocina con los filtros actuales."
      />
    );
  }

  return (
    <div
      data-testid="chef-ingredients-grid"
      className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
    >
      {items.map((item) => {
        const isInStock = item.stockStatus === 'In Stock';

        return (
          <BrutalistCard
            key={item.id}
            testId={`ingredient-card-${item.id}`}
            variant={isInStock ? 'default' : 'alert-danger'}
            padding="md"
            className="flex flex-col justify-between gap-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <h3 className="font-ee-display text-lg font-bold tracking-tight text-ee-text">
                  {item.name}
                </h3>
                <p className="mt-0.5 text-xs font-semibold uppercase tracking-wider text-ee-text-secondary">
                  {item.category}
                </p>
              </div>
              <StatusBadge
                variant={isInStock ? 'success' : 'danger'}
                icon={
                  isInStock ? (
                    <CheckCircle2 className="h-3.5 w-3.5" />
                  ) : (
                    <AlertTriangle className="h-3.5 w-3.5" />
                  )
                }
              >
                {item.stockStatus}
              </StatusBadge>
            </div>

            <div className="flex items-baseline justify-between border-t-2 border-ee-border-subtle pt-3">
              <span className="text-xs font-semibold uppercase tracking-wider text-ee-text-secondary">
                Existencia Actual
              </span>
              <span
                className={`font-ee-mono text-lg font-bold ${
                  isInStock ? 'text-ee-text' : 'text-ee-danger-fg'
                }`}
              >
                {item.currentStock} {item.unitOfMeasure}
              </span>
            </div>
          </BrutalistCard>
        );
      })}
    </div>
  );
};

export default ChefIngredientTable;
