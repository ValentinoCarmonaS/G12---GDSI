/**
 * Supported internal administrative notification types (CHF-2.2.4).
 */
export type NotificationType = 'DISH_SUBMITTED_FOR_REVIEW';

/**
 * Representation of an administrative alert for submitted dishes.
 */
export interface AdminNotificationResponse {
  id: string;
  tenantId: string;
  type: NotificationType;
  title: string;
  message: string;
  dishId: string;
  chefId: string;
  isRead: boolean;
  createdAt: string;
}

/**
 * Paginated list container for administrative notifications.
 */
export interface AdminNotificationPageResponse {
  content: AdminNotificationResponse[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
  unreadCount: number;
}
