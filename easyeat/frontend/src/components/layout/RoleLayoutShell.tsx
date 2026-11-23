import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { User as UserIcon } from 'lucide-react';
import type { AuthenticatedUser } from '../../types/auth';
import type { AdminNotificationResponse } from '../../types/notification';
import { getAuthenticatedAvatarUrl } from '../../utils/avatarUtils';
import { AdminNotificationBell } from '../notification/AdminNotificationBell';
import { AdminSidebar } from '../admin/AdminSidebar';
import { RoleHamburgerMenu } from './RoleHamburgerMenu';
import { ROLE_NAVIGATION_CONFIG, isRoleNavItemActive } from './roleNavigationConfig';

export type LegacySurfaceMode = 'none' | 'legacy-light' | 'legacy-dark';

export interface RoleLayoutShellProps {
  readonly title: string;
  readonly subtitle?: string;
  readonly children: React.ReactNode;
  readonly user: AuthenticatedUser | null;
  readonly avatarUrl?: string | null;
  readonly tenantName?: string | null;
  readonly notifications?: AdminNotificationResponse[];
  readonly unreadCount?: number;
  readonly notificationError?: string | null;
  readonly isLoadingNotifications?: boolean;
  readonly legacySurfaceMode?: LegacySurfaceMode;
  readonly onLogout: () => void | Promise<void>;
  readonly onMarkAsRead?: (notificationId: string) => void | Promise<void>;
  readonly onRefreshNotifications?: () => void | Promise<void>;
}

const noopMarkAsRead = () => undefined;

