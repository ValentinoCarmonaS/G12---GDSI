import { describe, expect, expectTypeOf, it } from 'vitest';
import type { Role } from '../../src/types/auth';
import {
  RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX,
  RESERVED_ROLE_CSS_PREFIX,
  type DataVizThemeTokens,
  type EasyEatUserRole,
  type GetThemeContract,
  type ResolveThemeForRoute,
  type ResolvedThemeScope,
  type RoleThemeContextValue,
  type RoleThemeId,
  type RoleVisualThemeContract,
  type SemanticTriad,
  type SemanticVariant,
  type ThemeColorTokens,
  type ThemeElevationTokens,
  type ThemeGeometryTokens,
  type ThemeResolutionReason,
  type ThemeResolverInput,
  type ThemeTypographyTokens,
  type UseDataVizTokens,
  type UseRoleTheme,
} from '../../src/theme/types';

describe('Phase 2 Foundational — Theme types and multi-theme CSS (Spec 024)', () => {
  describe('T005 — Executable theme contract types (FR-003, FR-004, FR-005, FR-006, FR-019)', () => {
    it('exports isolated CSS layer prefixes with zero overlap between role and establishment brand layers', () => {
      expect(RESERVED_ROLE_CSS_PREFIX).toBe('--ee-');
      expect(RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX).toBe('--brand-');
      expect(RESERVED_ROLE_CSS_PREFIX).not.toBe(RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX);
    });

    it('aligns EasyEatUserRole with src/types/auth Role and preserves all theme contract shapes', () => {
      expectTypeOf<EasyEatUserRole>().toEqualTypeOf<Role>();
      expectTypeOf<RoleThemeId>().toEqualTypeOf<'comensal' | 'mostrador' | 'chef' | 'admin'>();
      expectTypeOf<SemanticVariant>().toEqualTypeOf<
        'neutral' | 'info' | 'success' | 'warning' | 'danger'
      >();

      expectTypeOf<SemanticTriad>().toMatchTypeOf<{
        readonly bg: string;
        readonly fg: string;
        readonly border: string;
      }>();

      expectTypeOf<DataVizThemeTokens>().toMatchTypeOf<{
        readonly series: readonly [string, string, string, string, string, ...string[]];
        readonly gridStroke: string;
        readonly axisStroke: string;
        readonly axisLabelFg: string;
        readonly tooltip: SemanticTriad;
        readonly cursorFill: string;
        readonly fontFamilySans: string;
        readonly fontFamilyMono: string;
      }>();

      expectTypeOf<RoleVisualThemeContract>().toMatchTypeOf<{
        readonly id: RoleThemeId;
        readonly designName: 'Bento' | 'Bold' | 'Brutalism' | 'Glassmorphism';
        readonly sourceContractPath: string;
        readonly colors: ThemeColorTokens;
        readonly typography: ThemeTypographyTokens;
        readonly geometry: ThemeGeometryTokens;
        readonly elevation: ThemeElevationTokens;
        readonly semantics: Readonly<Record<SemanticVariant, SemanticTriad>>;
        readonly dataViz: DataVizThemeTokens;
      }>();

      expectTypeOf<ResolvedThemeScope>().toEqualTypeOf<{
        readonly themeId: RoleThemeId;
        readonly reason: ThemeResolutionReason;
      }>();

      expectTypeOf<RoleThemeContextValue>().toEqualTypeOf<{
        readonly themeId: RoleThemeId;
        readonly contract: RoleVisualThemeContract;
        readonly dataViz: DataVizThemeTokens;
        readonly resolutionReason: ThemeResolutionReason;
      }>();

      expectTypeOf<ResolveThemeForRoute>().toEqualTypeOf<
        (input: ThemeResolverInput) => ResolvedThemeScope
      >();
      expectTypeOf<UseRoleTheme>().toEqualTypeOf<() => RoleThemeContextValue>();
      expectTypeOf<UseDataVizTokens>().toEqualTypeOf<() => DataVizThemeTokens>();
      expectTypeOf<GetThemeContract>().toEqualTypeOf<
        (themeId: RoleThemeId) => RoleVisualThemeContract
      >();
    });
  });
});
