import React, { useState } from 'react';
import type { DishCatalogItem } from '@/types/dinerCatalog';
import type { EnrichedDish } from '@/types/catalog';
import { AllergenWarningBadge } from './AllergenWarningBadge';

export interface DishCardProps {
  dish: DishCatalogItem | EnrichedDish;
  onAddToCart?: (dish: any) => void;
  onSelectDish?: (dish: DishCatalogItem) => void;
  showWarnings?: boolean;
  isReadOnly?: boolean;
}

export const DishCard: React.FC<DishCardProps> = ({
  dish,
  onAddToCart,
  onSelectDish,
  showWarnings = true,
  isReadOnly = false,
}) => {
  const [imageError, setImageError] = useState(false);

  const photoUrl = 'photoUrl' in dish ? dish.photoUrl : null;
  const isAvailable = 'isAvailable' in dish ? dish.isAvailable !== false : dish.isActive !== false;
  const isOutOfStock = !isAvailable && !isReadOnly;
  const hasConflicts = dish.isCompliant === false && (dish.conflictingRestrictions?.length ?? 0) > 0;

  const handleCardClick = () => {
    if (onSelectDish && 'recipeIngredients' in dish) {
      onSelectDish(dish as DishCatalogItem);
    }
  };

  const handleAddToCartClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onAddToCart && !isOutOfStock && !isReadOnly) {
      onAddToCart(dish);
    }
  };

  return (
    <div
      data-testid={`dish-card-${dish.id}`}
      onClick={handleCardClick}
      className={`group relative bg-white rounded-2xl border flex flex-col justify-between overflow-hidden transition-all duration-200 cursor-pointer hover:shadow-lg ${
        isOutOfStock
          ? 'border-gray-200 grayscale opacity-60 bg-gray-50/50'
          : hasConflicts && showWarnings
          ? 'border-red-200 bg-red-50/15 hover:border-red-300'
          : 'border-gray-200 hover:border-amber-400/60'
      }`}
    >
      {/* Photo / Placeholder Container */}
      <div className="relative w-full h-44 bg-gray-100 overflow-hidden flex items-center justify-center">
        {photoUrl && !imageError ? (
          <img
            src={photoUrl}
            alt={dish.name}
            onError={() => setImageError(true)}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          <div
            data-testid={`dish-placeholder-${dish.id}`}
            className="w-full h-full flex flex-col items-center justify-center bg-linear-to-br from-amber-50 to-orange-50 text-amber-800/40 p-4"
          >
            <svg
              className="w-12 h-12 mb-1"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
              />
            </svg>
            <span className="text-2xs font-medium text-amber-900/60">EasyEat Gastronomía</span>
          </div>
        )}

        {/* Agotado Badge (strictly when out of stock and not in read-only mode) */}
        {isOutOfStock && (
          <div className="absolute top-2.5 right-2.5 bg-gray-900/85 backdrop-blur-xs text-white text-xs font-bold px-2.5 py-1 rounded-full uppercase tracking-wider shadow-sm">
            Agotado
          </div>
        )}

        {/* Price Tag Overlay on Image */}
        <div className="absolute bottom-2.5 right-2.5 bg-white/95 backdrop-blur-xs px-3 py-1 rounded-lg shadow-sm border border-gray-100">
          <span className="text-sm font-extrabold text-amber-900">
            ${dish.price.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </span>
        </div>
      </div>

      {/* Body Content */}
      <div className="p-4 flex-1 flex flex-col justify-between">
        <div>
          <h4 className="text-base font-bold text-gray-900 leading-snug mb-1 group-hover:text-amber-800 transition-colors">
            {dish.name}
          </h4>

          {dish.description && (
            <p className="text-xs text-gray-600 line-clamp-2 mb-3 leading-relaxed">
              {dish.description}
            </p>
          )}

          {/* Conflict warnings */}
          {hasConflicts && showWarnings && (
            <div className="mb-3">
              <AllergenWarningBadge conflicts={dish.conflictingRestrictions} size="sm" />
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="pt-3 border-t border-gray-100 flex items-center justify-between gap-2 mt-auto">
          <span className="text-2xs text-gray-500 font-medium truncate">
            {dish.isCompliant
              ? '✓ Apto según tu perfil'
              : hasConflicts
              ? '⚠️ Requiere confirmación'
              : ''}
          </span>

          {!isReadOnly && onAddToCart && (
            <button
              type="button"
              disabled={isOutOfStock}
              onClick={handleAddToCartClick}
              data-testid={`add-to-cart-btn-${dish.id}`}
              className={`inline-flex items-center justify-center px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-colors shadow-xs ${
                isOutOfStock
                  ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                  : hasConflicts && showWarnings
                  ? 'bg-amber-600 hover:bg-amber-700 text-white cursor-pointer'
                  : 'bg-green-600 hover:bg-green-700 text-white cursor-pointer'
              }`}
            >
              {isOutOfStock ? 'Agotado' : 'Agregar al carrito'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default DishCard;
