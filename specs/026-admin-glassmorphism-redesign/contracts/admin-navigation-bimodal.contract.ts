/**
 * Spec 026 — Admin Glassmorphism Redesign
 * Contract for Bimodal Executive Navigation (desktop fixed sidebar + mobile hamburger)
 * and lightweight pending dishes badge synchronization (FR-005, FR-006).
 */

export type AdminNavGroup = 'Operación' | 'Catálogo y equipo' | 'Negocio';

export type AdminNavItemId =
  | 'admin-panel'
  | 'admin-operaciones'
  | 'admin-inventario'
  | 'admin-auditoria'
  | 'admin-personal'
  | 'admin-mi-local';

export interface AdminSidebarProps {
  readonly userEmail: string;
  readonly tenantId?: string | null;
  readonly pendingDishesCount: number | null;
  readonly onLogout: () => void | Promise<void>;
}

export interface AdminPendingDishesContextValue {
  /** null while loading or on fetch error (never renders a fake 0 badge) */
  readonly pendingCount: number | null;
  readonly setPendingCount: (count: number) => void;
  readonly decrementPendingCount: () => void;
  readonly refreshPendingCount: () => Promise<void>;
}

export const ADMIN_BIMODAL_NAV_CONTRACT = {
  totalItems: 6,
  removedLegacyItem: 'admin-metricas',
  legacyRedirect: { from: '/admin/analytics', to: '/admin' },
  desktopBreakpointPx: 1024,
  hideHamburgerOnDesktop: true,
  badgeTargetItemId: 'admin-auditoria' as AdminNavItemId,
  pollingEnabled: false,
} as const;
