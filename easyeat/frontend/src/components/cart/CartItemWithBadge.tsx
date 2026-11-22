import React from 'react';
import { PauseCircle, AlertTriangle, Minus, Plus } from 'lucide-react';
import { CartItem } from '../../types/cart';
import { AllergenWarningBadge } from '../catalog/AllergenWarningBadge';
import { formatCurrency, calculateLineTotal } from '../../utils/currencyUtils';

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
  const hasConflicts =
    item.isCompliant === false &&
    item.conflictingRestrictions &&
    item.conflictingRestrictions.length > 0;

  const isPaused = Boolean(item.pausedByChef);
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
    if (isMaxStockReached || isPaused) {
      return;
    }
    onUpdateQuantity(item.dishId, item.quantity + 1);
  };

  return (
    <div
      data-testid={`cart-item-${item.dishId}`}
      className={`p-3.5 rounded-[var(--ee-radius-md)] border transition-colors ${
        isInsufficientStock
          ? 'bg-[var(--ee-warning-bg)] border-[var(--ee-warning-border)]'
          : hasConflicts
          ? 'bg-[var(--ee-surface-elevated)] border-[var(--ee-danger-border)]'
          : 'bg-[var(--ee-surface-elevated)] border-[var(--ee-border-subtle)]'
      }`}
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <div>
          <h5 className="text-sm font-semibold text-[var(--ee-text)] leading-snug">{item.name}</h5>
          <span className="text-xs font-ee-mono tabular-nums text-[var(--ee-text-secondary)] font-medium">
            {formatCurrency(item.price)} c/u
          </span>
        </div>
        <span className="text-sm font-bold font-ee-mono tabular-nums text-[var(--ee-text)] whitespace-nowrap">
          {formatCurrency(lineSubtotal)}
        </span>
      </div>

      {/* Warning badge on paused by chef */}
      {isPaused && (
        <div
          data-testid={`paused-by-chef-badge-${item.dishId}`}
          className="mb-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-[var(--ee-warning-bg)] text-[var(--ee-warning-fg)] border border-[var(--ee-warning-border)]"
        >
          <PauseCircle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
          <span>Pausado por cocina</span>
        </div>
      )}

      {/* Warning badge on insufficient kitchen stock */}
      {isInsufficientStock && !isPaused && (
        <div
          data-testid={`insufficient-stock-badge-${item.dishId}`}
          className="mb-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-[var(--ee-danger-bg)] text-[var(--ee-danger-fg)] border border-[var(--ee-danger-border)]"
        >
          <AlertTriangle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
          <span>Stock insuficiente (disponibles: {item.availableStock})</span>
        </div>
      )}

      {/* FR-022: Permanent visible inline warning badge in cart */}
      {hasConflicts && (
        <div className="mb-2">
          <AllergenWarningBadge conflicts={item.conflictingRestrictions!} size="sm" />
        </div>
      )}

      <div className="flex items-center justify-between pt-2 border-t border-[var(--ee-border-subtle)]">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleDecrement}
            data-testid={`decrement-qty-${item.dishId}`}
            aria-label={`Disminuir cantidad de ${item.name}`}
            className="min-w-[44px] min-h-[44px] rounded-[var(--ee-radius-sm)] border border-[var(--ee-border-strong)] bg-[var(--ee-surface-elevated)] flex items-center justify-center text-[var(--ee-text)] hover:bg-[var(--ee-surface-subtle)] cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
          >
            <Minus className="w-4 h-4" aria-hidden="true" />
          </button>
          <span
            data-testid={`item-qty-${item.dishId}`}
            className="text-sm font-bold font-ee-mono tabular-nums text-[var(--ee-text)] min-w-6 text-center"
          >
            {item.quantity}
          </span>
          <button
            type="button"
            onClick={handleIncrement}
            disabled={isMaxStockReached || isPaused}
            data-testid={`increment-qty-${item.dishId}`}
            aria-label={`Aumentar cantidad de ${item.name}`}
            className={`min-w-[44px] min-h-[44px] rounded-[var(--ee-radius-sm)] border flex items-center justify-center transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] ${
              isMaxStockReached || isPaused
                ? 'border-[var(--ee-border-subtle)] text-[var(--ee-text-secondary)] bg-[var(--ee-surface-subtle)] cursor-not-allowed opacity-50'
                : 'border-[var(--ee-border-strong)] bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] hover:bg-[var(--ee-surface-subtle)] cursor-pointer'
            }`}
          >
            <Plus className="w-4 h-4" aria-hidden="true" />
          </button>
        </div>

        <button
          type="button"
          onClick={() => onRemove(item.dishId)}
          data-testid={`remove-item-${item.dishId}`}
          className="min-h-[44px] px-2 text-xs text-[var(--ee-danger-fg)] hover:underline font-semibold cursor-pointer rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)]"
        >
          Eliminar
        </button>
      </div>
    </div>
  );
};

export default CartItemWithBadge;
