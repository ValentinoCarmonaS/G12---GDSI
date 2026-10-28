import React from 'react';
import type { AdminNotificationResponse } from '@/types/notification';

export interface AdminNotificationBellProps {
  notifications?: AdminNotificationResponse[];
  unreadCount?: number;
  onMarkAsRead?: (id: string) => Promise<void> | void;
  onRefresh?: () => Promise<void> | void;
}

export const AdminNotificationBell: React.FC<AdminNotificationBellProps> = () => {
  return <div data-testid="admin-notification-bell-stub">Stub</div>;
};

export default AdminNotificationBell;
