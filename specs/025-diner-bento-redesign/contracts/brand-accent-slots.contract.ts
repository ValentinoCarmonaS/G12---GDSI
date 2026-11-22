/**
 * Spec 025 — Diner Bento Redesign
 * Interface contract for Focused Brand Accent Slots (Spec 016 + FR-008)
 * and Bento-aligned WCAG 2.2 AA neutral branding fallback.
 */

export type BrandAccentSlotId = 'header' | 'category' | 'cta' | 'confirmation';

export interface BrandAccentSlotSpec {
  slotId: BrandAccentSlotId;
  /** Routes where this focused brand slot is active. */
  routes: readonly string[];
  /** CSS variables consumed from BrandThemeProvider (`--brand-*`). */
  allowedBrandVars: readonly (
    | '--brand-primary'
    | '--brand-primary-contrast'
    | '--brand-secondary'
    | '--brand-secondary-contrast'
    | '--brand-accent'
    | '--brand-accent-contrast'
  )[];
  /** DOM marker used for automated isolation & audit checks. */
  dataAttribute: `data-brand-slot="${BrandAccentSlotId}"`;
}

export const FOCUSED_BRAND_ACCENT_SLOTS: readonly BrandAccentSlotSpec[] = [
  {
    slotId: 'header',
    routes: ['/catalogo/*'],
    allowedBrandVars: ['--brand-primary', '--brand-primary-contrast', '--brand-secondary', '--brand-secondary-contrast'],
    dataAttribute: 'data-brand-slot="header"',
  },
  {
    slotId: 'category',
    routes: ['/catalogo/*'],
    allowedBrandVars: ['--brand-primary', '--brand-primary-contrast'],
    dataAttribute: 'data-brand-slot="category"',
  },
  {
    slotId: 'cta',
    routes: ['/catalogo/*', '/checkout'],
    allowedBrandVars: ['--brand-primary', '--brand-primary-contrast'],
    dataAttribute: 'data-brand-slot="cta"',
  },
  {
    slotId: 'confirmation',
    routes: ['/checkout/confirmacion/:orderId', '/pedidos/:orderId/seguimiento', '/seguimiento/:orderId'],
    allowedBrandVars: ['--brand-primary', '--brand-primary-contrast', '--brand-secondary', '--brand-secondary-contrast'],
    dataAttribute: 'data-brand-slot="confirmation"',
  },
] as const;

/**
 * Updated WCAG 2.2 AA Bento-aligned neutral branding defaults for `DEFAULT_NEUTRAL_BRANDING`
 * in `easyeat/frontend/src/hooks/useRestaurantBranding.ts`.
 *
 * Contrast verification:
 * - #111827 on #FAD4C0 = 12.88:1 (AAA)
 * - #111827 on #80A1C1 = 6.58:1 (AA)
 * - #FFFFFF on #9A4A24 = 5.97:1 (AA)
 */
export const BENTO_NEUTRAL_BRANDING_COLORS = {
  primaryColor: '#FAD4C0',
  primaryContrastColor: '#111827',
  secondaryColor: '#80A1C1',
  secondaryContrastColor: '#111827',
  accentColor: '#9A4A24',
  accentContrastColor: '#FFFFFF',
} as const;
