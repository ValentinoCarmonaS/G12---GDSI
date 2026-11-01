export interface CartItem {
  dishId: string;
  name: string;
  price: number;
  quantity: number;
  availableStock?: number;
  lastSyncedQuantity?: number;
  isCompliant?: boolean;
  conflictingRestrictions?: string[];
  riskConfirmed?: boolean;
}

export interface CartComplianceRequest {
  dishIds: string[];
}

export interface CartItemEvaluation {
  dishId: string;
  isCompliant: boolean;
  conflictingRestrictions: string[];
}

export interface CartComplianceResponse {
  items: CartItemEvaluation[];
}
