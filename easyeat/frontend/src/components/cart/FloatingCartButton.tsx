import React from 'react';
import { ShoppingBag } from 'lucide-react';
import { useCart } from '../../hooks/useCart';
import { formatCurrency } from '../../utils/currencyUtils';

export interface FloatingCartButtonProps {
  onClick: () => void;
  className?: string;
}

/**
 * FloatingCartButton Component (T028, US2, COM-6.1.2)
 *
 * Provides continuous order budget visibility and quick access to CartDrawer.
 * Enforces strict bijective visibility (INV-6, INV-7, FR-005, FR-008):
 * - Visible if and only if totalCount >= 1
 * - Immediately hidden if cart is empty (0 units)
 */
export const FloatingCartButton: React.FC<FloatingCartButtonProps> = ({
  onClick,
  className = '',
}) => {
  const { totalCount, totalAmount } = useCart();

  // Bijective visibility rule (INV-6, FR-008, SC-009)
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
      style={{
        backgroundColor: 'var(--brand-primary, #10B981)',
        color: 'var(--brand-primary-contrast, #FFFFFF)',
      }}
      className={`fixed bottom-20 right-6 z-40 inline-flex items-center gap-3 px-5 py-3.5 hover:brightness-110 active:scale-95 font-bold rounded-2xl shadow-xl transition-all duration-200 cursor-pointer animate-in fade-in slide-in-from-bottom-3 focus:outline-none focus:ring-4 focus:ring-emerald-500/30 ${className}`}
    >
      <span data-testid="open-cart-button" className="contents">
        {/* Icon with Counter Badge */}
        <div className="relative flex items-center justify-center">
          <ShoppingBag className="w-5 h-5" style={{ color: 'var(--brand-primary-contrast, #FFFFFF)' }} />
          <span
            data-testid="cart-badge-count"
            style={{
              backgroundColor: 'var(--brand-accent, #F59E0B)',
              color: 'var(--brand-accent-contrast, #000000)',
            }}
            className="absolute -top-2.5 -right-2.5 min-w-5 h-5 px-1 text-2xs font-extrabold rounded-full flex items-center justify-center shadow-xs"
          >
            {totalCount}
          </span>
        </div>

        {/* Label and Subtotal Separator */}
        <div className="flex items-center gap-2 text-sm">
          <span className="hidden sm:inline font-semibold opacity-90">Ver Carrito</span>
          <span className="hidden sm:inline opacity-60 font-light">•</span>
          <span data-testid="floating-cart-subtotal" className="font-extrabold tracking-tight">
            {formattedSubtotal}
          </span>
        </div>
      </span>
    </button>
  );
};

export default FloatingCartButton;
