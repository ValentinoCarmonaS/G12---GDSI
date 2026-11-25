import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { profileApi } from '../../api/profileApi';
import { getAdminBranding } from '../../api/brandingApi';
import { adminNotificationApi } from '../../api/adminNotificationApi';
import { useClientCache } from '../../context/ClientCacheContext';
import type { AdminNotificationResponse } from '../../types/notification';
import type { ProfileResponse } from '../../types/profile';
import { RoleLayoutShell, type LegacySurfaceMode } from '../layout/RoleLayoutShell';

export interface WorkspaceLayoutProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  legacySurfaceMode?: LegacySurfaceMode;
  fullWidth?: boolean;
}

export const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({
  title,
  subtitle,
  children,
  legacySurfaceMode = 'none',
  fullWidth = false,
}) => {
  const { user, logout } = useAuth();
  const cache = useClientCache();

  const [notifications, setNotifications] = useState<AdminNotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notificationError, setNotificationError] = useState<string | null>(null);
  const [isLoadingNotifications, setIsLoadingNotifications] = useState<boolean>(false);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(() => {
    if (user?.role !== 'COMENSAL') return null;
    return cache.get<ProfileResponse>('profile:me')?.data?.avatarUrl || null;
  });
  const [tenantName, setTenantName] = useState<string | null>(null);

  useEffect(() => {
    if (user?.role !== 'COMENSAL') return;
    return cache.subscribe('profile:me', () => {
      const entry = cache.get<ProfileResponse>('profile:me');
      setAvatarUrl(entry?.data?.avatarUrl || null);
    });
  }, [cache, user?.role]);

  const fetchAvatar = useCallback(async (forceRefresh = false) => {
    if (!user || user.role !== 'COMENSAL') {
      setAvatarUrl(null);
      return;
    }
    if (forceRefresh) {
      cache.invalidate('profile:me');
    }
    try {
      const data = await cache.fetchDeduped('profile:me', () => profileApi.getProfile());
      setAvatarUrl(data?.avatarUrl || null);
    } catch {
      if (!cache.get<ProfileResponse>('profile:me')?.data) {
        setAvatarUrl(null);
      }
    }
  }, [user, cache]);

  useEffect(() => {
    fetchAvatar();
    const handleAvatarUpdate = () => {
      fetchAvatar(true);
    };
    window.addEventListener('easyeat:avatar-changed', handleAvatarUpdate);
    return () => {
      window.removeEventListener('easyeat:avatar-changed', handleAvatarUpdate);
    };
  }, [fetchAvatar]);

  const fetchTenantName = useCallback(async (forceRefresh = false) => {
    if (user?.role !== 'ADMINISTRADOR' || !user.tenantId) {
      setTenantName(null);
      return;
    }
    const cacheKey = `branding:admin:${user.tenantId}`;
    if (forceRefresh) {
      cache.invalidate(cacheKey);
    }
    const cached = cache.get<{ fantasyName?: string | null }>(cacheKey);
    if (cached?.data) {
      setTenantName(cached.data.fantasyName?.trim() || null);
    }
    try {
      const branding = await cache.fetchDeduped(cacheKey, () => getAdminBranding(user.tenantId!));
      const normalized = branding?.fantasyName?.trim() || null;
      setTenantName(normalized);
    } catch {
      if (!cached?.data) {
        setTenantName(null);
      }
    }
  }, [user?.role, user?.tenantId, cache]);

  useEffect(() => {
    fetchTenantName();
    const handleBrandingUpdate = () => {
      fetchTenantName(true);
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
      fullWidth={fullWidth}
      onLogout={handleLogout}
      onMarkAsRead={handleMarkAsRead}
      onRefreshNotifications={fetchNotifications}
    >
      {children}
    </RoleLayoutShell>
  );
};

export default WorkspaceLayout;
