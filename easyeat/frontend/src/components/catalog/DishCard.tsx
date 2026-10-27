import React from 'react';
import { EnrichedDish } from '../../types/catalog';
import { AllergenWarningBadge } from './AllergenWarningBadge';

export interface DishCardProps {
  dish: EnrichedDish;
  onAddToCart: (dish: EnrichedDish) => void;
  showWarnings?: boolean;
}

export const DishCard: React.FC<DishCardProps> = ({
  dish,
  onAddToCart,
  showWarnings = true,
}) => {
  const hasConflicts = dish.isCompliant === false && dish.conflictingRestrictions?.length > 0;

  return (
    <div
      data-testid={`dish-card-${dish.id}`}
      className={`bg-white rounded-xl border p-5 flex flex-col justify-between transition-shadow hover:shadow-md ${
        hasConflicts && showWarnings
          ? 'border-red-200 bg-red-50/20'
          : 'border-gray-200'
      }`}
    >
      <div>
        <div className="flex items-start justify-between gap-3 mb-1.5">
          <h4 className="text-base font-bold text-gray-900 leading-snug">
            {dish.name}
          </h4>
          <span className="text-base font-extrabold text-amber-700 whitespace-nowrap">
            ${dish.price.toLocaleString('es-AR', { minimumFractionDigits: 2 })}
          </span>
        </div>

        {dish.description && (
          <p className="text-xs text-gray-600 line-clamp-2 mb-3 leading-relaxed">
            {dish.description}
          </p>
        )}

        {/* FR-020: Prominent inline visual warning badges on non-compliant dishes */}
        {hasConflicts && showWarnings && (
          <div className="mb-3">
            <AllergenWarningBadge conflicts={dish.conflictingRestrictions} size="sm" />
          </div>
        )}
      </div>

      <div className="pt-3 border-t border-gray-100 flex items-center justify-between gap-2 mt-auto">
        <span className="text-xs text-gray-500 font-medium">
          {dish.isCompliant
            ? '✓ Apto según tu perfil'
            : hasConflicts
            ? '⚠️ Requiere confirmación'
            : ''}
        </span>
        <button
          type="button"
          onClick={() => onAddToCart(dish)}
          data-testid={`add-to-cart-btn-${dish.id}`}
          className={`inline-flex items-center justify-center px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-colors shadow-xs ${
            hasConflicts && showWarnings
              ? 'bg-amber-600 hover:bg-amber-700 text-white'
              : 'bg-green-600 hover:bg-green-700 text-white'
          }`}
        >
          Agregar al carrito
        </button>
      </div>
    </div>
  );
};

export default DishCard;
