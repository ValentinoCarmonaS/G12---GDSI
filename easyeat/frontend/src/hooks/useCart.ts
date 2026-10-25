export interface CartItem {
  dishId: string;
  name: string;
  price: number;
  quantity: number;
  isCompliant?: boolean;
  conflictingRestrictions?: string[];
}

export function useCart() {
  // Minimal base hook for Phase 4; will be expanded with full CartContext and backend API in Phase 5 (T055)
  const items: CartItem[] = [];

  const reEvaluateCompliance = async () => {
    // Proactive compliance re-evaluation stub; will query POST /api/v1/cart/evaluate-compliance in Phase 5
  };

  return {
    items,
    reEvaluateCompliance,
  };
}

export default useCart;
