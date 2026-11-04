import { useContext } from 'react';
import { CartContext, CartContextType } from '../context/CartContext';
import { CartItem } from '../types/cart';

export type { CartItem };

export function useCart(): CartContextType {
  const context = useContext(CartContext);
  if (!context) {
    // Graceful fallback for components mounted outside CartProvider
    return {
      items: [],
      addItem: () => {},
      removeItem: () => {},
      updateQuantity: () => {},
      clearCart: () => {},
      reEvaluateCompliance: async () => {},
      totalAmount: 0,
      totalCount: 0,
      hasConflictingItems: false,
      toastMessage: null,
    };
  }
  return context;
}

export default useCart;
