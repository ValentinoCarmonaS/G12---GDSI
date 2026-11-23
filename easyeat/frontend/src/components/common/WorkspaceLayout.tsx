import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { profileApi } from '../../api/profileApi';
import { getAdminBranding } from '../../api/brandingApi';
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
  const [notificationError, setNotificationError] = useState<string | null>(null);
  const [isLoadingNotifications, setIsLoadingNotifications] = useState<boolean>(false);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);

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

  const fetchTenantName = useCallback(async () => {
    if (user?.role !== 'ADMINISTRADOR' || !user.tenantId) {
      setTenantName(null);
      return;
    }
    try {
      const branding = await getAdminBranding(user.tenantId);
      const normalized = branding?.fantasyName?.trim() || null;
      setTenantName(normalized);
    } catch {
      setTenantName(null);
    }
  }, [user?.role, user?.tenantId]);

  useEffect(() => {
    fetchTenantName();
    const handleBrandingUpdate = () => {
      fetchTenantName();
    };
    window.addEventListener('easyeat:branding-changed', handleBrandingUpdate);
    return () => {
      window.removeEventListener('easyeat:branding-changed', handleBrandingUpdate);
    };
  }, [fetchTenantName]);

  const fetchNotifications = useCallback(async () => {
    if (user?.role !== 'ADMINISTRADOR') return;
    setIsLoadingNotifications(true);
    try {
      const data = await adminNotificationApi.listNotifications({ page: 0, size: 20 });
      setNotifications(data.content || []);
      setUnreadCount(data.unreadCount || 0);
      setNotificationError(null);
    } catch {
      setNotificationError('No pudimos cargar las notificaciones. Intente nuevamente.');
    } finally {
      setIsLoadingNotifications(false);
    }
  }, [user?.role]);

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  const handleMarkAsRead = async (notificationId: string) => {
    try {
      await adminNotificationApi.markAsRead(notificationId);
      setNotificationError(null);
      await fetchNotifications();
    } catch {
      setNotificationError('No pudimos marcar la notificación como leída. Intente nuevamente.');
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
      tenantName={tenantName}
      notifications={notifications}
      unreadCount={unreadCount}
      notificationError={notificationError}
      isLoadingNotifications={isLoadingNotifications}
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
