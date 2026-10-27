import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { CartItem } from '../types/cart';
import { EnrichedDish } from '../types/catalog';
import { catalogApi } from '../api/catalogApi';

const CART_STORAGE_KEY = 'easyeat_cart';

export interface CartContextType {
  items: CartItem[];
  addItem: (dish: EnrichedDish, riskConfirmed?: boolean) => void;
  removeItem: (dishId: string) => void;
  updateQuantity: (dishId: string, quantity: number) => void;
  clearCart: () => void;
  reEvaluateCompliance: () => Promise<void>;
  totalAmount: number;
  totalCount: number;
  hasConflictingItems: boolean;
}

export const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [items, setItems] = useState<CartItem[]>(() => {
    try {
      const stored = sessionStorage.getItem(CART_STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  });

  // Sync to sessionStorage
  useEffect(() => {
    try {
      sessionStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
    } catch (e) {
      console.error('Failed to persist cart to sessionStorage', e);
    }
  }, [items]);

  const addItem = useCallback((dish: EnrichedDish, riskConfirmed: boolean = false) => {
    setItems((prev) => {
      const existing = prev.find((item) => item.dishId === dish.id);
      if (existing) {
        return prev.map((item) =>
          item.dishId === dish.id
            ? {
                ...item,
                quantity: item.quantity + 1,
                riskConfirmed: item.riskConfirmed || riskConfirmed,
              }
            : item
        );
      }
      const newItem: CartItem = {
        dishId: dish.id,
        name: dish.name,
        price: dish.price,
        quantity: 1,
        isCompliant: dish.isCompliant,
        conflictingRestrictions: dish.conflictingRestrictions,
        riskConfirmed,
      };
      return [...prev, newItem];
    });
  }, []);

  const removeItem = useCallback((dishId: string) => {
    setItems((prev) => prev.filter((item) => item.dishId !== dishId));
  }, []);

  const updateQuantity = useCallback((dishId: string, quantity: number) => {
    setItems((prev) => {
      if (quantity <= 0) {
        return prev.filter((item) => item.dishId !== dishId);
      }
      return prev.map((item) =>
        item.dishId === dishId ? { ...item, quantity } : item
      );
    });
  }, []);

  const clearCart = useCallback(() => {
    setItems([]);
  }, []);

  // FR-015: Proactive cart re-evaluation upon dietary profile changes
  const reEvaluateCompliance = useCallback(async () => {
    if (items.length === 0) return;

    try {
      const dishIds = items.map((i) => i.dishId);
      const result = await catalogApi.evaluateCartCompliance({ dishIds });
      
      const evalMap = new Map(
        result.items.map((ev) => [ev.dishId, ev])
      );

      setItems((prev) =>
        prev.map((item) => {
          const evaluation = evalMap.get(item.dishId);
          if (evaluation) {
            return {
              ...item,
              isCompliant: evaluation.isCompliant,
              conflictingRestrictions: evaluation.conflictingRestrictions,
            };
          }
          return item;
        })
      );
    } catch (err) {
      console.error('Error re-evaluating cart compliance', err);
    }
  }, [items]);

  const totalAmount = useMemo(() => {
    return items.reduce((sum, item) => sum + item.price * item.quantity, 0);
  }, [items]);

  const totalCount = useMemo(() => {
    return items.reduce((sum, item) => sum + item.quantity, 0);
  }, [items]);

  const hasConflictingItems = useMemo(() => {
    return items.some((item) => item.isCompliant === false);
  }, [items]);

  return (
    <CartContext.Provider
      value={{
        items,
        addItem,
        removeItem,
        updateQuantity,
        clearCart,
        reEvaluateCompliance,
        totalAmount,
        totalCount,
        hasConflictingItems,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export function useCart(): CartContextType {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
}

export default CartContext;
