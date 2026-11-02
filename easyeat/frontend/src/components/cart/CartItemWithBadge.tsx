import React from 'react';
import { CartItem } from '../../types/cart';
import { AllergenWarningBadge } from '../catalog/AllergenWarningBadge';
import { formatCurrency, calculateLineTotal } from '../../utils/currencyUtils';

export interface CartItemWithBadgeProps {
  item: CartItem;
  onUpdateQuantity: (dishId: string, quantity: number) => void;
  onRemove: (dishId: string) => void;
}

/**
 * CartItemWithBadge Component (T038, US3, COM-6.1.3, INV-3, INV-9, FR-011..FR-015)
 *
 * Displays an individual cart line item with:
 * - Line subtotal calculated in integer cents (INV-2)
 * - Allergen dietary compliance warning badge (FR-022)
 * - Insufficient kitchen stock warning badge (FR-014a, INV-3)
 * - Numeric quantity +/- controls with kitchen stock ceiling guardrail (INV-3, FR-014)
 * - Complete item removal when decrementing quantity 1 (AC-3.3, FR-015)
 */
export const CartItemWithBadge: React.FC<CartItemWithBadgeProps> = ({
  item,
  onUpdateQuantity,
  onRemove,
}) => {
  const hasConflicts =
    item.isCompliant === false &&
    item.conflictingRestrictions &&
    item.conflictingRestrictions.length > 0;

  const isMaxStockReached =
    item.availableStock !== undefined && item.quantity >= item.availableStock;

  const isInsufficientStock =
    item.availableStock !== undefined && item.quantity > item.availableStock;

  const lineSubtotal = calculateLineTotal(item.price, item.quantity);

  const handleDecrement = () => {
    if (item.quantity <= 1) {
      onRemove(item.dishId);
    } else {
      onUpdateQuantity(item.dishId, item.quantity - 1);
    }
  };

  const handleIncrement = () => {
    if (isMaxStockReached) {
      return;
    }
    onUpdateQuantity(item.dishId, item.quantity + 1);
  };

  return (
    <div
      data-testid={`cart-item-${item.dishId}`}
      className={`p-3.5 rounded-xl border transition-colors ${
        isInsufficientStock
          ? 'bg-amber-50/50 border-amber-300'
          : hasConflicts
          ? 'bg-red-50/30 border-red-200'
          : 'bg-white border-gray-200'
      }`}
    >
      <div className="flex items-start justify-between gap-2 mb-1.5">
        <div>
          <h5 className="text-sm font-semibold text-gray-900 leading-snug">{item.name}</h5>
          <span className="text-xs text-gray-500 font-medium">
            {formatCurrency(item.price)} c/u
          </span>
        </div>
        <span className="text-sm font-bold text-gray-900 whitespace-nowrap">
          {formatCurrency(lineSubtotal)}
        </span>
      </div>

      {/* Warning badge on insufficient kitchen stock (FR-014a, INV-3) */}
      {isInsufficientStock && (
        <div
          data-testid={`insufficient-stock-badge-${item.dishId}`}
          className="mb-2 inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-2xs font-bold bg-amber-100 text-amber-900 border border-amber-300"
        >
          <span>⚠️</span>
          <span>Stock insuficiente (disponibles: {item.availableStock})</span>
        </div>
      )}

      {/* FR-022: Permanent visible inline warning badge in cart */}
      {hasConflicts && (
        <div className="mb-2">
          <AllergenWarningBadge conflicts={item.conflictingRestrictions!} size="sm" />
        </div>
      )}

      <div className="flex items-center justify-between pt-2 border-t border-gray-100">
        <div className="flex items-center space-x-2">
          <button
            type="button"
            onClick={handleDecrement}
            data-testid={`decrement-qty-${item.dishId}`}
            aria-label={`Disminuir cantidad de ${item.name}`}
            className="w-6 h-6 rounded border border-gray-300 flex items-center justify-center text-gray-600 hover:bg-gray-100 font-bold text-xs cursor-pointer active:scale-95 transition-all"
          >
            -
          </button>
          <span
            data-testid={`item-qty-${item.dishId}`}
            className="text-xs font-semibold text-gray-800 min-w-4 text-center"
          >
            {item.quantity}
          </span>
          <button
            type="button"
            onClick={handleIncrement}
            disabled={isMaxStockReached}
            data-testid={`increment-qty-${item.dishId}`}
            aria-label={`Aumentar cantidad de ${item.name}`}
            className={`w-6 h-6 rounded border flex items-center justify-center font-bold text-xs transition-all ${
              isMaxStockReached
                ? 'border-gray-200 text-gray-300 bg-gray-50 cursor-not-allowed opacity-40'
                : 'border-gray-300 text-gray-600 hover:bg-gray-100 cursor-pointer active:scale-95'
            }`}
          >
            +
          </button>
        </div>

        <button
          type="button"
          onClick={() => onRemove(item.dishId)}
          data-testid={`remove-item-${item.dishId}`}
          className="text-xs text-red-600 hover:text-red-700 font-medium cursor-pointer"
        >
          Eliminar
        </button>
      </div>
    </div>
  );
};

export default CartItemWithBadge;
