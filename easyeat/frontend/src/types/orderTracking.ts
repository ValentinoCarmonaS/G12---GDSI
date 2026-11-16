import type { FulfillmentMode, OrderStatus } from './order';

export type { FulfillmentMode, OrderStatus };

export interface OrderTrackingItem {
  dishId: string;
  dishName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface OrderConfirmationTrackingResponse {
  orderId: string;
  ticketNumber: number | null;
  status: OrderStatus;
  approvalStatus: 'APPROVED' | 'PENDING' | string;
  establishmentId: string;
  establishmentName: string;
  fulfillmentMode: FulfillmentMode;
  pickupDate: string;
  timeSlotStart: string;
  timeSlotEnd: string;
  totalAmount: number;
  items: OrderTrackingItem[];
  trackingUrl: string;
  serverTime: string;
}

export type KitchenStatus = 'NORMAL' | 'HIGH_DEMAND' | 'DELAYED';

export interface KdsQueueSaturation {
  activeOrdersAhead: number;
  kitchenStatus: KitchenStatus;
  globalDelayAppliedMinutes: number;
}

export interface LiveTrackingItem {
  dishName: string;
  quantity: number;
}

export interface OrderLiveTrackingResponse {
  orderId: string;
  ticketNumber: number | null;
  status: OrderStatus;
  fulfillmentMode: FulfillmentMode;
  isCountdownActive: boolean;
  estimatedMinutesRemaining: number | null;
  progressPercentage: number | null;
  isReadyForPickup: boolean;
  kdsQueueSaturation?: KdsQueueSaturation;
  message: string;
  serverTime: string;
  // --- Nuevos campos adaptativos Spec 021 ---
  positionAhead?: number | null;
  estimatedTotalMinutes?: number | null;
  elapsedMinutes?: number | null;
  remainingMinutes?: number | null;
  progressPercent?: number | null;
  specificDelayMinutes?: number | null;
  securityCode?: string | null;
  startedAt?: string | null;
  readyAt?: string | null;
  items?: LiveTrackingItem[];
}

export type OrderLiveTrackingData = OrderLiveTrackingResponse;

export interface KdsDelayAdjustmentRequest {
  establishmentId: string;
  globalDelayMinutes: number;
}

export interface KdsDelayAdjustmentResponse {
  establishmentId: string;
  globalDelayMinutes: number;
  updatedAt: string;
}

export interface KdsTicketReadyResponse {
  ticketId: string;
  orderId: string;
  ticketNumber: number | null;
  status: 'READY_FOR_PICKUP';
  readyAt: string;
}

export interface KdsDelayIncrementResponse {
  ticketId: string;
  orderId: string;
  ticketNumber: number;
  specificDelayMinutes: number;
  estimatedTotalMinutes: number;
  updatedAt: string;
}

export interface UseOrderLiveTrackingPollingResult {
  tracking: OrderLiveTrackingResponse | null;
  isLoading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
  isDisconnected?: boolean;
}
