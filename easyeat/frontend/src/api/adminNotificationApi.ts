import { apiClient } from './apiClient';
import type { AdminNotificationPageResponse, AdminNotificationResponse } from '@/types/notification';

export const adminNotificationApi = {
  /**
   * Lists administrative alerts with optional unreadOnly filter and pagination.
   */
  async listNotifications(params?: {
    unreadOnly?: boolean;
    page?: number;
    size?: number;
  }): Promise<AdminNotificationPageResponse> {
    const response = await apiClient.get<AdminNotificationPageResponse>('/admin/notifications', {
      params,
    });
    return response.data;
  },

  /**
   * Marks a specific administrative notification as read.
   */
  async markAsRead(id: string): Promise<AdminNotificationResponse> {
    const response = await apiClient.patch<AdminNotificationResponse>(`/admin/notifications/${id}/read`);
    return response.data;
  },
};

export default adminNotificationApi;
