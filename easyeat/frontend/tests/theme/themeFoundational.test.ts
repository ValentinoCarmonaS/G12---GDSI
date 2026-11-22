import fs from 'node:fs';
import path from 'node:path';
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

const FRONTEND_ROOT = path.resolve(__dirname, '../..');

const REQUIRED_EE_CSS_VARS = [
  '--ee-surface',
  '--ee-surface-elevated',
  '--ee-surface-elevated-glass',
  '--ee-surface-subtle',
  '--ee-text',
  '--ee-text-secondary',
  '--ee-border-subtle',
  '--ee-border-strong',
  '--ee-primary',
  '--ee-on-primary',
  '--ee-primary-hover',
  '--ee-primary-border',
  '--ee-secondary',
  '--ee-on-secondary',
  '--ee-secondary-hover',
  '--ee-secondary-border',
  '--ee-danger-solid',
  '--ee-on-danger',
  '--ee-danger-hover',
  '--ee-focus-ring',
  '--ee-neutral-bg',
  '--ee-neutral-fg',
  '--ee-neutral-border',
  '--ee-info-bg',
  '--ee-info-fg',
  '--ee-info-border',
  '--ee-success-bg',
  '--ee-success-fg',
  '--ee-success-border',
  '--ee-warning-bg',
  '--ee-warning-fg',
  '--ee-warning-border',
  '--ee-danger-bg',
  '--ee-danger-fg',
  '--ee-danger-border',
  '--ee-font-display',
  '--ee-font-sans',
  '--ee-font-mono',
  '--ee-radius-sm',
  '--ee-radius-md',
  '--ee-radius-lg',
  '--ee-border-width',
  '--ee-shadow-elevated',
  '--ee-backdrop-blur',
  '--ee-dataviz-series-1',
  '--ee-dataviz-series-2',
  '--ee-dataviz-series-3',
  '--ee-dataviz-series-4',
  '--ee-dataviz-series-5',
  '--ee-dataviz-grid-stroke',
  '--ee-dataviz-axis-stroke',
  '--ee-dataviz-axis-label-fg',
  '--ee-dataviz-tooltip-bg',
  '--ee-dataviz-tooltip-fg',
  '--ee-dataviz-tooltip-border',
  '--ee-dataviz-cursor-fill',
] as const;

function extractSelectorBlock(css: string, selectorRegex: RegExp): string {
  const match = css.match(selectorRegex);
  expect(match, `Expected CSS block matching ${selectorRegex}`).not.toBeNull();
  return match?.[1] ?? '';
}

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

  describe('T006 — Isolated multi-theme CSS sheet src/styles/themes.css (FR-002, FR-003, FR-005, FR-006, FR-017, FR-018, FR-019)', () => {
    it('defines all four role theme scopes (:root + comensal fallback, mostrador, chef, admin) with complete --ee-* and --ee-dataviz-* tokens', () => {
      const themesCssPath = path.join(FRONTEND_ROOT, 'src', 'styles', 'themes.css');
      expect(fs.existsSync(themesCssPath), 'Expected src/styles/themes.css to exist').toBe(true);

      const css = fs.readFileSync(themesCssPath, 'utf-8');

      // Zero AI-Slop & strict layer isolation checks
      expect(css).not.toMatch(/\bInter\b/);
      expect(css).not.toContain('--brand-');

      const comensalBlock = extractSelectorBlock(
        css,
        /:root\s*,\s*\[data-theme="comensal"\]\s*\{([\s\S]*?)\}/,
      );
      const mostradorBlock = extractSelectorBlock(
        css,
        /\[data-theme="mostrador"\]\s*\{([\s\S]*?)\}/,
      );
      const chefBlock = extractSelectorBlock(css, /\[data-theme="chef"\]\s*\{([\s\S]*?)\}/);
      const adminBlock = extractSelectorBlock(css, /\[data-theme="admin"\]\s*\{([\s\S]*?)\}/);

      const blocks = [
        { name: 'comensal', content: comensalBlock },
        { name: 'mostrador', content: mostradorBlock },
        { name: 'chef', content: chefBlock },
        { name: 'admin', content: adminBlock },
      ];

      for (const { name, content } of blocks) {
        for (const cssVar of REQUIRED_EE_CSS_VARS) {
          expect(content, `Missing ${cssVar} in [data-theme="${name}"]`).toContain(`${cssVar}:`);
        }
      }

      // Key remediated WCAG 2.2 AA & identity spot checks from data-model.md
      expect(comensalBlock).toContain('--ee-surface: #FFF5E6;');
      expect(comensalBlock).toContain("--ee-font-sans: 'DM Sans'");

      expect(mostradorBlock).toContain('--ee-surface: #111111;');
      expect(mostradorBlock).toContain('--ee-text: #F8FAFC;');
      expect(mostradorBlock).toContain("--ee-font-display: 'Archivo Black'");
      expect(mostradorBlock).toContain("--ee-font-sans: 'Archivo'");
      expect(mostradorBlock).toContain('--ee-on-secondary: #0A0A0A;');

      expect(chefBlock).toContain('--ee-surface: #FFFFFF;');
      expect(chefBlock).toContain('--ee-primary: #DD614C;');
      expect(chefBlock).toContain('--ee-on-primary: #111827;');
      expect(chefBlock).toContain('--ee-primary-hover: #E67966;');
      expect(chefBlock).toContain("--ee-font-sans: 'Darker Grotesque'");

      expect(adminBlock).toContain('--ee-surface: #F4F7FC;');
      expect(adminBlock).toContain('--ee-surface-elevated-glass: rgba(255, 255, 255, 0.82);');
      expect(adminBlock).toContain('--ee-backdrop-blur: blur(12px);');
      expect(adminBlock).toContain("--ee-font-sans: 'Plus Jakarta Sans'");
    });

    it('declares :focus-visible outline rule, prefers-reduced-motion media query, and transitory legacy surface containers', () => {
      const themesCssPath = path.join(FRONTEND_ROOT, 'src', 'styles', 'themes.css');
      const css = fs.readFileSync(themesCssPath, 'utf-8');

      expect(css).toMatch(/:focus-visible\s*\{[\s\S]*outline:\s*2px\s+solid\s+var\(--ee-focus-ring\)/);
      expect(css).toMatch(/@media\s*\(\s*prefers-reduced-motion:\s*reduce\s*\)/);
      expect(css).toMatch(/\[data-legacy-surface="light"\]\s*\{[\s\S]*background-color:\s*#FFFFFF/i);
      expect(css).toMatch(/\[data-legacy-surface="light"\]\s*\{[\s\S]*color:\s*#111827/i);
      expect(css).toMatch(/\[data-legacy-surface="dark"\]\s*\{[\s\S]*background-color:\s*#0A0A0A/i);
      expect(css).toMatch(/\[data-legacy-surface="dark"\]\s*\{[\s\S]*color:\s*#F5F5F5/i);
    });
  });
});
