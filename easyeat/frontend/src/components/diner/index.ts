/**
 * Spec 025 — Diner Bento Redesign
 * Shared Bento layout blocks and accessible overlays barrel export.
 * Contract: specs/025-diner-bento-redesign/contracts/bento-ui-blocks.contract.ts
 */

export { BentoCard } from './BentoCard';
export type {
  BentoCardProps,
  BentoCardVariant,
  BentoCardPadding,
  BentoCardRadius,
} from './BentoCard';

export { AuthCardShell } from './AuthCardShell';
export type { AuthCardShellProps } from './AuthCardShell';

export { useAccessibleOverlay } from './useAccessibleOverlay';
export type { UseAccessibleOverlayOptions } from './useAccessibleOverlay';

export { AccessibleModal } from './AccessibleModal';
export type { AccessibleModalProps } from './AccessibleModal';

export { AccessibleDrawer } from './AccessibleDrawer';
export type { AccessibleDrawerProps } from './AccessibleDrawer';
