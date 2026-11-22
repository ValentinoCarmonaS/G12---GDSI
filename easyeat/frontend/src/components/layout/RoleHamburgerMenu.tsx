import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LogOut, Menu, X } from 'lucide-react';
import type { EasyEatUserRole } from '../../theme/types';
import { ROLE_NAVIGATION_CONFIG, isRoleNavItemActive } from './roleNavigationConfig';

export interface RoleHamburgerMenuProps {
  role: EasyEatUserRole;
  userEmail?: string | null;
  onLogout: () => void | Promise<void>;
  panelId?: string;
  className?: string;
}

export const RoleHamburgerMenu: React.FC<RoleHamburgerMenuProps> = ({
  role,
  userEmail,
  onLogout,
  panelId = 'ee-role-menu-panel',
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const { pathname, hash } = useLocation();

  const config = ROLE_NAVIGATION_CONFIG[role];

  const close = useCallback((options?: { readonly restoreFocus?: boolean }) => {
    setIsOpen(false);
    if (options?.restoreFocus) {
      triggerRef.current?.focus();
    }
  }, []);

  const toggle = useCallback(() => {
    setIsOpen((prev) => !prev);
  }, []);

  useEffect(() => {
    setIsOpen(false);
  }, [pathname, hash]);

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close({ restoreFocus: true });
      }
    };

    const handleOutsideInteraction = (event: MouseEvent | TouchEvent) => {
      if (
        containerRef.current &&
        event.target instanceof Node &&
        !containerRef.current.contains(event.target)
      ) {
        close();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('mousedown', handleOutsideInteraction);
    document.addEventListener('touchstart', handleOutsideInteraction);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handleOutsideInteraction);
      document.removeEventListener('touchstart', handleOutsideInteraction);
    };
  }, [isOpen, close]);

  const handleLogoutClick = async () => {
    close();
    await onLogout();
  };

  const handleContainerBlur = (event: React.FocusEvent<HTMLDivElement>) => {
    if (
      isOpen &&
      containerRef.current &&
      event.relatedTarget instanceof Node &&
      !containerRef.current.contains(event.relatedTarget)
    ) {
      close();
    }
  };

  const hasGroups = config.items.some((item) => Boolean(item.group));
  const groupedSections = hasGroups
    ? config.items.reduce<Array<{ group: string; items: typeof config.items[number][] }>>(
        (acc, item) => {
          const groupName = item.group ?? 'Secciones';
          const existing = acc.find((entry) => entry.group === groupName);
          if (existing) {
            existing.items.push(item);
          } else {
            acc.push({ group: groupName, items: [item] });
          }
          return acc;
        },
        []
      )
    : null;

  return (
    <div ref={containerRef} onBlur={handleContainerBlur} className={`relative ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        onClick={toggle}
        aria-expanded={isOpen}
        aria-controls={panelId}
        aria-label={isOpen ? 'Cerrar menú de navegación' : 'Abrir menú de navegación'}
        className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-ee-sm border border-ee-border-subtle bg-ee-surface-elevated text-ee-text transition-colors hover:bg-ee-surface-subtle"
        style={{ borderWidth: 'var(--ee-border-width)' }}
      >
        {isOpen ? (
          <X className="h-5 w-5" aria-hidden="true" />
        ) : (
          <Menu className="h-5 w-5" aria-hidden="true" />
        )}
      </button>

      {isOpen && (
        <nav
          id={panelId}
          aria-label={config.ariaLabel}
          className="absolute left-0 z-50 mt-2 max-h-[calc(100dvh-5rem)] w-72 max-w-[calc(100vw-2rem)] overflow-y-auto rounded-ee-md border border-ee-border-strong p-3 shadow-ee-elevated"
          style={{
            backgroundColor: 'var(--ee-surface-elevated-glass, var(--ee-surface-elevated))',
            backdropFilter: 'var(--ee-backdrop-blur)',
            borderWidth: 'var(--ee-border-width)',
          }}
        >
          <div className="mb-2 border-b border-ee-border-subtle px-2 pb-2.5">
            <p className="font-ee-mono text-xs font-semibold uppercase tracking-wider text-ee-text-secondary">
              {config.displayRoleLabel}
            </p>
            {userEmail && (
              <p className="mt-0.5 truncate text-sm font-medium text-ee-text" title={userEmail}>
                {userEmail}
              </p>
            )}
          </div>

          {groupedSections ? (
            <div className="space-y-3">
              {groupedSections.map((section) => (
                <div key={section.group}>
                  <p className="mb-1 px-2 font-ee-mono text-[11px] font-semibold uppercase tracking-wider text-ee-text-secondary">
                    {section.group}
                  </p>
                  <ul className="space-y-1">
                    {section.items.map((item) => {
                      const active = isRoleNavItemActive(item, pathname, hash);
                      return (
                        <li key={item.id}>
                          <Link
                            to={item.to}
                            aria-current={active ? 'page' : undefined}
                            onClick={() => close()}
                            className={`flex min-h-[44px] items-center justify-between rounded-ee-sm border-l-4 px-3 py-2 text-sm transition-colors ${
                              active
                                ? 'border-ee-primary-border bg-ee-primary font-semibold text-ee-on-primary'
                                : 'border-transparent font-medium text-ee-text hover:bg-ee-surface-subtle'
                            }`}
                          >
                            <span>{item.label}</span>
                            {active && (
                              <span
                                aria-hidden="true"
                                className="ml-2 font-ee-mono text-[11px] font-semibold uppercase tracking-wider"
                              >
                                Actual
                              </span>
                            )}
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
            </div>
          ) : (
            <ul className="space-y-1">
              {config.items.map((item) => {
                const active = isRoleNavItemActive(item, pathname, hash);
                return (
                  <li key={item.id}>
                    <Link
                      to={item.to}
                      aria-current={active ? 'page' : undefined}
                      onClick={() => close()}
                      className={`flex min-h-[44px] items-center justify-between rounded-ee-sm border-l-4 px-3 py-2 text-sm transition-colors ${
                        active
                          ? 'border-ee-primary-border bg-ee-primary font-semibold text-ee-on-primary'
                          : 'border-transparent font-medium text-ee-text hover:bg-ee-surface-subtle'
                      }`}
                    >
                      <span>{item.label}</span>
                      {active && (
                        <span
                          aria-hidden="true"
                          className="ml-2 font-ee-mono text-[11px] font-semibold uppercase tracking-wider"
                        >
                          Actual
                        </span>
                      )}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}

          <div className="mt-3 border-t border-ee-border-subtle pt-2.5">
            <button
              type="button"
              onClick={handleLogoutClick}
              className="flex min-h-[44px] w-full items-center gap-2 rounded-ee-sm border border-ee-danger-border bg-ee-danger-bg px-3 py-2 text-sm font-semibold text-ee-danger-fg transition-colors hover:bg-ee-danger-solid hover:text-ee-on-danger"
            >
              <LogOut className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span>Cerrar sesión</span>
            </button>
          </div>
        </nav>
      )}
    </div>
  );
};

export default RoleHamburgerMenu;
