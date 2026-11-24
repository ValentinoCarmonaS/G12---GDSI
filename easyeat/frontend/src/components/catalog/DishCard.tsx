import React, { useState, useContext } from 'react';
import { UtensilsCrossed, CheckCircle2, AlertTriangle, ShoppingBag } from 'lucide-react';
import type { DishCatalogItem } from '@/types/dinerCatalog';
import type { EnrichedDish } from '@/types/catalog';
import { AllergenWarningBadge } from './AllergenWarningBadge';
import { AllergenNeutralPill } from './AllergenNeutralPill';
import { CartContext } from '@/context/CartContext';
import { formatCurrency } from '@/utils/currencyUtils';
import { resolveApiMediaUrl } from '@/utils/mediaUtils';

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
  const cartContext = useContext(CartContext);

  const existingCartItem = cartContext?.items.find(
    (item) => item.dishId === dish.id || (item as any).dish?.id === dish.id
  );
  const quantityInCart = existingCartItem ? existingCartItem.quantity : 0;
  const availableStock: number | undefined = (dish as any).availableStock;
  const isMaxStockReached =
    availableStock !== undefined && quantityInCart >= availableStock;

  const photoUrl = 'photoUrl' in dish ? dish.photoUrl : null;
  const isPaused = Boolean((dish as any).pausedByChef);
  if (isPaused) {
    return null;
  }
  const isAvailable = 'isAvailable' in dish ? dish.isAvailable !== false : dish.isActive !== false;
  const isOutOfStock = !isAvailable && !isReadOnly;
  const isButtonDisabled = isOutOfStock || isMaxStockReached || isReadOnly;
  const hasConflicts = dish.isCompliant === false && (dish.conflictingRestrictions?.length ?? 0) > 0;
  const dishAllergens = 'allergens' in dish && Array.isArray(dish.allergens) ? dish.allergens : [];

  const canOpenDetail = Boolean(onSelectDish);

  const handleOpenDetail = () => {
    if (onSelectDish) {
      onSelectDish(dish as DishCatalogItem);
    }
  };

  const handleAddToCartClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onAddToCart && !isButtonDisabled) {
      onAddToCart(dish);
    }
  };

  return (
    <article
      data-testid={`dish-card-${dish.id}`}
      onClick={canOpenDetail ? handleOpenDetail : undefined}
      className={`group relative bg-[var(--ee-surface-elevated)] rounded-[var(--ee-radius-lg)] border flex flex-col justify-between overflow-hidden shadow-[var(--ee-shadow-elevated)] transition-colors duration-150 ${
        canOpenDetail ? 'cursor-pointer' : ''
      } ${
        isOutOfStock
          ? 'border-[var(--ee-border-subtle)] opacity-70 bg-[var(--ee-surface-subtle)]'
          : hasConflicts && showWarnings
          ? 'border-[var(--ee-danger-border)]'
          : 'border-[var(--ee-border-subtle)] hover:border-[var(--ee-border-strong)]'
      }`}
    >
      {/* Photo / Placeholder Container */}
      <div className="relative w-full h-44 bg-[var(--ee-surface-subtle)] overflow-hidden flex items-center justify-center">
        {photoUrl && !imageError ? (
          <img
            src={resolveApiMediaUrl(photoUrl)}
            alt={dish.name}
            loading="lazy"
            decoding="async"
            onError={() => setImageError(true)}
            className="w-full h-full object-cover"
          />
        ) : (
          <div
            data-testid={`dish-placeholder-${dish.id}`}
            className="w-full h-full flex flex-col items-center justify-center bg-[var(--ee-surface-subtle)] text-[var(--ee-text-secondary)] p-4"
          >
            <UtensilsCrossed className="w-10 h-10 mb-1.5" aria-hidden="true" />
            <span className="text-xs font-medium text-[var(--ee-text-secondary)]">
              EasyEat Gastronomía
            </span>
          </div>
        )}

        {/* Agotado Badge (strictly when out of stock and not in read-only mode) */}
        {isOutOfStock && (
          <div className="absolute top-2.5 right-2.5 bg-[var(--ee-text)] text-[var(--ee-surface-elevated)] text-xs font-bold px-2.5 py-1 rounded-full uppercase tracking-wider">
            Agotado
          </div>
        )}

        {/* Quantity in cart indicator */}
        {quantityInCart > 0 && (
          <div
            data-testid={`dish-cart-qty-${dish.id}`}
            aria-live="polite"
            className="absolute top-2.5 left-2.5 inline-flex items-center gap-1 bg-[var(--ee-primary)] text-[var(--ee-on-primary)] border border-[var(--ee-primary-border)] text-xs font-bold font-ee-mono tabular-nums px-2.5 py-1 rounded-full"
          >
            <ShoppingBag className="w-3.5 h-3.5" aria-hidden="true" />
            <span>{quantityInCart}</span>
          </div>
        )}
      </div>

      {/* Body Content */}
      <div className="p-4 flex-1 flex flex-col justify-between gap-3">
        <div className="space-y-2">
          <div className="flex items-start justify-between gap-2">
            {canOpenDetail ? (
              <button
                type="button"
                data-testid={`dish-detail-trigger-${dish.id}`}
                onClick={(e) => {
                  e.stopPropagation();
                  handleOpenDetail();
                }}
                className="text-left text-base font-bold text-[var(--ee-text)] leading-snug hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] rounded cursor-pointer"
              >
                {dish.name}
              </button>
            ) : (
              <h4 className="text-base font-bold text-[var(--ee-text)] leading-snug">
                {dish.name}
              </h4>
            )}
            <span
              data-testid={`dish-price-${dish.id}`}
              className="text-sm font-bold font-ee-mono tabular-nums text-[var(--ee-text)] whitespace-nowrap shrink-0"
            >
              {formatCurrency(dish.price)}
            </span>
          </div>

          {dish.description && (
            <p className="text-xs text-[var(--ee-text-secondary)] line-clamp-2 leading-relaxed">
              {dish.description}
            </p>
          )}

          {/* Personalized conflict warnings */}
          {hasConflicts && showWarnings && (
            <div>
              <AllergenWarningBadge conflicts={dish.conflictingRestrictions} size="sm" />
            </div>
          )}

          {/* Neutral allergen pills for guests / when automatic profile filter is inactive */}
          {!showWarnings && dishAllergens.length > 0 && (
            <div className="flex flex-wrap gap-1.5" data-testid={`dish-neutral-allergens-${dish.id}`}>
              {dishAllergens.map((allergen) => (
                <AllergenNeutralPill key={allergen} allergen={allergen} />
              ))}
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="pt-3 border-t border-[var(--ee-border-subtle)] flex items-center justify-between gap-2 mt-auto">
          <span className="inline-flex items-center gap-1 text-xs text-[var(--ee-text-secondary)] font-medium truncate">
            {dish.isCompliant ? (
              <>
                <CheckCircle2 className="w-3.5 h-3.5 text-[var(--ee-success-fg)] shrink-0" aria-hidden="true" />
                <span className="truncate">✓ Apto según tu perfil</span>
              </>
            ) : hasConflicts ? (
              <>
                <AlertTriangle className="w-3.5 h-3.5 text-[var(--ee-warning-fg)] shrink-0" aria-hidden="true" />
                <span className="truncate">⚠️ Requiere confirmación</span>
              </>
            ) : null}
          </span>

          {!isReadOnly && onAddToCart && (
            <button
              type="button"
              disabled={isButtonDisabled}
              onClick={handleAddToCartClick}
              data-testid={`add-to-cart-btn-${dish.id}`}
              data-brand-slot={!isButtonDisabled && !(hasConflicts && showWarnings) ? 'cta' : undefined}
              style={
                !isButtonDisabled && !(hasConflicts && showWarnings)
                  ? {
                      backgroundColor: 'var(--brand-primary, var(--ee-primary))',
                      color: 'var(--brand-primary-contrast, var(--ee-on-primary))',
                    }
                  : undefined
              }
              className={`min-h-[44px] inline-flex items-center justify-center px-3.5 py-2 text-xs font-semibold rounded-[var(--ee-radius-sm)] border transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] shrink-0 ${
                isOutOfStock || isMaxStockReached
                  ? 'bg-[var(--ee-surface-subtle)] text-[var(--ee-text-secondary)] border-[var(--ee-border-subtle)] cursor-not-allowed'
                  : hasConflicts && showWarnings
                  ? 'bg-[var(--ee-warning-bg)] text-[var(--ee-warning-fg)] border-[var(--ee-warning-border)] cursor-pointer'
                  : 'border-[var(--ee-border-strong)] hover:opacity-95 cursor-pointer'
              }`}
            >
              {isOutOfStock
                ? 'Agotado'
                : isMaxStockReached
                ? 'Límite alcanzado'
                : 'Agregar al carrito'}
            </button>
          )}
        </div>
      </div>
    </article>
  );
};

export default DishCard;
