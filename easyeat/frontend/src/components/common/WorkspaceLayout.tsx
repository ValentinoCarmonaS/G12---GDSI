import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { profileApi } from '../../api/profileApi';
import { adminNotificationApi } from '../../api/adminNotificationApi';
import type { AdminNotificationResponse } from '../../types/notification';
import { RoleLayoutShell, type LegacySurfaceMode } from '../layout/RoleLayoutShell';

export interface WorkspaceLayoutProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  legacySurfaceMode?: LegacySurfaceMode;
}

export const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({
  title,
  subtitle,
  children,
  legacySurfaceMode = 'none',
}) => {
  const { user, logout } = useAuth();

  const [notifications, setNotifications] = useState<AdminNotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);

  const fetchAvatar = useCallback(async () => {
    if (!user) {
      setAvatarUrl(null);
      return;
    }
    try {
      const data = await profileApi.getProfile();
      setAvatarUrl(data?.avatarUrl || null);
    } catch {
      setAvatarUrl(null);
    }
  }, [user]);

  useEffect(() => {
    fetchAvatar();
    const handleAvatarUpdate = () => {
      fetchAvatar();
    };
    window.addEventListener('easyeat:avatar-changed', handleAvatarUpdate);
    return () => {
      window.removeEventListener('easyeat:avatar-changed', handleAvatarUpdate);
    };
  }, [fetchAvatar]);

  const fetchNotifications = useCallback(async () => {
    if (user?.role !== 'ADMINISTRADOR') return;
    try {
      const data = await adminNotificationApi.listNotifications({ page: 0, size: 20 });
      setNotifications(data.content || []);
      setUnreadCount(data.unreadCount || 0);
    } catch {
      // Ignored for non-admin or unauthenticated
    }
  }, [user?.role]);

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  const handleMarkAsRead = async (notificationId: string) => {
    try {
      await adminNotificationApi.markAsRead(notificationId);
      await fetchNotifications();
    } catch {
      // Ignore
    }
  };

  const handleLogout = async () => {
    await logout();
  };

  return (
    <RoleLayoutShell
      title={title}
      subtitle={subtitle}
      user={user}
      avatarUrl={avatarUrl}
      notifications={notifications}
      unreadCount={unreadCount}
      legacySurfaceMode={legacySurfaceMode}
      onLogout={handleLogout}
      onMarkAsRead={handleMarkAsRead}
      onRefreshNotifications={fetchNotifications}
    >
      {children}
    </RoleLayoutShell>
  );
};

export default WorkspaceLayout;
