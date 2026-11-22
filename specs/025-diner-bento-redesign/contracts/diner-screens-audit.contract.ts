/**
 * Spec 025 — Diner Bento Redesign
 * Screen coverage, responsive viewport, and Impeccable Operate / WCAG 2.2 AA audit contract.
 */

export type DinerScreenId =
  | 'login'
  | 'register'
  | 'forgot-password'
  | 'reset-password'
  | 'verify-email'
  | 'discovery'
  | 'catalog'
  | 'checkout'
  | 'payment-return'
  | 'order-confirmation-tracking'
  | 'profile';

export interface DinerScreenAuditEntry {
  id: DinerScreenId;
  userStory: 'US1' | 'US2' | 'US3' | 'US4' | 'US5';
  routePatterns: readonly string[];
  pageFile: string;
  usesBrandThemeProvider: boolean;
  /** Whether the screen must adapt polymorphically when another role theme is active (e.g., staff deep-link). */
  requiresCrossRolePolymorphism: boolean;
  /** Required responsive layout summary across 360 / 768 / 1440 px. */
  bentoLayoutRule: string;
}

export const REQUIRED_AUDIT_VIEWPORTS_PX = [360, 768, 1440] as const;
export const MIN_PRIMARY_TOUCH_TARGET_PX = 44;
export const MAX_TRANSITION_DURATION_MS = 200;

export const DINER_SCREEN_AUDIT_MANIFEST: readonly DinerScreenAuditEntry[] = [
  {
    id: 'login',
    userStory: 'US1',
    routePatterns: ['/login'],
    pageFile: 'easyeat/frontend/src/pages/LoginPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: true,
    bentoLayoutRule: 'Centered AuthCardShell (100% --ee-*), single column responsive',
  },
  {
    id: 'register',
    userStory: 'US1',
    routePatterns: ['/register'],
    pageFile: 'easyeat/frontend/src/pages/RegisterPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Centered AuthCardShell, responsive form grid on sm+',
  },
  {
    id: 'forgot-password',
    userStory: 'US1',
    routePatterns: ['/forgot-password'],
    pageFile: 'easyeat/frontend/src/pages/ForgotPasswordPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Centered AuthCardShell, single column responsive',
  },
  {
    id: 'reset-password',
    userStory: 'US1',
    routePatterns: ['/reset-password', '/auth/reset-password'],
    pageFile: 'easyeat/frontend/src/pages/ResetPasswordPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Centered AuthCardShell, single column responsive',
  },
  {
    id: 'verify-email',
    userStory: 'US1',
    routePatterns: ['/verificar-email', '/verify-email'],
    pageFile: 'easyeat/frontend/src/pages/VerifyEmailPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Centered AuthCardShell with LoadingSkeleton / FeedbackState / StatusBadge',
  },
  {
    id: 'discovery',
    userStory: 'US1',
    routePatterns: ['/descubrimiento', '/discovery'],
    pageFile: 'easyeat/frontend/src/pages/EstablishmentDiscoveryPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Active tenant BentoCard + Search Hero BentoCard + 3-column Bento highlights grid (1-col mobile)',
  },
  {
    id: 'catalog',
    userStory: 'US2',
    routePatterns: ['/catalogo/*'],
    pageFile: 'easyeat/frontend/src/pages/CatalogPage.tsx',
    usesBrandThemeProvider: true,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Brand header + sticky category nav + collapsible desktop sidebar / mobile AccessibleDrawer + modular dish grid + CartDrawer',
  },
  {
    id: 'checkout',
    userStory: 'US3',
    routePatterns: ['/checkout'],
    pageFile: 'easyeat/frontend/src/pages/CheckoutPage.tsx',
    usesBrandThemeProvider: true,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: '2-column Bento desktop (config blocks left, sticky CheckoutSummary right); 1-column mobile',
  },
  {
    id: 'payment-return',
    userStory: 'US3',
    routePatterns: ['/checkout/payment-return'],
    pageFile: 'easyeat/frontend/src/pages/PaymentReturnPage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Centered BentoCard with semantic status, PendingAccreditationBanner and direct action buttons',
  },
  {
    id: 'order-confirmation-tracking',
    userStory: 'US4',
    routePatterns: ['/checkout/confirmacion/:orderId', '/pedidos/:orderId/seguimiento', '/seguimiento/:orderId'],
    pageFile: 'easyeat/frontend/src/pages/OrderConfirmationTrackingPage.tsx',
    usesBrandThemeProvider: true,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Brand confirmation header + connection StatusBadge + step progress + countdown/delay + Pickup PIN Hero block',
  },
  {
    id: 'profile',
    userStory: 'US5',
    routePatterns: ['/perfil', '/perfil#pedidos', '/profile'],
    pageFile: 'easyeat/frontend/src/pages/ProfilePage.tsx',
    usesBrandThemeProvider: false,
    requiresCrossRolePolymorphism: false,
    bentoLayoutRule: 'Segmented Bento role="tablist"; 2-column Bento grid in Mi perfil (1-col mobile); Bento cards grid in Mis pedidos',
  },
] as const;
