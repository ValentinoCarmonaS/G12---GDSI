/**
 * Spec 027 — Chef Brutalism Redesign
 * Contract for One-Tap Kitchen Navigation (desktop >= 768px + mobile < 768px)
 * and Full-Width Kitchen Display System (KDS) ergonomics in `/cocina`.
 */

export interface ChefNavDestination {
  readonly id: 'chef-kds' | 'chef-platos' | 'chef-ingredientes';
  readonly desktopLabel: string;
  readonly mobileLabel: string;
  readonly to: '/cocina' | '/cocina/platos' | '/cocina/ingredientes';
  readonly matchStrategy: 'exact' | 'prefix';
}

export const CHEF_NAVIGATION_DESTINATIONS: readonly ChefNavDestination[] = [
  {
    id: 'chef-kds',
    desktopLabel: 'Comandas KDS',
    mobileLabel: 'Comandas KDS',
    to: '/cocina',
    matchStrategy: 'exact',
  },
  {
    id: 'chef-platos',
    desktopLabel: 'Gestionar Platos',
    mobileLabel: 'Platos',
    to: '/cocina/platos',
    matchStrategy: 'prefix',
  },
  {
    id: 'chef-ingredientes',
    desktopLabel: 'Ingredientes',
    mobileLabel: 'Ingredientes',
    to: '/cocina/ingredientes',
    matchStrategy: 'prefix',
  },
] as const;

export interface ChefMobileBottomBarProps {
  readonly className?: string;
}

export interface RoleLayoutShellChefExtension {
  /** Removes max-w-7xl constraint on `<main>`; enabled exclusively in KitchenWorkspacePage. */
  readonly fullWidth?: boolean;
  /** RoleNavigationConfig flag; enabled exclusively for CHEF (`< 768px`). */
  readonly useMobileBottomBar?: boolean;
  /** Reserves bottom clearance above fixed ChefMobileBottomBar on `< 768px`. */
  readonly mobileBottomClearanceClass: 'pb-24 md:pb-6';
  /** Desktop C-08 header link minimum target height in pixels. */
  readonly desktopHeaderLinkMinHeightPx: 48;
  /** Distinct accessible landmark label for ChefMobileBottomBar (must not collide with desktop "Secciones de cocina"). */
  readonly mobileBottomBarAriaLabel: 'Navegación rápida móvil de cocina';
}

export type KdsMobileColumnKey = 'queued' | 'in-preparation';

export interface KdsErgonomicsContract {
  readonly fullWidthWorkspace: true;
  readonly removeRedundantIngredientsBlock: true;
  readonly kpiStripPosition: 'top-compact';
  readonly kpiLabels: readonly ['En Cola', 'En Preparación', 'Para Retirar', 'Completadas hoy'];
  readonly mobileColumnSwitcherMinHeightPx: 56;
  readonly startButtonMinHeightPx: 64;
  readonly readyButtonMinHeightPx: 64;
  readonly delayButtonMinSizePx: 56;
  readonly minControlSeparationPx: 12;
  readonly delayAlertThresholdMinutes: 15;
  readonly delayAlertBorderWidthPx: 4;
  readonly keepDishTextNeutralOnDelay: true;
  readonly readyDispatchRequiresConfirmationModal: false;
  readonly perDishCheckboxesAllowed: false;
  readonly showPollingErrorBannerWithoutClearingTickets: true;
  readonly preservedTestIds: readonly [
    'kds-kanban-section',
    'queued-column',
    'in-preparation-column',
    'kds-column-in-preparation',
    'delay-badge',
  ];
}
