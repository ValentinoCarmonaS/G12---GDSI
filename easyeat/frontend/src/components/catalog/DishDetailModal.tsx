import React, { useState, useEffect, useRef, useContext } from 'react';
import { Clock, PauseCircle, AlertTriangle, ShoppingCart, Minus, Plus } from 'lucide-react';
import type { DishCatalogItem } from '@/types/dinerCatalog';
import { AllergenNeutralPill } from './AllergenNeutralPill';
import { AccessibleModal } from '@/components/diner/AccessibleModal';
import { Button } from '@/components/ui/Button';
import { CartContext } from '@/context/CartContext';
import { formatCurrency, calculateLineTotal } from '@/utils/currencyUtils';
import { resolveApiMediaUrl } from '@/utils/mediaUtils';

export interface DishDetailModalProps {
  dish: (DishCatalogItem & { availableStock?: number }) | null;
  isOpen: boolean;
  onClose: () => void;
  isReadOnly?: boolean;
  nextOpeningSchedule?: string | null;
  onAddToCart?: (dish: DishCatalogItem, quantity: number) => void;
}

export const DishDetailModal: React.FC<DishDetailModalProps> = ({
  dish,
  isOpen,
  onClose,
  isReadOnly = false,
  nextOpeningSchedule,
  onAddToCart,
}) => {
  const [imageError, setImageError] = useState(false);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [selectedQuantity, setSelectedQuantity] = useState<number>(1);
  const [stockErrorWarning, setStockErrorWarning] = useState<string | null>(null);
  const cartContext = useContext(CartContext);
  const savedScrollYRef = useRef<number>(0);

  // Capture window.scrollY upon modal opening and restore it upon close (INV-1, FR-004, SC-002, EC-021)
  useEffect(() => {
    if (isOpen && typeof window !== 'undefined') {
      savedScrollYRef.current = window.scrollY;
    }
  }, [isOpen]);

  const safelyRestoreScroll = () => {
    try {
      if (typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
        window.scrollTo({ top: savedScrollYRef.current, behavior: 'instant' });
      }
    } catch {
      // Ignore environments where window.scrollTo is not implemented (e.g. jsdom)
    }
  };

  useEffect(() => {
    return () => {
      safelyRestoreScroll();
    };
  }, []);

  const handleClose = () => {
    safelyRestoreScroll();
    onClose();
  };

  useEffect(() => {
    setSelectedQuantity(1);
    setStockErrorWarning(null);
    setImageError(false);
    setImageLoaded(false);
  }, [dish?.id, isOpen]);

  if (!isOpen || !dish) {
    return null;
  }

  const existingCartItem = cartContext?.items.find((item) => item.dishId === dish.id);
  const quantityInCart = existingCartItem ? existingCartItem.quantity : 0;

  const effectiveAvailableStock: number | undefined =
    dish.availableStock !== undefined
      ? dish.availableStock
      : dish.isAvailable === false
      ? 0
      : undefined;

  const remainingStock: number | undefined =
    effectiveAvailableStock !== undefined
      ? Math.max(0, effectiveAvailableStock - quantityInCart)
      : undefined;

  const isPaused = Boolean((dish as any).pausedByChef);
  const isOutOfStock =
    dish.isAvailable === false ||
    isPaused ||
    (effectiveAvailableStock !== undefined && effectiveAvailableStock <= 0);

  const isMaxStockReached = remainingStock !== undefined && remainingStock <= 0;
  const canAddToCart = !isReadOnly && !isOutOfStock && !isMaxStockReached && !isPaused;

  const minQuantity = 1;
  const isDecrementDisabled = selectedQuantity <= minQuantity || !canAddToCart;
  const isIncrementDisabled = !canAddToCart;

  const provisionalSubtotal = calculateLineTotal(dish.price, selectedQuantity);

  const handleAddToCart = () => {
    if (!canAddToCart) return;

    if (remainingStock !== undefined && selectedQuantity > remainingStock) {
      setStockErrorWarning(
        `Disponibilidad insuficiente: solo quedan ${remainingStock} unidad${
          remainingStock === 1 ? '' : 'es'
        } disponible${remainingStock === 1 ? '' : 's'} (${effectiveAvailableStock} en cocina)`
      );
      return;
    }

    setStockErrorWarning(null);
    if (onAddToCart) {
      onAddToCart(dish, selectedQuantity);
    } else if (cartContext) {
      cartContext.addItem(dish, selectedQuantity);
    }
    onClose();
  };

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={handleClose}
      title={dish.name}
      subtitle={dish.category}
      size="md"
      testId="dish-detail-modal"
      closeButtonTestId="modal-close-btn"
      closeButtonLabel="Cerrar modal"
      footer={
        <div className="flex items-center justify-between gap-3 w-full">
          <Button type="button" variant="secondary" onClick={handleClose} className="min-h-[44px]">
            Cerrar
          </Button>
          <button
            type="button"
            data-testid="add-to-cart-btn"
            data-brand-slot={canAddToCart ? 'cta' : undefined}
            disabled={!canAddToCart}
            onClick={handleAddToCart}
            style={
              canAddToCart
                ? {
                    backgroundColor: 'var(--brand-primary, var(--ee-primary))',
                    color: 'var(--brand-primary-contrast, var(--ee-on-primary))',
                  }
                : undefined
            }
            className="min-h-[44px] px-5 py-2 text-sm font-bold rounded-[var(--ee-radius-sm)] border border-[var(--ee-border-strong)] transition-colors duration-150 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer inline-flex items-center gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
          >
            <ShoppingCart className="w-4 h-4" aria-hidden="true" />
            <span>Agregar al Carrito</span>
          </button>
        </div>
      }
    >
      <div data-testid="dish-detail-scrollable-content" className="space-y-4">
        {/* Modal Header Image - Completely omitted if no photoUrl or error */}
        {dish.photoUrl && !imageError && (
          <div
            data-testid="dish-detail-photo-container"
            className="relative w-full h-56 bg-[var(--ee-surface-subtle)] rounded-[var(--ee-radius-md)] flex items-center justify-center overflow-hidden border border-[var(--ee-border-subtle)]"
          >
            {!imageLoaded && (
              <div
                data-testid="dish-detail-image-skeleton"
                className="absolute inset-0 w-full h-full bg-[var(--ee-surface-subtle)] flex items-center justify-center"
              >
                <div className="w-8 h-8 rounded-full border-2 border-[var(--ee-primary-border)] border-t-transparent animate-spin" />
              </div>
            )}

            <img
              src={resolveApiMediaUrl(dish.photoUrl)}
              alt={dish.name}
              onLoad={() => setImageLoaded(true)}
              onError={() => setImageError(true)}
              className={`w-full h-full object-cover transition-opacity duration-150 ${
                imageLoaded ? 'opacity-100' : 'opacity-0'
              }`}
            />

            {isOutOfStock && (
              <div className="absolute top-3 left-3 bg-[var(--ee-text)] text-[var(--ee-surface-elevated)] text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider z-10">
                Agotado
              </div>
            )}
          </div>
        )}

        {/* Outside Hours Notice */}
        {isReadOnly && (
          <div
            data-testid="outside-hours-modal-notice"
            className="bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] p-3 rounded-[var(--ee-radius-sm)] text-xs flex items-center gap-2"
          >
            <Clock className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>
              {nextOpeningSchedule
                ? `Establecimiento cerrado. ${nextOpeningSchedule}. Catálogo en modo informativo.`
                : 'Establecimiento fuera de su horario de atención. Catálogo en modo informativo.'}
            </span>
          </div>
        )}

        {/* Dish Unavailable or Paused Banner */}
        {isPaused ? (
          <div
            data-testid="dish-paused-banner"
            className="bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] p-3 rounded-[var(--ee-radius-sm)] text-xs font-semibold flex items-center gap-2"
          >
            <PauseCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>Pausado por cocina</span>
          </div>
        ) : (
          isOutOfStock && (
            <div
              data-testid="dish-unavailable-banner"
              className="bg-[var(--ee-danger-bg)] border border-[var(--ee-danger-border)] text-[var(--ee-danger-fg)] p-3 rounded-[var(--ee-radius-sm)] text-xs font-semibold flex items-center gap-2"
            >
              <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span>Plato no disponible</span>
            </div>
          )
        )}

        {/* Price row */}
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs font-semibold uppercase tracking-wider text-[var(--ee-text-secondary)]">
            Precio unitario
          </span>
          <span
            data-testid="dish-detail-price"
            className="text-xl font-extrabold font-ee-mono tabular-nums text-[var(--ee-text)] whitespace-nowrap"
          >
            {formatCurrency(dish.price)}
          </span>
        </div>

        {/* Description */}
        {dish.description && (
          <p
            data-testid="dish-detail-description"
            className="text-sm text-[var(--ee-text-secondary)] leading-relaxed break-words"
          >
            {dish.description}
          </p>
        )}

        {/* Recipe Ingredients */}
        <div className="pt-3 border-t border-[var(--ee-border-subtle)]">
          <h4 className="text-xs font-bold text-[var(--ee-text)] uppercase tracking-wider mb-1.5">
            Ingredientes
          </h4>
          {dish.recipeIngredients && dish.recipeIngredients.length > 0 ? (
            <p className="text-xs text-[var(--ee-text-secondary)] leading-relaxed">
              {dish.recipeIngredients.join(', ')}
            </p>
          ) : (
            <p
              data-testid="missing-ingredients-notice"
              className="text-xs text-[var(--ee-text-secondary)] italic bg-[var(--ee-surface-subtle)] px-3 py-2 rounded-[var(--ee-radius-sm)] border border-[var(--ee-border-subtle)]"
            >
              Lista de componentes no disponible
            </p>
          )}
        </div>

        {/* Neutral Allergen Labels */}
        <div className="pt-3 border-t border-[var(--ee-border-subtle)]">
          <h4 className="text-xs font-bold text-[var(--ee-text)] uppercase tracking-wider mb-1.5">
            Alérgenos
          </h4>
          {dish.allergens && dish.allergens.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {dish.allergens.map((allergen) => (
                <AllergenNeutralPill key={allergen} allergen={allergen} />
              ))}
            </div>
          ) : (
            <p
              data-testid="no-allergens-declared-notice"
              className="text-xs text-[var(--ee-text-secondary)] italic"
            >
              No contiene alérgenos declarados
            </p>
          )}
        </div>

        {/* Quantity Selector & Provisional Subtotal */}
        <div className="pt-3 border-t border-[var(--ee-border-subtle)] flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-[var(--ee-text)] uppercase tracking-wider">
              Cantidad
            </span>
            <div data-testid="quantity-selector" className="flex items-center gap-2">
              <button
                type="button"
                data-testid="quantity-decrement-btn"
                disabled={isDecrementDisabled}
                onClick={() => {
                  setSelectedQuantity((q) => Math.max(minQuantity, q - 1));
                  setStockErrorWarning(null);
                }}
                className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-[var(--ee-radius-sm)] border border-[var(--ee-border-strong)] bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] hover:bg-[var(--ee-surface-subtle)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                aria-label="Disminuir cantidad"
              >
                <Minus className="w-4 h-4" aria-hidden="true" />
              </button>
              <span
                data-testid="quantity-value"
                className="w-8 text-center font-bold font-ee-mono tabular-nums text-[var(--ee-text)] text-sm"
              >
                {selectedQuantity}
              </span>
              <button
                type="button"
                data-testid="quantity-increment-btn"
                disabled={isIncrementDisabled}
                onClick={() => {
                  setSelectedQuantity((q) => q + 1);
                  setStockErrorWarning(null);
                }}
                className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-[var(--ee-radius-sm)] border border-[var(--ee-border-strong)] bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] hover:bg-[var(--ee-surface-subtle)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
                aria-label="Aumentar cantidad"
              >
                <Plus className="w-4 h-4" aria-hidden="true" />
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between py-2 px-3 bg-[var(--ee-surface-subtle)] rounded-[var(--ee-radius-sm)] border border-[var(--ee-border-subtle)]">
            <span className="text-xs font-medium text-[var(--ee-text-secondary)]">
              Subtotal provisorio:
            </span>
            <span
              data-testid="dish-detail-subtotal"
              className="text-sm font-extrabold font-ee-mono tabular-nums text-[var(--ee-text)]"
            >
              {formatCurrency(provisionalSubtotal)}
            </span>
          </div>

          {stockErrorWarning && (
            <div
              data-testid="stock-insufficient-warning"
              role="alert"
              className="text-xs font-semibold text-[var(--ee-danger-fg)] bg-[var(--ee-danger-bg)] px-3 py-2 rounded-[var(--ee-radius-sm)] border border-[var(--ee-danger-border)]"
            >
              {stockErrorWarning}
            </div>
          )}

          {isMaxStockReached && (
            <div
              data-testid="stock-limit-warning"
              role="status"
              className="text-xs font-semibold text-[var(--ee-warning-fg)] bg-[var(--ee-warning-bg)] px-3 py-2 rounded-[var(--ee-radius-sm)] border border-[var(--ee-warning-border)]"
            >
              Límite de disponibilidad alcanzado ({effectiveAvailableStock} en cocina)
            </div>
          )}
        </div>
      </div>
    </AccessibleModal>
  );
};

export default DishDetailModal;
