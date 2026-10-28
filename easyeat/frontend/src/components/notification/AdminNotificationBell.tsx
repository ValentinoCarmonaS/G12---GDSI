import React, { useState, useRef, useEffect } from 'react';
import type { AdminNotificationResponse } from '@/types/notification';

export interface AdminNotificationBellProps {
  notifications: AdminNotificationResponse[];
  unreadCount: number;
  onMarkAsRead: (id: string) => void | Promise<void>;
  onRefresh?: () => void | Promise<void>;
  className?: string;
}

export const AdminNotificationBell: React.FC<AdminNotificationBellProps> = ({
  notifications,
  unreadCount,
  onMarkAsRead,
  onRefresh,
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleToggle = () => {
    const nextState = !isOpen;
    setIsOpen(nextState);
    if (nextState && onRefresh) {
      onRefresh();
    }
  };

  return (
    <div className={`relative inline-block text-left ${className}`} ref={dropdownRef}>
      <button
        type="button"
        aria-label="Notificaciones"
        onClick={handleToggle}
        className="relative p-2 text-zinc-600 hover:text-zinc-900 dark:text-zinc-300 dark:hover:text-white rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors focus:outline-none focus:ring-2 focus:ring-purple-500"
      >
        <svg
          className="w-5 h-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
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
            className="absolute top-1 right-1 inline-flex items-center justify-center px-1.5 py-0.5 text-[10px] font-bold leading-none text-white transform translate-x-1/4 -translate-y-1/4 bg-red-600 rounded-full"
          >
            {unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          data-testid="notifications-dropdown"
          className="origin-top-right absolute right-0 mt-2 w-80 sm:w-96 rounded-xl shadow-lg bg-white dark:bg-zinc-900 ring-1 ring-black ring-opacity-5 border border-zinc-200 dark:border-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800 focus:outline-none z-50"
        >
          <div className="p-4 flex items-center justify-between">
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
              Notificaciones
            </h3>
            {unreadCount > 0 && (
              <span className="text-xs bg-purple-100 text-purple-800 dark:bg-purple-950/60 dark:text-purple-300 px-2 py-0.5 rounded-full font-medium">
                {unreadCount} pendientes
              </span>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto divide-y divide-zinc-100 dark:divide-zinc-800">
            {notifications.length === 0 ? (
              <div className="p-6 text-center text-xs text-zinc-500 dark:text-zinc-400">
                No hay notificaciones disponibles.
              </div>
            ) : (
              notifications.map((item) => (
                <div
                  key={item.id}
                  className={`p-4 transition-colors ${
                    item.isRead
                      ? 'bg-white dark:bg-zinc-900 opacity-70'
                      : 'bg-purple-50/40 dark:bg-purple-950/20'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <h4 className="text-xs font-semibold text-zinc-900 dark:text-zinc-100">
                      {item.title}
                    </h4>
                    {!item.isRead && (
                      <span className="w-2 h-2 rounded-full bg-purple-600 flex-shrink-0 mt-1" />
                    )}
                  </div>
                  <p className="text-xs text-zinc-600 dark:text-zinc-300 mt-1">
                    {item.message}
                  </p>
                  <div className="mt-3 flex items-center justify-between text-[11px]">
                    <span className="text-zinc-400">
                      {new Date(item.createdAt).toLocaleDateString('es-AR', {
                        day: '2-digit',
                        month: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                    {!item.isRead && (
                      <button
                        type="button"
                        onClick={() => onMarkAsRead(item.id)}
                        className="text-purple-600 dark:text-purple-400 hover:text-purple-800 dark:hover:text-purple-300 font-semibold"
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
