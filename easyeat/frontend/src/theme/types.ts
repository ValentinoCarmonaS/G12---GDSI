import type { Role } from '../types/auth';

export type RoleThemeId = 'comensal' | 'mostrador' | 'chef' | 'admin';

export type EasyEatUserRole = Role;

export type SemanticVariant = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

export interface SemanticTriad {
  readonly bg: string;
  readonly fg: string;
  readonly border: string;
}

export interface DataVizThemeTokens {
  readonly series: readonly [string, string, string, string, string, ...string[]];
  readonly gridStroke: string;
  readonly axisStroke: string;
  readonly axisLabelFg: string;
  readonly tooltip: SemanticTriad;
  readonly cursorFill: string;
  readonly fontFamilySans: string;
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
  | 'root-role-redirect'
  | 'login-staff-deeplink'
  | 'route-mostrador'
  | 'route-chef'
  | 'route-admin'
  | 'route-public-auth'
  | 'route-comensal'
  | 'universal-fallback-comensal';

export interface ThemeResolverInput {
  readonly pathname: string;
  readonly userRole?: EasyEatUserRole | null;
  readonly fromPath?: string | null;
}

export interface ResolvedThemeScope {
  readonly themeId: RoleThemeId;
  readonly reason: ThemeResolutionReason;
}

export type ResolveThemeForRoute = (input: ThemeResolverInput) => ResolvedThemeScope;

export interface RoleThemeContextValue {
  readonly themeId: RoleThemeId;
  readonly contract: RoleVisualThemeContract;
  readonly dataViz: DataVizThemeTokens;
  readonly resolutionReason: ThemeResolutionReason;
}

export type UseRoleTheme = () => RoleThemeContextValue;
export type UseDataVizTokens = () => DataVizThemeTokens;
export type GetThemeContract = (themeId: RoleThemeId) => RoleVisualThemeContract;

export const RESERVED_ROLE_CSS_PREFIX = '--ee-' as const;
export const RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX = '--brand-' as const;
