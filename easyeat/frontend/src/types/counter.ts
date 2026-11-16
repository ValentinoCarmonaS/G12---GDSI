/**
 * Domain types for Counter Dispatch and Ready-for-Pickup Orders (Spec 020).
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
  securityCode: string;
  items: CounterOrderItem[];
}

export interface CounterOrdersPollingState {
  orders: CounterReadyOrder[];
  isLoading: boolean;
  isDisconnected: boolean;
  serverOffsetMs?: number;
  refresh: () => Promise<void>;
}
