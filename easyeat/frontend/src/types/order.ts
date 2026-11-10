/**
 * Domain types and contracts for Diner Order Fulfillment Mode & Time-Slot Reservation Engine (COM-6.2.x).
 */

export type FulfillmentMode = 'IMMEDIATE' | 'SCHEDULED';

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PENDING_ACCREDITATION'
  | 'IN_PROGRESS'
  | 'READY_FOR_PICKUP'
  | 'COMPLETED'
  | 'PAID'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'EXPIRED';

export type SlotStatus = 'AVAILABLE' | 'HIGH_DEMAND' | 'SOLD_OUT' | 'EXPIRED_OR_LEAD_TIME';

export type OrderErrorCode =
  | 'SLOT_SOLD_OUT'
  | 'ACTIVE_RESERVATION_EXISTS'
  | 'LEAD_TIME_VIOLATION'
  | 'DISH_PAUSED_BY_CHEF'
  | 'DISH_OUT_OF_STOCK'
  | 'ESTABLISHMENT_CLOSED'
  | 'INVALID_FULFILLMENT_MODE'
  | 'ORDER_ALREADY_EXPIRED';

export interface TimeSlotAvailability {
  startTime: string; // "HH:mm:ss" or "HH:mm"
  endTime: string;   // "HH:mm:ss" or "HH:mm"
  date: string;      // "YYYY-MM-DD"
  maxCapacity: number;
  bookedCount: number;
  occupancyPercentage: number;
  status: SlotStatus;
  isSelectable: boolean;
}

export interface PickupSlotCatalogResponse {
  establishmentId: string;
  date: string;
  timezone: string;
  isOperatingDay: boolean;
  leadTimeMinutes: number;
  slots: TimeSlotAvailability[];
}

export interface CreateOrderItemRequest {
  dishId: string;
  quantity: number;
}

export interface CreateOrderRequest {
  establishmentId: string;
  fulfillmentMode: FulfillmentMode;
  pickupDate?: string | null;
  timeSlotStart?: string | null;
  timeSlotEnd?: string | null;
  items: CreateOrderItemRequest[];
}

export interface OrderItemResponse {
  id: string;
  dishId: string;
  dishName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface OrderCreatedResponse {
  id?: string;
  orderId: string;
  status: OrderStatus;
  fulfillmentMode: FulfillmentMode;
  pickupDate: string;
  timeSlotStart: string;
  timeSlotEnd: string;
  totalAmount: number;
  expiresAt: string;
  createdAt: string;
}

export interface OrderDetailResponse {
  id?: string;
  orderId: string;
  establishmentId: string;
  userId: string;
  fulfillmentMode: FulfillmentMode;
  pickupDate: string;
  timeSlotStart: string;
  timeSlotEnd: string;
  status: OrderStatus;
  totalAmount: number;
  expiresAt: string;
  serverTime: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderSummaryResponse {
  id?: string;
  orderId: string;
  status: OrderStatus;
  message: string;
}

export interface OrderErrorProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?: OrderErrorCode | string;
  errors?: Record<string, string[]>;
}
