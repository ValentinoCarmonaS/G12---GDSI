/**
 * Contrato de tokens de tema por rol, resolución determinista por ruta
 * y acceso programático de visualización de datos.
 *
 * Especificación: specs/024-ui-theme-architecture/spec.md (FR-001 a FR-006, FR-019)
 * Nota: Artefacto de diseño (Phase 1). No es código ejecutable de producción.
 */

export type RoleThemeId = 'comensal' | 'mostrador' | 'chef' | 'admin';

export type EasyEatUserRole = 'COMENSAL' | 'ENCARGADO' | 'CHEF' | 'ADMINISTRADOR';

export type SemanticVariant = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

export interface SemanticTriad {
  /** Color de fondo de la superficie semántica (hex #RRGGBB). */
  readonly bg: string;
  /** Color de texto principal sobre bg; MUST cumplir contraste >= 4.5:1 (WCAG 2.2 AA). */
  readonly fg: string;
  /** Color de borde sobre superficie base; MUST cumplir contraste >= 3.0:1 (WCAG 2.2 AA 1.4.11). */
  readonly border: string;
}

export interface DataVizThemeTokens {
  /** Paleta categórica ordenada de al menos 5 series con contraste WCAG 2.2 AA sobre superficie. */
  readonly series: readonly [string, string, string, string, string, ...string[]];
  /** Color de líneas de grilla secundarias. */
  readonly gridStroke: string;
  /** Color de trazos de eje; contraste >= 3.0:1 sobre superficie. */
  readonly axisStroke: string;
  /** Color de etiquetas textuales de eje; contraste >= 4.5:1 sobre superficie y superficie elevada. */
  readonly axisLabelFg: string;
  /** Tríada accesible de tooltip (fg sobre bg >= 4.5:1). */
  readonly tooltip: SemanticTriad;
  /** Relleno translúcido para cursor / resaltado interactivo. */
  readonly cursorFill: string;
  /** Familia tipográfica principal del tema activo (sin Inter). */
  readonly fontFamilySans: string;
  /** Familia tipográfica monoespaciada (JetBrains Mono) para valores numéricos. */
  readonly fontFamilyMono: string;
}

export interface ThemeColorTokens {
  readonly surface: string;
  readonly surfaceElevated: string;
  readonly surfaceSubtle: string;
  readonly text: string;
  readonly textSecondary: string;
  readonly borderSubtle: string;
  readonly borderStrong: string;
  readonly primary: string;
  readonly onPrimary: string;
  readonly primaryHover: string;
  readonly primaryBorder: string;
  readonly secondary: string;
  readonly onSecondary: string;
  readonly secondaryHover: string;
  readonly secondaryBorder: string;
  readonly dangerSolid: string;
  readonly onDanger: string;
  readonly dangerHover: string;
  readonly focusRing: string;
}

export interface ThemeTypographyTokens {
  readonly displayFamily: string;
  readonly sansFamily: string;
  readonly monoFamily: string;
  readonly allowedWeights: {
    readonly display: readonly number[];
    readonly sans: readonly number[];
    readonly mono: readonly number[];
  };
}

export interface ThemeGeometryTokens {
  readonly radiusSm: string;
  readonly radiusMd: string;
  readonly radiusLg: string;
  readonly borderWidth: string;
  readonly spacingScalePx: readonly number[];
}

export interface ThemeElevationTokens {
  readonly shadowElevated: string;
  readonly surfaceElevatedGlass?: string;
  readonly backdropBlur: string;
}

export interface RoleVisualThemeContract {
  readonly id: RoleThemeId;
  readonly designName: 'Bento' | 'Bold' | 'Brutalism' | 'Glassmorphism';
  readonly sourceContractPath: string;
  readonly colors: ThemeColorTokens;
  readonly typography: ThemeTypographyTokens;
  readonly geometry: ThemeGeometryTokens;
  readonly elevation: ThemeElevationTokens;
  readonly semantics: Readonly<Record<SemanticVariant, SemanticTriad>>;
  readonly dataViz: DataVizThemeTokens;
}

export type ThemeResolutionReason =
  | 'first-login-user-role'
  | 'first-login-fallback-admin'
  | 'route-mostrador'
  | 'route-chef'
  | 'route-admin'
  | 'route-public-auth'
  | 'route-comensal'
  | 'universal-fallback-comensal';

export interface ThemeResolverInput {
  readonly pathname: string;
  readonly userRole?: EasyEatUserRole | null;
}

export interface ResolvedThemeScope {
  readonly themeId: RoleThemeId;
  readonly reason: ThemeResolutionReason;
}

/**
 * Resolución pura y determinista del tema activo por ruta (FR-004).
 * - `/first-login-password-change`: hereda el tema del rol autenticado, con respaldo a `'admin'`.
 * - `/mostrador*` y `/counter*`: `'mostrador'`.
 * - `/cocina*` y `/kds*`: `'chef'`.
 * - `/admin*`: `'admin'`.
 * - Rutas públicas/auth, dominio Comensal, raíz `/`, comodín `*` e hidratación inicial: `'comensal'`.
 */
export type ResolveThemeForRoute = (input: ThemeResolverInput) => ResolvedThemeScope;

/**
 * Punto de acceso programático al tema activo y su sub-contrato de visualización de datos (FR-019).
 * Derivado de la misma fuente única de verdad (`ROLE_THEME_REGISTRY`) que genera las variables `--ee-*`.
 */
export interface RoleThemeContextValue {
  readonly themeId: RoleThemeId;
  readonly contract: RoleVisualThemeContract;
  readonly dataViz: DataVizThemeTokens;
  readonly resolutionReason: ThemeResolutionReason;
}

export type UseRoleTheme = () => RoleThemeContextValue;
export type UseDataVizTokens = () => DataVizThemeTokens;
export type GetThemeContract = (themeId: RoleThemeId) => RoleVisualThemeContract;

/**
 * Variables CSS reservadas por capa (FR-005, FR-006):
 * - Capa de rol (`[data-theme="<id>"]`): prefijo exclusivo `--ee-*`.
 * - Capa de establecimiento (Spec 016, `BrandThemeProvider`): prefijo exclusivo `--brand-*`.
 * Queda prohibida cualquier intersección entre ambos conjuntos de variables.
 */
export const RESERVED_ROLE_CSS_PREFIX = '--ee-' as const;
export const RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX = '--brand-' as const;
