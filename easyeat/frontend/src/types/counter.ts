/**
 * Domain types for Counter Dispatch and Ready-for-Pickup Orders (Spec 020)
 * and delivery completion (Spec 022).
 * Strictly omits monetary/financial fields per RF-013 and Invariante 10.
 */

export interface CounterTimeSlot {
  start: string;
  end: string;
}

export interface CounterCustomerSummary {
  userId: string;
  fullName: string;
  photoUrl: string | null;
}

export interface CounterOrderItem {
  quantity: number;
  dishName: string;
}

export interface CounterReadyOrder {
  ticketId: string;
  orderId: string;
  ticketNumber: number;
  timeSlot: CounterTimeSlot;
  readyAt: string;
  customer: CounterCustomerSummary;
  /** Stored 4-digit code; `null` when the order lacks one (Spec 022 edge case 10). */
  securityCode: string | null;
  items: CounterOrderItem[];
}

/**
 * Payload of POST /counter/orders/{orderId}/complete-delivery (Spec 022 contract 01).
 * Identical for the first delivery and for idempotent retries, which keep the original `completedAt`.
 */
export interface CompleteDeliveryResponse {
  orderId: string;
  /** `null` for a ready order without KDS ticket (Spec 022 T030, contract 01 §3.1). */
  ticketNumber: number | null;
  status: 'COMPLETED';
  completedAt: string;
  message: string;
}

export interface CounterOrdersPollingState {
  orders: CounterReadyOrder[];
  isLoading: boolean;
  isDisconnected: boolean;
  serverOffsetMs?: number;
  refresh: () => Promise<void>;
}
