import React from 'react';
import { CartItem } from '../../types/cart';
import { AllergenWarningBadge } from '../catalog/AllergenWarningBadge';

export interface CartItemWithBadgeProps {
  item: CartItem;
  onUpdateQuantity: (dishId: string, quantity: number) => void;
  onRemove: (dishId: string) => void;
}

export const CartItemWithBadge: React.FC<CartItemWithBadgeProps> = ({
  item,
  onUpdateQuantity,
  onRemove,
}) => {
  const hasConflicts = item.isCompliant === false && item.conflictingRestrictions && item.conflictingRestrictions.length > 0;

  return (
    <div
      data-testid={`cart-item-${item.dishId}`}
      className={`p-3.5 rounded-xl border transition-colors ${
        hasConflicts
          ? 'bg-red-50/30 border-red-200'
          : 'bg-white border-gray-200'
      }`}
    >
      <div className="flex items-start justify-between gap-2 mb-1.5">
        <h5 className="text-sm font-semibold text-gray-900 leading-snug">{item.name}</h5>
        <span className="text-sm font-bold text-gray-900 whitespace-nowrap">
          ${(item.price * item.quantity).toLocaleString('es-AR', { minimumFractionDigits: 2 })}
        </span>
      </div>

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
            onClick={() => onUpdateQuantity(item.dishId, item.quantity - 1)}
            data-testid={`decrement-qty-${item.dishId}`}
            className="w-6 h-6 rounded border border-gray-300 flex items-center justify-center text-gray-600 hover:bg-gray-100 font-bold text-xs"
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
            onClick={() => onUpdateQuantity(item.dishId, item.quantity + 1)}
            data-testid={`increment-qty-${item.dishId}`}
            className="w-6 h-6 rounded border border-gray-300 flex items-center justify-center text-gray-600 hover:bg-gray-100 font-bold text-xs"
          >
            +
          </button>
        </div>

        <button
          type="button"
          onClick={() => onRemove(item.dishId)}
          data-testid={`remove-item-${item.dishId}`}
          className="text-xs text-red-600 hover:text-red-700 font-medium"
        >
          Eliminar
        </button>
      </div>
    </div>
  );
};

export default CartItemWithBadge;