export const RoleLayoutShell: React.FC<RoleLayoutShellProps> = ({
  title,
  subtitle,
  children,
  user,
  avatarUrl = null,
  tenantName = null,
  notifications = [],
  unreadCount = 0,
  notificationError = null,
  isLoadingNotifications = false,
  legacySurfaceMode = 'none',
  onLogout,
  onMarkAsRead = noopMarkAsRead,
  onRefreshNotifications,
}) => {
  const { pathname, hash } = useLocation();
  const config = user?.role ? ROLE_NAVIGATION_CONFIG[user.role] : null;

  const avatarContent = avatarUrl ? (
    <img
      src={getAuthenticatedAvatarUrl(avatarUrl)}
      alt="Avatar"
      className="h-full w-full object-cover"
    />
  ) : (
    <UserIcon className="h-4 w-4 text-ee-text-secondary" aria-hidden="true" />
  );

  const legacySurfaceAttr =
    legacySurfaceMode === 'legacy-light'
      ? 'light'
      : legacySurfaceMode === 'legacy-dark'
        ? 'dark'
        : null;

  return (
    <div className="flex min-h-screen flex-col bg-ee-surface font-ee-sans text-ee-text">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-ee-sm focus:border focus:border-ee-primary-border focus:bg-ee-primary focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-ee-on-primary focus:shadow-ee-elevated"
      >
        Saltar al contenido principal
      </a>

      <header
        className="sticky top-0 z-30 border-b border-ee-border-subtle shadow-ee-elevated"
        style={{
          backgroundColor: 'var(--ee-surface-elevated-glass, var(--ee-surface-elevated))',
          backdropFilter: 'var(--ee-backdrop-blur)',
          borderBottomWidth: 'var(--ee-border-width)',
        }}
      >
        <div className="mx-auto flex min-h-16 max-w-7xl flex-wrap items-center justify-between gap-2 px-3 py-2 sm:flex-nowrap sm:gap-3 sm:px-6 lg:px-8">
          <div className="flex min-w-0 items-center gap-2 sm:gap-3">
            {user && config && (
              <RoleHamburgerMenu
                role={user.role}
                userEmail={user.email}
                onLogout={onLogout}
                className={config.useDesktopSidebar ? 'lg:hidden' : ''}
              />
            )}

            <span className="shrink-0 font-ee-display text-base font-bold tracking-tight text-ee-text sm:text-xl">
              EasyEat
            </span>
            <span aria-hidden="true" className="text-ee-border-strong">
              /
            </span>
            <span className="truncate text-sm font-semibold text-ee-text-secondary sm:text-base">
              {title}
            </span>

            {config?.exposeDirectDesktopNav && (
              <nav
                aria-label={config.ariaLabel}
                className="ml-2 hidden items-center gap-1.5 md:flex"
              >
                {config.items.map((item) => {
                  const active = isRoleNavItemActive(item, pathname, hash);
                  return (
                    <Link
                      key={item.id}
                      to={item.to}
                      aria-current={active ? 'page' : undefined}
                      className={`inline-flex min-h-[44px] items-center whitespace-nowrap rounded-ee-sm border px-3 text-sm transition-colors ${
                        active
                          ? 'border-ee-primary-border bg-ee-primary font-semibold text-ee-on-primary'
                          : 'border-transparent font-medium text-ee-text hover:border-ee-border-subtle hover:bg-ee-surface-subtle'
                      }`}
                      style={{ borderWidth: 'var(--ee-border-width)' }}
                    >
                      {item.label}
                    </Link>
                  );
                })}
              </nav>
            )}
          </div>

          <div className="flex shrink-0 items-center gap-2 sm:gap-3">
            {user && config ? (
              <>
                {config.showTenantBadge && user.tenantId && (
                  <span
                    data-testid="tenant-badge"
                    className="hidden items-center rounded-ee-sm border border-ee-border-subtle bg-ee-surface-subtle px-2.5 py-1 font-ee-mono text-xs font-medium text-ee-text lg:inline-flex"
                    title={`Local: ${user.tenantId}`}
                  >
                    Local: {user.tenantId.slice(0, 8)}
                  </span>
                )}

                {config.showNotificationBell && (
                  <AdminNotificationBell
                    notifications={notifications}
                    unreadCount={unreadCount}
                    error={notificationError}
                    isLoading={isLoadingNotifications}
                    onMarkAsRead={onMarkAsRead}
                    onRefresh={onRefreshNotifications}
                  />
                )}

                {config.allowProfileLink && (
                  <Link
                    to="/perfil"
                    title="Mi perfil"
                    aria-label="Mi perfil"
                    className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center overflow-hidden rounded-full border border-ee-border-subtle bg-ee-surface-subtle text-ee-text transition-colors hover:border-ee-primary-border"
                    style={{ borderWidth: 'var(--ee-border-width)' }}
                  >
                    {avatarContent}
                  </Link>
                )}
              </>
            ) : (
              <div className="flex items-center gap-2">
                <Link
                  to="/login"
                  className="inline-flex min-h-[44px] items-center rounded-ee-sm border border-ee-border-subtle bg-ee-surface-elevated px-3 text-sm font-semibold text-ee-text transition-colors hover:bg-ee-surface-subtle"
                  style={{ borderWidth: 'var(--ee-border-width)' }}
                >
                  Iniciar sesión
                </Link>
                <Link
                  to="/register"
                  className="inline-flex min-h-[44px] items-center rounded-ee-sm border border-ee-primary-border bg-ee-primary px-3 text-sm font-semibold text-ee-on-primary transition-colors hover:bg-ee-primary-hover"
                  style={{ borderWidth: 'var(--ee-border-width)' }}
                >
                  Registrarse
                </Link>
              </div>
            )}
          </div>
        </div>
      </header>

      <div className="flex flex-1">
        {user && config?.useDesktopSidebar && (
          <AdminSidebar
            userEmail={user.email}
            tenantName={tenantName}
            pendingDishesCount={null}
            onLogout={onLogout}
          />
        )}

        <main
          id="main-content"
          tabIndex={-1}
          className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 outline-none sm:px-6 sm:py-8 lg:px-8"
        >
        {subtitle && (
          <div className="mb-6">
            <p className="text-sm text-ee-text-secondary">{subtitle}</p>
          </div>
        )}

        {legacySurfaceAttr ? (
          <div
            data-testid="legacy-surface-container"
            data-legacy-surface={legacySurfaceAttr}
          >
            {children}
          </div>
        ) : (
          children
        )}
        </main>
      </div>
    </div>
  );
};

export default RoleLayoutShell;
