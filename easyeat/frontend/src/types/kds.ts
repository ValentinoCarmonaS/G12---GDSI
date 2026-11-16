/**
 * Domain types for Kitchen Display System (KDS) and kitchen queue management.
 */

export type KdsTicketStatus = 'QUEUED' | 'IN_PREPARATION' | 'READY_FOR_PICKUP' | 'COMPLETED';

export type FulfillmentMode = 'IMMEDIATE' | 'SCHEDULED';

export interface KdsOrderItem {
  dishId: string;
  dishName: string;
  quantity: number;
}

export interface KdsTicket {
  ticketId: string;
  ticketNumber: number;
  orderId: string;
  status: KdsTicketStatus;
  fulfillmentMode: FulfillmentMode;
  pickupDate: string; // YYYY-MM-DD
  timeSlotStart: string; // HH:mm:ss
  timeSlotEnd: string; // HH:mm:ss
  queuedAt: string; // ISO-8601 UTC
  startedAt: string | null; // ISO-8601 UTC
  items: KdsOrderItem[];
}

export interface KdsStartPreparationResponse {
  ticketId: string;
  ticketNumber: number;
  orderId: string;
  status: KdsTicketStatus;
  startedAt: string; // ISO-8601 UTC
}

export interface KdsTicketSummary {
  ticketId: string;
  ticketNumber: number;
  status: KdsTicketStatus;
  queuedAt: string;
}

export interface KdsDashboardSummaryResponse {
  queuedOrdersCount: number;
  inPreparationOrdersCount: number;
  readyForPickupOrdersCount: number;
  completedTodayCount: number;
}

export interface KdsOrderPageResponse {
  content: KdsTicketSummary[];
  totalElements: number;
  totalPages: number;
}
