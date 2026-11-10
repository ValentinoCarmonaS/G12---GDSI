/**
 * Domain types for Kitchen Display System (KDS) and kitchen queue management.
 */

export type KdsTicketStatus = 'QUEUED' | 'IN_PREPARATION' | 'READY_FOR_PICKUP' | 'COMPLETED';

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
