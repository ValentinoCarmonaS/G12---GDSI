import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import {
  ROLE_NAVIGATION_CONFIG,
  isRoleNavItemActive,
  type RoleNavItem,
} from '../layout/roleNavigationConfig';

export interface AdminSidebarProps {
  readonly userEmail: string;
  readonly tenantName?: string | null;
  readonly pendingDishesCount: number | null;
  readonly onLogout: () => void | Promise<void>;
}

/**
 * Barra lateral ejecutiva Glassmorphism fija en escritorio (lg:flex) para ADMINISTRADOR.
 * Expone las 6 secciones agrupadas en 3 categorías, insignia condicional de platos pendientes
 * junto a Auditoría, metadatos de cuenta no navegables y cierre de sesión accesible.
 */
export const AdminSidebar: React.FC<AdminSidebarProps> = ({
  userEmail,
  tenantName = null,
  pendingDishesCount,
  onLogout,
}) => {
  const { pathname, hash } = useLocation();
  const config = ROLE_NAVIGATION_CONFIG.ADMINISTRADOR;

  const groupedSections = config.items.reduce<
    Array<{ group: string; items: RoleNavItem[] }>
  >((acc, item) => {
    const groupName = item.group ?? 'Secciones';
    const existing = acc.find((entry) => entry.group === groupName);
    if (existing) {
      existing.items.push(item);
    } else {
      acc.push({ group: groupName, items: [item] });
    }
    return acc;
  }, []);

  const showPendingBadge =
    typeof pendingDishesCount === 'number' && pendingDishesCount > 0;

  return (
    <aside
      aria-label={config.ariaLabel}
      className="hidden w-64 shrink-0 flex-col justify-between border-r border-ee-border-subtle p-4 shadow-ee-elevated lg:sticky lg:top-16 lg:flex lg:h-[calc(100dvh-4rem)]"
      style={{
        backgroundColor: 'var(--ee-surface-elevated-glass, var(--ee-surface-elevated))',
        backdropFilter: 'var(--ee-backdrop-blur, blur(12px))',
        borderRightWidth: 'var(--ee-border-width, 1px)',
      }}
    >
      <div className="space-y-5 overflow-y-auto pr-1">
        <div className="border-b border-ee-border-subtle pb-3">
          <p className="font-ee-mono text-xs font-semibold uppercase tracking-wider text-ee-text-secondary">
            {config.displayRoleLabel}
          </p>
          <p className="mt-0.5 truncate text-sm font-medium text-ee-text" title={userEmail}>
            {userEmail}
          </p>
          {tenantName && (
            <p
              className="mt-1 truncate font-ee-mono text-xs text-ee-text-secondary"
              title={`Local: ${tenantName}`}
            >
              Local: {tenantName}
            </p>
          )}
        </div>

        <nav aria-label="Secciones de administración" className="space-y-4">
          {groupedSections.map((section) => (
            <div key={section.group} className="space-y-1">
              <p className="px-2.5 font-ee-mono text-[11px] font-semibold uppercase tracking-wider text-ee-text-secondary">
                {section.group}
              </p>
              <ul className="space-y-1">
                {section.items.map((item) => {
                  const active = isRoleNavItemActive(item, pathname, hash);
                  const isAuditItem = item.id === 'admin-auditoria';

                  return (
                    <li key={item.id}>
                      <Link
                        to={item.to}
                        aria-current={active ? 'page' : undefined}
                        className={`flex min-h-[44px] items-center justify-between gap-2 rounded-ee-sm border px-3 py-2 text-sm transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring ${
                          active
                            ? 'border-ee-primary-border bg-ee-primary font-semibold text-ee-on-primary'
                            : 'border-transparent font-medium text-ee-text hover:border-ee-border-subtle hover:bg-ee-surface-subtle'
                        }`}
                        style={{ borderWidth: 'var(--ee-border-width, 1px)' }}
                      >
                        <span className="truncate">{item.label}</span>
                        {isAuditItem && showPendingBadge && (
                          <span
                            aria-label={`${pendingDishesCount} platos pendientes`}
                            className={`inline-flex min-w-[22px] items-center justify-center rounded-full px-1.5 py-0.5 font-ee-mono text-xs font-bold tabular-nums ${
                              active
                                ? 'bg-ee-on-primary text-ee-primary'
                                : 'bg-ee-primary text-ee-on-primary'
                            }`}
                          >
                            {pendingDishesCount}
                          </span>
                        )}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </div>

      <div className="border-t border-ee-border-subtle pt-3">
        <button
          type="button"
          onClick={() => void onLogout()}
          className="flex min-h-[44px] w-full items-center gap-2 rounded-ee-sm border border-transparent px-3 py-2 text-sm font-medium text-ee-text transition-colors duration-150 motion-reduce:transition-none hover:border-ee-border-subtle hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
        >
          <LogOut className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>Cerrar sesión</span>
        </button>
      </div>
    </aside>
  );
};
