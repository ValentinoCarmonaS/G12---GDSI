import React, { useState, useEffect, useRef, useContext } from 'react';
import type { DishCatalogItem } from '@/types/dinerCatalog';
import { AllergenWarningBadge } from './AllergenWarningBadge';
import { CartContext } from '@/context/CartContext';

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
  const cartContext = useContext(CartContext);
  const savedScrollYRef = useRef<number>(0);

  // Capture window.scrollY upon modal opening and restore it upon close (INV-1, FR-004, SC-002, EC-021)
  useEffect(() => {
    if (isOpen) {
      if (typeof window !== 'undefined') {
        savedScrollYRef.current = window.scrollY;
      }
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

  const isOutOfStock =
    dish.isAvailable === false ||
    (effectiveAvailableStock !== undefined && effectiveAvailableStock <= 0);

  const isMaxStockReached = remainingStock !== undefined && remainingStock <= 0;
  const canAddToCart = !isReadOnly && !isOutOfStock && !isMaxStockReached;

  const minQuantity = 1;
  const maxQuantity = remainingStock !== undefined ? Math.max(minQuantity, remainingStock) : 99;

  const isDecrementDisabled = selectedQuantity <= minQuantity || !canAddToCart;
  const isIncrementDisabled =
    !canAddToCart || (remainingStock !== undefined && selectedQuantity >= remainingStock);

  const hasConflicts = dish.isCompliant === false && (dish.conflictingRestrictions?.length ?? 0) > 0;

  const handleAddToCart = () => {
    if (!canAddToCart) return;
    if (onAddToCart) {
      onAddToCart(dish, selectedQuantity);
    } else if (cartContext) {
      cartContext.addItem(dish, selectedQuantity);
    }
    onClose();
  };

  return (
    <div
      data-testid="dish-detail-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs transition-opacity animate-in fade-in"
      onClick={handleClose}
    >
      <div
        className="bg-white rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header Image (INV-2, INV-3, FR-003, FR-005, SC-004, EC-001, EC-002) - Completely omitted if no photoUrl or error */}
        {dish.photoUrl && !imageError ? (
          <div
            data-testid="dish-detail-photo-container"
            className="relative w-full h-56 bg-gray-100 flex items-center justify-center overflow-hidden"
          >
            {/* Animated Skeleton while downloading image in slow networks */}
            {!imageLoaded && (
              <div
                data-testid="dish-detail-image-skeleton"
                className="absolute inset-0 w-full h-full bg-linear-to-r from-gray-200 via-gray-100 to-gray-200 animate-pulse flex items-center justify-center"
              >
                <div className="w-8 h-8 rounded-full border-2 border-amber-600 border-t-transparent animate-spin" />
              </div>
            )}

            <img
              src={dish.photoUrl}
              alt={dish.name}
              onLoad={() => setImageLoaded(true)}
              onError={() => setImageError(true)}
              className={`w-full h-full object-cover transition-opacity duration-300 ${
                imageLoaded ? 'opacity-100' : 'opacity-0'
              }`}
            />

            {/* Close button inside image header */}
            <button
              type="button"
              onClick={handleClose}
              data-testid="modal-close-btn"
              className="absolute top-3 right-3 bg-black/40 hover:bg-black/60 text-white rounded-full p-2 transition-colors cursor-pointer z-10"
              aria-label="Cerrar modal"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            {/* Availability badge */}
            {isOutOfStock && (
              <div className="absolute top-3 left-3 bg-gray-900/90 text-white text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider z-10">
                Agotado
              </div>
            )}
          </div>
        ) : (
          /* Header close button when photo container is omitted */
          <div className="relative w-full flex justify-end p-4 pb-0">
            <button
              type="button"
              onClick={handleClose}
              data-testid="modal-close-btn"
              className="bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-full p-2 transition-colors cursor-pointer"
              aria-label="Cerrar modal"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        )}

        {/* Modal Scrollable Content */}
        <div className="p-6 overflow-y-auto space-y-4">
          {/* Outside Hours Notice */}
          {isReadOnly && (
            <div
              data-testid="outside-hours-modal-notice"
              className="bg-amber-50 border border-amber-200 text-amber-900 p-3 rounded-xl text-xs flex items-center gap-2"
            >
              <svg className="w-4 h-4 shrink-0 text-amber-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>
                {nextOpeningSchedule
                  ? `Establecimiento cerrado. ${nextOpeningSchedule}. Catálogo en modo informativo.`
                  : 'Establecimiento fuera de su horario de atención. Catálogo en modo informativo.'}
              </span>
            </div>
          )}

          {/* Title and Price */}
          <div className="flex items-start justify-between gap-3">
            <div>
              <span className="text-2xs font-bold tracking-wider text-amber-700 uppercase">
                {dish.category}
              </span>
              <h3 className="text-xl font-bold text-gray-900">{dish.name}</h3>
            </div>
            <span className="text-xl font-extrabold text-amber-900 whitespace-nowrap">
              ${dish.price.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </span>
          </div>

          {/* Description */}
          {dish.description && (
            <p className="text-sm text-gray-700 leading-relaxed">
              {dish.description}
            </p>
          )}

          {/* Recipe Ingredients (COM-4.1.1, FR-007, FR-014) */}
          {dish.recipeIngredients && dish.recipeIngredients.length > 0 && (
            <div className="pt-2 border-t border-gray-100">
              <h4 className="text-xs font-bold text-gray-800 uppercase tracking-wider mb-1.5">
                Ingredientes
              </h4>
              <p className="text-xs text-gray-600 leading-relaxed">
                {dish.recipeIngredients.join(', ')}
              </p>
            </div>
          )}

          {/* Dietary Compliance */}
          {hasConflicts && (
            <div className="pt-2 border-t border-gray-100">
              <h4 className="text-xs font-bold text-red-800 uppercase tracking-wider mb-1.5">
                Advertencia Dietaria
              </h4>
              <AllergenWarningBadge conflicts={dish.conflictingRestrictions} size="sm" />
            </div>
          )}

          {/* Quantity Selector Section */}
          <div className="pt-3 border-t border-gray-100 flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-gray-700 uppercase tracking-wider">
                Cantidad
              </span>
              <div data-testid="quantity-selector" className="flex items-center gap-2">
                <button
                  type="button"
                  data-testid="quantity-decrement-btn"
                  disabled={isDecrementDisabled}
                  onClick={() => setSelectedQuantity((q) => Math.max(minQuantity, q - 1))}
                  className="w-8 h-8 flex items-center justify-center rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-bold text-base cursor-pointer"
                  aria-label="Disminuir cantidad"
                >
                  -
                </button>
                <span
                  data-testid="quantity-value"
                  className="w-8 text-center font-bold text-gray-900 text-sm"
                >
                  {selectedQuantity}
                </span>
                <button
                  type="button"
                  data-testid="quantity-increment-btn"
                  disabled={isIncrementDisabled}
                  onClick={() =>
                    setSelectedQuantity((q) =>
                      remainingStock !== undefined ? Math.min(maxQuantity, q + 1) : q + 1
                    )
                  }
                  className="w-8 h-8 flex items-center justify-center rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-bold text-base cursor-pointer"
                  aria-label="Aumentar cantidad"
                >
                  +
                </button>
              </div>
            </div>

            {/* Stock Limit Warning */}
            {isMaxStockReached && (
              <div
                data-testid="stock-limit-warning"
                className="text-xs font-semibold text-amber-800 bg-amber-50 px-3 py-1.5 rounded-lg border border-amber-200"
              >
                Límite de disponibilidad alcanzado ({effectiveAvailableStock} en cocina)
              </div>
            )}
            {!isMaxStockReached && remainingStock !== undefined && selectedQuantity >= remainingStock && (
              <div
                data-testid="stock-limit-warning"
                className="text-xs font-semibold text-amber-800 bg-amber-50 px-3 py-1.5 rounded-lg border border-amber-200"
              >
                Máxima disponibilidad seleccionada ({remainingStock} disponible{remainingStock === 1 ? '' : 's'})
              </div>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-4 bg-gray-50 border-t border-gray-100 flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 bg-gray-200 hover:bg-gray-300 text-gray-800 text-sm font-semibold rounded-xl transition-colors cursor-pointer"
          >
            Cerrar
          </button>
          <button
            type="button"
            data-testid="add-to-cart-btn"
            disabled={!canAddToCart}
            onClick={handleAddToCart}
            className="px-5 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-bold rounded-xl transition-all shadow-xs disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer flex items-center gap-2"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
              />
            </svg>
            <span>Agregar al Carrito</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default DishDetailModal;
