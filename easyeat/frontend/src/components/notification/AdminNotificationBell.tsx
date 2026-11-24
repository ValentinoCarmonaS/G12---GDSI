import React, { useCallback, useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import type { AdminNotificationResponse } from '@/types/notification';

export interface AdminNotificationBellProps {
  notifications: AdminNotificationResponse[];
  unreadCount: number;
  onMarkAsRead: (id: string) => void | Promise<void>;
  onRefresh?: () => void | Promise<void>;
  error?: string | null;
  isLoading?: boolean;
  className?: string;
}

const NOTIFICATIONS_PANEL_ID = 'ee-admin-notifications-panel';

function formatNotificationDate(createdAt: string): string {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return 'Fecha no disponible';
  }
  return date.toLocaleDateString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export const AdminNotificationBell: React.FC<AdminNotificationBellProps> = ({
  notifications,
  unreadCount,
  onMarkAsRead,
  onRefresh,
  error = null,
  isLoading = false,
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const close = useCallback((options?: { readonly restoreFocus?: boolean }) => {
    setIsOpen(false);
    if (options?.restoreFocus) {
      triggerRef.current?.focus();
    }
  }, []);

  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent | TouchEvent) => {
      if (
        dropdownRef.current &&
        event.target instanceof Node &&
        !dropdownRef.current.contains(event.target)
      ) {
        close();
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close({ restoreFocus: true });
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('touchstart', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('touchstart', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, close]);

  const handleToggle = () => {
    const nextState = !isOpen;
    setIsOpen(nextState);
    if (nextState && onRefresh) {
      onRefresh();
    }
  };

  const handleContainerBlur = (event: React.FocusEvent<HTMLDivElement>) => {
    if (
      isOpen &&
      dropdownRef.current &&
      event.relatedTarget instanceof Node &&
      !dropdownRef.current.contains(event.relatedTarget)
    ) {
      close();
    }
  };

  return (
    <div
      className={`relative inline-block text-left ${className}`}
      ref={dropdownRef}
      onBlur={handleContainerBlur}
    >
      <button
        ref={triggerRef}
        type="button"
        aria-label={
          unreadCount > 0 ? `Notificaciones (${unreadCount} sin leer)` : 'Notificaciones'
        }
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        aria-controls={NOTIFICATIONS_PANEL_ID}
        onClick={handleToggle}
        className="relative inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-ee-sm border border-ee-border-subtle bg-ee-surface-elevated text-ee-text transition-colors duration-150 motion-reduce:transition-none hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
        style={{ borderWidth: 'var(--ee-border-width)' }}
      >
        <svg
          className="h-5 w-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>

        {unreadCount > 0 && (
          <span
            data-testid="notification-badge"
            aria-hidden="true"
            className="absolute right-1 top-1 inline-flex min-h-[18px] min-w-[18px] items-center justify-center rounded-full bg-ee-danger-solid px-1 text-[11px] font-bold leading-none text-ee-on-danger"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          id={NOTIFICATIONS_PANEL_ID}
          role="dialog"
          aria-label="Panel de notificaciones"
          data-testid="notifications-dropdown"
          className="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] origin-top-right divide-y divide-ee-border-subtle rounded-ee-md border border-ee-border-strong shadow-ee-elevated sm:w-96"
          style={{
            backgroundColor: 'var(--ee-surface-elevated-glass, var(--ee-surface-elevated))',
            backdropFilter: 'var(--ee-backdrop-blur)',
            borderWidth: 'var(--ee-border-width)',
          }}
        >
          <div className="flex items-center justify-between gap-2 p-4">
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-bold text-ee-text">Notificaciones</h3>
              {unreadCount > 0 && (
                <span className="rounded-ee-sm border border-ee-info-border bg-ee-info-bg px-2 py-0.5 font-ee-mono text-xs font-semibold text-ee-info-fg">
                  {unreadCount} pendientes
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => close({ restoreFocus: true })}
              aria-label="Cerrar notificaciones"
              className="inline-flex min-h-[36px] min-w-[36px] items-center justify-center rounded-ee-sm border border-ee-border-subtle bg-ee-surface-elevated text-ee-text transition-colors hover:bg-ee-surface-subtle"
              style={{ borderWidth: 'var(--ee-border-width)' }}
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          <div className="max-h-80 divide-y divide-ee-border-subtle overflow-y-auto">
            {error && (
              <div
                role="alert"
                aria-live="assertive"
                className="m-3 rounded-ee-sm border border-ee-danger-border bg-ee-danger-bg p-3 text-xs text-ee-danger-fg"
              >
                <p className="font-semibold">{error}</p>
                {onRefresh && (
                  <button
                    type="button"
                    onClick={() => onRefresh()}
                    className="mt-2 inline-flex min-h-[36px] items-center rounded-ee-sm border border-ee-danger-border bg-ee-surface-elevated px-3 py-1 text-xs font-semibold text-ee-danger-fg transition-colors hover:bg-ee-danger-solid hover:text-ee-on-danger"
                  >
                    Reintentar
                  </button>
                )}
              </div>
            )}

            {isLoading && notifications.length === 0 ? (
              <div
                role="status"
                aria-live="polite"
                className="p-6 text-center text-xs text-ee-text-secondary"
              >
                Cargando notificaciones…
              </div>
            ) : notifications.length === 0 ? (
              !error && (
                <div className="p-6 text-center text-xs text-ee-text-secondary">
                  No hay notificaciones disponibles.
                </div>
              )
            ) : (
              notifications.map((item) => (
                <div
                  key={item.id}
                  className={`p-4 transition-colors ${
                    item.isRead ? 'bg-ee-surface-elevated' : 'bg-ee-info-bg'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <h4 className="text-xs font-semibold text-ee-text">
                      {!item.isRead && <span className="sr-only">No leída: </span>}
                      {item.title}
                    </h4>
                    {!item.isRead && (
                      <span
                        aria-hidden="true"
                        className="mt-1 h-2.5 w-2.5 flex-shrink-0 rounded-full bg-ee-primary"
                      />
                    )}
                  </div>
                  <p className="mt-1 text-xs text-ee-text-secondary">{item.message}</p>
                  <div className="mt-3 flex items-center justify-between gap-2 text-xs">
                    <span
                      data-testid="notification-timestamp"
                      className="font-ee-mono text-ee-text-secondary"
                    >
                      {formatNotificationDate(item.createdAt)}
                    </span>
                    {!item.isRead && (
                      <button
                        type="button"
                        onClick={() => onMarkAsRead(item.id)}
                        className="min-h-[36px] rounded-ee-sm px-2 py-1 font-semibold text-ee-info-fg underline-offset-2 transition-colors hover:underline"
                      >
                        Marcar como leída
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminNotificationBell;
