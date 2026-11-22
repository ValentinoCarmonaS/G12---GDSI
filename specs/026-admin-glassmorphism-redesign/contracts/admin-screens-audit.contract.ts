/**
 * Spec 026 — Admin Glassmorphism Redesign
 * Audit manifest for all in-scope screens across 360px, 768px and 1440px (SC-001 to SC-006).
 */

export interface AdminScreenAuditEntry {
  readonly route: string;
  readonly pageComponent: string;
  readonly userStory: 'US1' | 'US2' | 'US3' | 'US4' | 'US5';
  readonly themeMode: 'admin-glassmorphism' | 'polymorphic-role-tokens';
  readonly semanticTableException: 'none' | 'chart-ver-tabla' | 'slot-capacity-row-role';
  readonly requiredViewportsPx: readonly [360, 768, 1440];
}

export const ADMIN_SCREENS_AUDIT_MANIFEST: readonly AdminScreenAuditEntry[] = [
  {
    route: '/admin',
    pageComponent: 'AdminWorkspacePage',
    userStory: 'US2',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'chart-ver-tabla',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/admin/dishes',
    pageComponent: 'AdminDishReviewPage',
    userStory: 'US3',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'none',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/admin/dishes/:id/cost-audit',
    pageComponent: 'AdminDishCostAuditPage',
    userStory: 'US3',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'none',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/admin/staff',
    pageComponent: 'AdminStaffPage',
    userStory: 'US4',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'none',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/admin/inventory',
    pageComponent: 'AdminInventoryPage',
    userStory: 'US4',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'none',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/admin/operations',
    pageComponent: 'AdminOperationsPage',
    userStory: 'US5',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'slot-capacity-row-role',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/admin/mi-local',
    pageComponent: 'AdminBrandingPage',
    userStory: 'US5',
    themeMode: 'admin-glassmorphism',
    semanticTableException: 'none',
    requiredViewportsPx: [360, 768, 1440],
  },
  {
    route: '/first-login-password-change',
    pageComponent: 'FirstLoginPasswordPage',
    userStory: 'US5',
    themeMode: 'polymorphic-role-tokens',
    semanticTableException: 'none',
    requiredViewportsPx: [360, 768, 1440],
  },
] as const;

export const ADMIN_LEGACY_REDIRECTS = [
  { from: '/admin/analytics', to: '/admin' },
] as const;
