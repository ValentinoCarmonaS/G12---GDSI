import React, { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { CartItem } from '../types/cart';
import { EnrichedDish } from '../types/catalog';
import { DishCatalogItem } from '../types/dinerCatalog';
import { catalogApi } from '../api/catalogApi';
import { calculateCartTotal } from '../utils/currencyUtils';

const CART_STORAGE_KEY = 'easyeat_cart';

export interface CartContextType {
  items: CartItem[];
  addItem: (
    dish: EnrichedDish | DishCatalogItem | { id: string; name: string; price: number; [key: string]: any },
    quantityOrRiskConfirmed?: number | boolean,
    riskConfirmed?: boolean
  ) => void;
  removeItem: (dishId: string) => void;
  updateQuantity: (dishId: string, quantity: number) => void;
  clearCart: () => void;
  reEvaluateCompliance: () => Promise<void>;
  totalAmount: number;
  totalCount: number;
  hasConflictingItems: boolean;
  toastMessage?: string | null;
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

  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimeoutRef = useRef<NodeJS.Timeout | number | null>(null);

  const showToast = useCallback((msg: string) => {
    if (toastTimeoutRef.current) {
      clearTimeout(toastTimeoutRef.current as any);
    }
    setToastMessage(msg);
    toastTimeoutRef.current = setTimeout(() => {
      setToastMessage(null);
      toastTimeoutRef.current = null;
    }, 3000);
  }, []);

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (toastTimeoutRef.current) {
        clearTimeout(toastTimeoutRef.current as any);
      }
    };
  }, []);

  // Sync to sessionStorage
  useEffect(() => {
    try {
      if (items.length > 0) {
        sessionStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
      } else {
        sessionStorage.removeItem(CART_STORAGE_KEY);
      }
    } catch (e) {
      console.error('Failed to persist cart to sessionStorage', e);
    }
  }, [items]);

  const addItem = useCallback(
    (
      dish: EnrichedDish | DishCatalogItem | { id: string; name: string; price: number; [key: string]: any },
      quantityOrRiskConfirmed: number | boolean = 1,
      riskConfirmedParam: boolean = false
    ) => {
      let quantity = 1;
      let riskConfirmed = false;

      if (typeof quantityOrRiskConfirmed === 'number') {
        quantity = quantityOrRiskConfirmed > 0 ? quantityOrRiskConfirmed : 1;
        riskConfirmed = Boolean(riskConfirmedParam);
      } else if (typeof quantityOrRiskConfirmed === 'boolean') {
        quantity = 1;
        riskConfirmed = quantityOrRiskConfirmed;
      }

      const availableStock: number | undefined = (dish as any).availableStock;
      let effectiveAddedQuantity = quantity;

      setItems((prev) => {
        const existing = prev.find((item) => item.dishId === dish.id);
        if (existing) {
          const maxStock = availableStock !== undefined ? availableStock : existing.availableStock;
          if (maxStock !== undefined && existing.quantity >= maxStock) {
            effectiveAddedQuantity = 0;
            return prev;
          }
          let newQuantity = existing.quantity + quantity;
          if (maxStock !== undefined && newQuantity > maxStock) {
            effectiveAddedQuantity = maxStock - existing.quantity;
            newQuantity = maxStock;
          }
          return prev.map((item) =>
            item.dishId === dish.id
              ? {
                  ...item,
                  quantity: newQuantity,
                  availableStock: maxStock !== undefined ? maxStock : item.availableStock,
                  riskConfirmed: item.riskConfirmed || riskConfirmed,
                }
              : item
          );
        }

        let initialQuantity = quantity;
        if (availableStock !== undefined && initialQuantity > availableStock) {
          initialQuantity = availableStock;
        }

        if (initialQuantity <= 0) {
          effectiveAddedQuantity = 0;
          return prev;
        }

        const newItem: CartItem = {
          dishId: dish.id,
          tenantId: (dish as any).tenantId,
          name: dish.name,
          price: dish.price,
          quantity: initialQuantity,
          availableStock,
          isCompliant: (dish as any).isCompliant,
          conflictingRestrictions: (dish as any).conflictingRestrictions,
          riskConfirmed,
        };
        return [...prev, newItem];
      });

      if (effectiveAddedQuantity <= 0) {
        showToast(`Límite de disponibilidad alcanzado para ${dish.name}`);
        return;
      }

      const unitLabel = effectiveAddedQuantity === 1 ? '1 unidad' : `${effectiveAddedQuantity} unidades`;
      showToast(`Se agregaron ${unitLabel} de ${dish.name} al carrito`);
    },
    [showToast]
  );

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
    try {
      sessionStorage.removeItem(CART_STORAGE_KEY);
    } catch (e) {
      console.error('Failed to clear cart in sessionStorage', e);
    }
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
    return calculateCartTotal(items);
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
        toastMessage,
      }}
    >
      {children}
      {toastMessage && (
        <div
          role="status"
          aria-live="polite"
          data-testid="cart-toast"
          className="fixed bottom-6 right-6 z-50 bg-gray-900/95 text-white text-xs sm:text-sm font-medium px-4 py-3 rounded-2xl shadow-xl flex items-center gap-2 animate-in fade-in"
        >
          <span>{toastMessage}</span>
        </div>
      )}
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
