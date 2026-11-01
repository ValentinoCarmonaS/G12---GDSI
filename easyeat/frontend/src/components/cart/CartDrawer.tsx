import React from 'react';
import { useCart } from '../../hooks/useCart';
import { CartItemWithBadge } from './CartItemWithBadge';
import { formatCurrency } from '../../utils/currencyUtils';

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
  } = useCart();

  if (!isOpen) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Cajón de carrito de compras"
      data-testid="cart-drawer"
      className="fixed inset-0 z-40 overflow-hidden"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/40 backdrop-blur-xs transition-opacity"
        onClick={onClose}
        data-testid="cart-drawer-backdrop"
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-white shadow-2xl flex flex-col justify-between">
          {/* Header */}
          <div className="p-5 border-b border-gray-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-bold text-gray-900">Tu Carrito</h3>
              <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-amber-100 text-amber-800">
                {totalCount} {totalCount === 1 ? 'ítem' : 'ítems'}
              </span>
            </div>
            <button
              type="button"
              onClick={onClose}
              data-testid="cart-drawer-close-button"
              className="p-1 rounded-md text-gray-400 hover:text-gray-600 focus:outline-none"
            >
              <span className="sr-only">Cerrar carrito</span>
              <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Body */}
          <div className="flex-1 overflow-y-auto p-5 space-y-4">
            {/* FR-022: Permanent visible banner without redundant blocking modal at checkout */}
            {hasConflictingItems && (
              <div
                data-testid="cart-conflict-banner"
                className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-900 flex items-start gap-2"
              >
                <span className="text-base leading-none">⚠️</span>
                <p>
                  <strong>Aviso:</strong> Tu carrito contiene platos que no cumplen con tu perfil
                  dietario. El riesgo fue previamente confirmado al agregarlos.
                </p>
              </div>
            )}

            {items.length === 0 ? (
              <div data-testid="cart-empty-message" className="text-center py-16 text-gray-500">
                <p className="text-base font-semibold text-gray-700">Tu carrito está vacío</p>
                <p className="text-xs text-gray-500 mt-1">Explorá los platos y agregalos para ordenar.</p>
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

          {/* Footer */}
          {items.length > 0 && (
            <div className="p-5 border-t border-gray-200 bg-gray-50 space-y-3">
              <div className="flex items-center justify-between text-sm font-semibold text-gray-900">
                <span>Total a pagar:</span>
                <span data-testid="cart-total-amount" className="text-lg font-bold text-amber-700">
                  {formatCurrency(totalAmount)}
                </span>
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={clearCart}
                  data-testid="cart-clear-button"
                  className="px-3 py-2 text-xs font-semibold text-gray-600 hover:text-red-600 border border-gray-300 rounded-lg bg-white"
                >
                  Vaciar
                </button>
                <button
                  type="button"
                  onClick={onCheckout}
                  data-testid="cart-checkout-button"
                  className="flex-1 py-2.5 px-4 bg-green-600 hover:bg-green-700 text-white text-sm font-bold rounded-lg shadow-sm transition-colors text-center"
                >
                  Continuar al checkout
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CartDrawer;
