import React from 'react';
import { ShoppingBag } from 'lucide-react';
import { useCart } from '../../hooks/useCart';
import { formatCurrency } from '../../utils/currencyUtils';

export interface FloatingCartButtonProps {
  onClick: () => void;
  className?: string;
}

/**
 * FloatingCartButton Component (Spec 009 + Spec 025 FR-008/FR-009)
 * Visible if and only if totalCount >= 1.
 */
export const FloatingCartButton: React.FC<FloatingCartButtonProps> = ({
  onClick,
  className = '',
}) => {
  const { totalCount, totalAmount } = useCart();

  if (!totalCount || totalCount <= 0) {
    return null;
  }

  const formattedSubtotal = formatCurrency(totalAmount);

  return (
    <button
      type="button"
      onClick={onClick}
      role="button"
      aria-label="Ver carrito de compras"
      data-testid="floating-cart-button"
      data-brand-slot="cta"
      style={{
        backgroundColor: 'var(--brand-primary, var(--ee-primary))',
        color: 'var(--brand-primary-contrast, var(--ee-on-primary))',
      }}
      className={`fixed bottom-20 right-6 z-40 min-h-[48px] inline-flex items-center gap-3 px-5 py-3 font-bold rounded-[var(--ee-radius-lg)] border border-[var(--ee-border-strong)] shadow-[var(--ee-shadow-elevated)] transition-colors duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] focus-visible:ring-offset-2 ${className}`}
    >
      <span data-testid="open-cart-button" className="contents">
        <div className="relative flex items-center justify-center">
          <ShoppingBag className="w-5 h-5" aria-hidden="true" />
          <span
            data-testid="cart-badge-count"
            className="absolute -top-2.5 -right-2.5 min-w-5 h-5 px-1 text-xs font-extrabold font-ee-mono tabular-nums rounded-full flex items-center justify-center bg-[var(--ee-surface-elevated)] text-[var(--ee-text)] border border-[var(--ee-border-strong)]"
          >
            {totalCount}
          </span>
        </div>

        <div className="flex items-center gap-2 text-sm">
          <span className="hidden sm:inline font-semibold">Ver Carrito</span>
          <span className="hidden sm:inline opacity-60" aria-hidden="true">
            •
          </span>
          <span
            data-testid="floating-cart-subtotal"
            className="font-extrabold font-ee-mono tabular-nums tracking-tight"
          >
            {formattedSubtotal}
          </span>
        </div>
      </span>
    </button>
  );
};

export default FloatingCartButton;
