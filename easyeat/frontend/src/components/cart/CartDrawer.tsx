import React from 'react';
import { AlertTriangle, ShoppingBag } from 'lucide-react';
import { useCart } from '../../hooks/useCart';
import { CartItemWithBadge } from './CartItemWithBadge';
import { formatCurrency } from '../../utils/currencyUtils';
import { AccessibleDrawer } from '@/components/diner/AccessibleDrawer';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Button } from '@/components/ui/Button';

export interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onCheckout?: () => void;
}

export const CartDrawer: React.FC<CartDrawerProps> = ({
  isOpen,
  onClose,
  onCheckout,
}) => {
  const {
    items,
    updateQuantity,
    removeItem,
    clearCart,
    totalAmount,
    totalCount,
    hasConflictingItems,
    hasPausedItems,
  } = useCart();

  const hasInsufficientStock = items.some(
    (item) => item.availableStock !== undefined && item.quantity > item.availableStock
  );

  const isCheckoutDisabled = hasInsufficientStock || hasPausedItems;

  return (
    <AccessibleDrawer
      isOpen={isOpen}
      onClose={onClose}
      side="right"
      title="Tu Carrito"
      ariaLabel="Carrito de compras"
      headerBadge={
        <StatusBadge
          tone="neutral"
          label={`${totalCount} ${totalCount === 1 ? 'ítem' : 'ítems'}`}
        />
      }
      testId="cart-drawer"
      backdropTestId="cart-drawer-backdrop"
      closeButtonTestId="cart-drawer-close-button"
      closeButtonLabel="Cerrar carrito"
      footer={
        items.length > 0 ? (
          <div className="space-y-3 w-full">
            <div className="flex items-center justify-between text-sm font-semibold text-[var(--ee-text)]">
              <span>Total a pagar:</span>
              <span
                data-testid="cart-total-amount"
                className="text-lg font-bold font-ee-mono tabular-nums text-[var(--ee-text)]"
              >
                {formatCurrency(totalAmount)}
              </span>
            </div>

            {hasPausedItems && (
              <div
                role="alert"
                className="p-3 bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] text-[var(--ee-warning-fg)] rounded-[var(--ee-radius-sm)] text-xs font-medium"
              >
                Tu orden contiene platos pausados por cocina. Debés retirarlos o aguardar su reactivación para continuar.
              </div>
            )}

            <div className="flex gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={clearCart}
                data-testid="cart-clear-button"
                className="min-h-[44px]"
              >
                Vaciar
              </Button>
              <button
                type="button"
                onClick={onCheckout}
                disabled={isCheckoutDisabled}
                data-testid="cart-checkout-button"
                data-brand-slot={!isCheckoutDisabled ? 'cta' : undefined}
                style={
                  !isCheckoutDisabled
                    ? {
                        backgroundColor: 'var(--brand-primary, var(--ee-primary))',
                        color: 'var(--brand-primary-contrast, var(--ee-on-primary))',
                      }
                    : undefined
                }
                className={`flex-1 min-h-[44px] py-2.5 px-4 text-sm font-bold rounded-[var(--ee-radius-sm)] border transition-colors duration-150 text-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] ${
                  isCheckoutDisabled
                    ? 'bg-[var(--ee-surface-subtle)] text-[var(--ee-text-secondary)] border-[var(--ee-border-subtle)] cursor-not-allowed opacity-60'
                    : 'border-[var(--ee-border-strong)] hover:opacity-95 cursor-pointer'
                }`}
              >
                Continuar al checkout
              </button>
            </div>
          </div>
        ) : undefined
      }
    >
      <div className="space-y-4">
        {/* FR-014a: Warning banner on insufficient kitchen stock blocking checkout */}
        {hasInsufficientStock && (
          <div
            data-testid="cart-insufficient-stock-banner"
            role="alert"
            className="p-3 bg-[var(--ee-danger-bg)] border border-[var(--ee-danger-border)] rounded-[var(--ee-radius-sm)] text-xs text-[var(--ee-danger-fg)] flex items-start gap-2"
          >
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
            <p>
              <strong>Atención:</strong> Uno o más platos en tu orden exceden el stock disponible
              en cocina. Ajustá las cantidades antes de continuar al checkout.
            </p>
          </div>
        )}

        {/* FR-022: Permanent visible banner without redundant blocking modal at checkout */}
        {hasConflictingItems && (
          <div
            data-testid="cart-conflict-banner"
            role="status"
            className="p-3 bg-[var(--ee-warning-bg)] border border-[var(--ee-warning-border)] rounded-[var(--ee-radius-sm)] text-xs text-[var(--ee-warning-fg)] flex items-start gap-2"
          >
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
            <p>
              <strong>Aviso:</strong> Tu carrito contiene platos que no cumplen con tu perfil
              dietario. El riesgo fue previamente confirmado al agregarlos.
            </p>
          </div>
        )}

        {items.length === 0 ? (
          <div
            data-testid="cart-empty-message"
            className="text-center py-12 text-[var(--ee-text-secondary)] space-y-2"
          >
            <div className="w-12 h-12 mx-auto rounded-full bg-[var(--ee-surface-subtle)] border border-[var(--ee-border-subtle)] flex items-center justify-center text-[var(--ee-text)]">
              <ShoppingBag className="w-5 h-5" aria-hidden="true" />
            </div>
            <p className="text-base font-semibold text-[var(--ee-text)]">Tu carrito está vacío</p>
            <p className="text-xs text-[var(--ee-text-secondary)]">
              Explorá los platos y agregalos para ordenar.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {items.map((item) => (
              <CartItemWithBadge
                key={item.dishId}
                item={item}
                onUpdateQuantity={updateQuantity}
                onRemove={removeItem}
              />
            ))}
          </div>
        )}
      </div>
    </AccessibleDrawer>
  );
};

export default CartDrawer;
