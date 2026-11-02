import { useState, useRef, useEffect, useCallback } from 'react';
import { cartValidationApi } from '../api/cartValidationApi';

export interface UseDebouncedCartModifierProps {
  dishId: string;
  tenantId: string;
  initialQuantity: number;
  availableStock?: number;
  onCommitQuantity: (dishId: string, quantity: number) => void;
  onRollback?: (dishId: string, revertedQuantity: number, error: Error) => void;
  debounceMs?: number;
}

export interface UseDebouncedCartModifierReturn {
  quantity: number;
  isSyncing: boolean;
  isMaxStockReached: boolean;
  isInsufficientStock: boolean;
  increment: () => void;
  decrement: () => void;
  setQuantity: (newQty: number) => void;
}

/**
 * useDebouncedCartModifier Hook (T037, US3, COM-6.1.3, INV-3, INV-9, FR-016)
 *
 * Implements optimistic quantity updates with a 300 ms debounced sync to
 * backend POST /api/v1/cart/validate-stock.
 * Ensures deterministic rollback on network drop or kitchen stock exhaustion (INV-9).
 */
export function useDebouncedCartModifier({
  dishId,
  tenantId,
  initialQuantity,
  availableStock,
  onCommitQuantity,
  onRollback,
  debounceMs = 300,
}: UseDebouncedCartModifierProps): UseDebouncedCartModifierReturn {
  const [quantity, setQuantityState] = useState<number>(initialQuantity);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);

  const confirmedQuantityRef = useRef<number>(initialQuantity);
  const targetQuantityRef = useRef<number>(initialQuantity);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Sync state if initialQuantity changes externally
  useEffect(() => {
    confirmedQuantityRef.current = initialQuantity;
    targetQuantityRef.current = initialQuantity;
    setQuantityState(initialQuantity);
  }, [initialQuantity]);

  // Clean up timer on unmount
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  const triggerDebouncedSync = useCallback(
    (newQuantity: number) => {
      targetQuantityRef.current = newQuantity;

      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }

      debounceTimerRef.current = setTimeout(async () => {
        setIsSyncing(true);
        try {
          // If quantity reached 0, no stock validation needed, commit removal
          if (newQuantity <= 0) {
            confirmedQuantityRef.current = 0;
            onCommitQuantity(dishId, 0);
            setIsSyncing(false);
            return;
          }

          const response = await cartValidationApi.validateStock({
            tenantId,
            items: [{ dishId, quantity: newQuantity }],
          });

          const itemEval = response.items?.find((i) => i.dishId === dishId);
          const isItemAvailable = itemEval ? itemEval.isAvailable : response.isValid;

          if (isItemAvailable) {
            confirmedQuantityRef.current = newQuantity;
            onCommitQuantity(dishId, newQuantity);
          } else {
            // Stock insufficient: Roll back to confirmed quantity (INV-9)
            const rollbackQty = confirmedQuantityRef.current;
            setQuantityState(rollbackQty);
            targetQuantityRef.current = rollbackQty;
            const error = new Error('Stock insuficiente en cocina');
            if (onRollback) {
              onRollback(dishId, rollbackQty, error);
            }
          }
        } catch (err: unknown) {
          // Network failure or 5xx: Roll back to confirmed quantity (INV-9, FR-016)
          const rollbackQty = confirmedQuantityRef.current;
          setQuantityState(rollbackQty);
          targetQuantityRef.current = rollbackQty;
          const error = err instanceof Error ? err : new Error('Error al sincronizar con el servidor');
          if (onRollback) {
            onRollback(dishId, rollbackQty, error);
          }
        } finally {
          setIsSyncing(false);
        }
      }, debounceMs);
    },
    [dishId, tenantId, debounceMs, onCommitQuantity, onRollback]
  );

  const increment = useCallback(() => {
    // Stock limit guardrail (INV-3, FR-014)
    if (availableStock !== undefined && targetQuantityRef.current >= availableStock) {
      return;
    }

    const nextQty = targetQuantityRef.current + 1;
    setQuantityState(nextQty);
    triggerDebouncedSync(nextQty);
  }, [availableStock, triggerDebouncedSync]);

  const decrement = useCallback(() => {
    const nextQty = Math.max(0, targetQuantityRef.current - 1);
    setQuantityState(nextQty);
    triggerDebouncedSync(nextQty);
  }, [triggerDebouncedSync]);

  const setQuantity = useCallback(
    (newQty: number) => {
      const clamped = Math.max(0, newQty);
      if (availableStock !== undefined && clamped > availableStock) {
        return;
      }
      setQuantityState(clamped);
      triggerDebouncedSync(clamped);
    },
    [availableStock, triggerDebouncedSync]
  );

  const isMaxStockReached = availableStock !== undefined && quantity >= availableStock;
  const isInsufficientStock = availableStock !== undefined && quantity > availableStock;

  return {
    quantity,
    isSyncing,
    isMaxStockReached,
    isInsufficientStock,
    increment,
    decrement,
    setQuantity,
  };
}

export default useDebouncedCartModifier;
