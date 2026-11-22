import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { getContrastRatio, isValidHexColor } from '../../src/utils/colorContrastUtils';
import { ROLE_THEME_REGISTRY, getThemeContract } from '../../src/theme/themeRegistry';
import type { RoleThemeId, SemanticVariant } from '../../src/theme/types';

const REPO_ROOT = path.resolve(__dirname, '../../../..');
const DESIGNS_DIR = path.join(REPO_ROOT, '.specify', 'memory', 'designs');
const CONSTITUTION_PATH = path.join(REPO_ROOT, '.specify', 'memory', 'constitution.md');
const THEMES_CSS_PATH = path.resolve(__dirname, '../../src/styles/themes.css');

const ROLE_THEME_IDS: readonly RoleThemeId[] = ['comensal', 'mostrador', 'chef', 'admin'];
const SEMANTIC_VARIANTS: readonly SemanticVariant[] = [
  'neutral',
  'info',
  'success',
  'warning',
  'danger',
];

const DESIGN_FILES: Record<RoleThemeId, string> = {
  comensal: 'comensal.DESIGN.md',
  mostrador: 'mostrador.DESIGN.md',
  chef: 'chef.DESIGN.md',
  admin: 'admin.DESIGN.md',
};

function readDesignContract(themeId: RoleThemeId): string {
  return fs.readFileSync(path.join(DESIGNS_DIR, DESIGN_FILES[themeId]), 'utf-8');
}

function extractCssThemeBlock(css: string, themeId: RoleThemeId): string {
  const marker = `[data-theme="${themeId}"]`;
  const startIndex = css.indexOf(marker);
  if (startIndex === -1) return '';
  const openBrace = css.indexOf('{', startIndex);
  const closeBrace = css.indexOf('}', openBrace);
  return css.slice(openBrace + 1, closeBrace);
}

function readCssVar(block: string, varName: string): string | null {
  const escaped = varName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = block.match(new RegExp(`${escaped}:\\s*([^;]+);`));
  return match ? match[1].trim() : null;
}

describe('Role Visual Theme Contracts & WCAG 2.2 AA Contrast (Spec 024 - US1)', () => {
  describe('Design contracts (.specify/memory/designs/*.DESIGN.md) & Constitution remediation', () => {
    it('contains zero occurrences of prohibited Inter typography across all four .DESIGN.md contracts', () => {
      for (const themeId of ROLE_THEME_IDS) {
        const content = readDesignContract(themeId);
        expect(content, `${DESIGN_FILES[themeId]} must not reference Inter`).not.toMatch(/\bInter\b/);
      }
    });

    it('documents remediated typography, WCAG base pairs, semantic triads, and dataViz in each .DESIGN.md', () => {
      const comensal = readDesignContract('comensal');
      expect(comensal).toContain('DM Sans');
      expect(comensal).toContain('JetBrains Mono');
      expect(comensal).toContain('#FFF5E6');
      expect(comensal).toContain('#9A4A24');
      expect(comensal).toContain('dataViz');

      const mostrador = readDesignContract('mostrador');
      expect(mostrador).toContain('Archivo Black');
      expect(mostrador).toContain('Archivo');
      expect(mostrador).toContain('#111111');
      expect(mostrador).toContain('#F8FAFC');
      expect(mostrador).toContain('#0A0A0A');
      expect(mostrador).toContain('dataViz');

      const chef = readDesignContract('chef');
      expect(chef).toContain('Darker Grotesque');
      expect(chef).toContain('#DD614C');
      expect(chef).toContain('#E67966');
      expect(chef).toContain('#111827');
      expect(chef).toContain('dataViz');

      const admin = readDesignContract('admin');
      expect(admin).toContain('Plus Jakarta Sans');
      expect(admin).toContain('#F4F7FC');
      expect(admin).toContain('rgba(255, 255, 255, 0.82)');
      expect(admin).toContain('blur(12px)');
      expect(admin).toContain('#C81433');
      expect(admin).toContain('dataViz');
    });

    it('closes the pending TODO(DESIGN_CONTRACTS_REMEDIATION) in .specify/memory/constitution.md', () => {
      const constitution = fs.readFileSync(CONSTITUTION_PATH, 'utf-8');
      expect(constitution).not.toContain('TODO(DESIGN_CONTRACTS_REMEDIATION)');
    });
  });

  describe('ROLE_THEME_REGISTRY single source of truth & WCAG 2.2 AA contrast matrix (148 base + 20 elevated DataViz = 168 pairs)', () => {
    it('exposes all four role contracts via ROLE_THEME_REGISTRY and getThemeContract without Inter', () => {
      for (const themeId of ROLE_THEME_IDS) {
        const contract = getThemeContract(themeId);
        expect(contract).toBe(ROLE_THEME_REGISTRY[themeId]);
        expect(contract.id).toBe(themeId);
        expect(contract.sourceContractPath).toBe(`.specify/memory/designs/${DESIGN_FILES[themeId]}`);

        expect(contract.typography.displayFamily).not.toMatch(/\bInter\b/);
        expect(contract.typography.sansFamily).not.toMatch(/\bInter\b/);
        expect(contract.typography.monoFamily).not.toMatch(/\bInter\b/);
      }
    });

    it('passes all 168 WCAG 2.2 AA contrast checks across all four role themes', () => {
      let basePairCount = 0;
      let elevatedSeriesPairCount = 0;

      for (const themeId of ROLE_THEME_IDS) {
        const { colors, semantics, dataViz } = getThemeContract(themeId);

        const textPairs: Array<[string, string, string]> = [
          ['colors.text on colors.surface', colors.text, colors.surface],
          ['colors.text on colors.surfaceElevated', colors.text, colors.surfaceElevated],
          ['colors.textSecondary on colors.surface', colors.textSecondary, colors.surface],
          ['colors.textSecondary on colors.surfaceElevated', colors.textSecondary, colors.surfaceElevated],
          ['colors.onPrimary on colors.primary', colors.onPrimary, colors.primary],
          ['colors.onPrimary on colors.primaryHover', colors.onPrimary, colors.primaryHover],
          ['colors.onSecondary on colors.secondary', colors.onSecondary, colors.secondary],
          ['colors.onSecondary on colors.secondaryHover', colors.onSecondary, colors.secondaryHover],
          ['colors.onDanger on colors.dangerSolid', colors.onDanger, colors.dangerSolid],
          ['colors.onDanger on colors.dangerHover', colors.onDanger, colors.dangerHover],
        ];

        for (const [label, fg, bg] of textPairs) {
          const ratio = getContrastRatio(fg, bg);
          expect(ratio, `[${themeId}] ${label} (${fg} on ${bg})`).toBeGreaterThanOrEqual(4.5);
          basePairCount += 1;
        }

        const uiComponentPairs: Array<[string, string, string]> = [
          ['colors.borderStrong on colors.surface', colors.borderStrong, colors.surface],
          ['colors.borderStrong on colors.surfaceElevated', colors.borderStrong, colors.surfaceElevated],
          ['colors.primaryBorder on colors.surface', colors.primaryBorder, colors.surface],
          ['colors.primaryBorder on colors.surfaceElevated', colors.primaryBorder, colors.surfaceElevated],
          ['colors.secondaryBorder on colors.surface', colors.secondaryBorder, colors.surface],
          ['colors.secondaryBorder on colors.surfaceElevated', colors.secondaryBorder, colors.surfaceElevated],
          ['colors.focusRing on colors.surface', colors.focusRing, colors.surface],
          ['colors.focusRing on colors.surfaceElevated', colors.focusRing, colors.surfaceElevated],
        ];

        for (const [label, fg, bg] of uiComponentPairs) {
          const ratio = getContrastRatio(fg, bg);
          expect(ratio, `[${themeId}] ${label} (${fg} on ${bg})`).toBeGreaterThanOrEqual(3.0);
          basePairCount += 1;
        }

        for (const variant of SEMANTIC_VARIANTS) {
          const triad = semantics[variant];
          const fgRatio = getContrastRatio(triad.fg, triad.bg);
          expect(
            fgRatio,
            `[${themeId}] semantics.${variant}.fg on bg (${triad.fg} on ${triad.bg})`
          ).toBeGreaterThanOrEqual(4.5);
          basePairCount += 1;

          const borderRatio = getContrastRatio(triad.border, colors.surface);
          expect(
            borderRatio,
            `[${themeId}] semantics.${variant}.border on surface (${triad.border} on ${colors.surface})`
          ).toBeGreaterThanOrEqual(3.0);
          basePairCount += 1;
        }

        expect(dataViz.series.length).toBeGreaterThanOrEqual(5);
        dataViz.series.slice(0, 5).forEach((seriesColor, idx) => {
          const onSurface = getContrastRatio(seriesColor, colors.surface);
          expect(
            onSurface,
            `[${themeId}] dataViz.series[${idx}] on surface (${seriesColor} on ${colors.surface})`
          ).toBeGreaterThanOrEqual(3.0);
          basePairCount += 1;

          const onElevated = getContrastRatio(seriesColor, colors.surfaceElevated);
          expect(
            onElevated,
            `[${themeId}] dataViz.series[${idx}] on surfaceElevated (${seriesColor} on ${colors.surfaceElevated})`
          ).toBeGreaterThanOrEqual(3.0);
          elevatedSeriesPairCount += 1;
        });

        expect(
          getContrastRatio(dataViz.axisStroke, colors.surface),
          `[${themeId}] dataViz.axisStroke on surface`
        ).toBeGreaterThanOrEqual(3.0);
        basePairCount += 1;

        expect(
          getContrastRatio(dataViz.axisLabelFg, colors.surface),
          `[${themeId}] dataViz.axisLabelFg on surface`
        ).toBeGreaterThanOrEqual(4.5);
        basePairCount += 1;

        expect(
          getContrastRatio(dataViz.axisLabelFg, colors.surfaceElevated),
          `[${themeId}] dataViz.axisLabelFg on surfaceElevated`
        ).toBeGreaterThanOrEqual(4.5);
        basePairCount += 1;

        expect(
          getContrastRatio(dataViz.tooltip.fg, dataViz.tooltip.bg),
          `[${themeId}] dataViz.tooltip.fg on tooltip.bg`
        ).toBeGreaterThanOrEqual(4.5);
        basePairCount += 1;
      }

      expect(basePairCount).toBe(148);
      expect(elevatedSeriesPairCount).toBe(20);
      expect(basePairCount + elevatedSeriesPairCount).toBe(168);
    });

    it('derives DataVizThemeTokens from the same single source of truth as themes.css and typography', () => {
      const css = fs.readFileSync(THEMES_CSS_PATH, 'utf-8');

      for (const themeId of ROLE_THEME_IDS) {
        const contract = getThemeContract(themeId);
        const { colors, typography, dataViz } = contract;
        const cssBlock = extractCssThemeBlock(css, themeId);

        expect(cssBlock.length).toBeGreaterThan(0);
        expect(dataViz.fontFamilySans).toBe(typography.sansFamily);
        expect(dataViz.fontFamilyMono).toBe(typography.monoFamily);

        const uniqueSeries = new Set(dataViz.series.map((c) => c.toUpperCase()));
        expect(uniqueSeries.size).toBe(dataViz.series.length);
        for (const color of dataViz.series) {
          expect(isValidHexColor(color)).toBe(true);
        }

        // Cross-check CSS tokens against ROLE_THEME_REGISTRY single source of truth
        expect(readCssVar(cssBlock, '--ee-surface')).toBe(colors.surface);
        expect(readCssVar(cssBlock, '--ee-surface-elevated')).toBe(colors.surfaceElevated);
        expect(readCssVar(cssBlock, '--ee-text')).toBe(colors.text);
        expect(readCssVar(cssBlock, '--ee-primary')).toBe(colors.primary);
        expect(readCssVar(cssBlock, '--ee-on-primary')).toBe(colors.onPrimary);
        expect(readCssVar(cssBlock, '--ee-secondary')).toBe(colors.secondary);
        expect(readCssVar(cssBlock, '--ee-on-secondary')).toBe(colors.onSecondary);
        expect(readCssVar(cssBlock, '--ee-danger-solid')).toBe(colors.dangerSolid);
        expect(readCssVar(cssBlock, '--ee-focus-ring')).toBe(colors.focusRing);

        expect(readCssVar(cssBlock, '--ee-dataviz-series-1')).toBe(dataViz.series[0]);
        expect(readCssVar(cssBlock, '--ee-dataviz-series-2')).toBe(dataViz.series[1]);
        expect(readCssVar(cssBlock, '--ee-dataviz-series-3')).toBe(dataViz.series[2]);
        expect(readCssVar(cssBlock, '--ee-dataviz-series-4')).toBe(dataViz.series[3]);
        expect(readCssVar(cssBlock, '--ee-dataviz-series-5')).toBe(dataViz.series[4]);
        expect(readCssVar(cssBlock, '--ee-dataviz-grid-stroke')).toBe(dataViz.gridStroke);
        expect(readCssVar(cssBlock, '--ee-dataviz-axis-stroke')).toBe(dataViz.axisStroke);
        expect(readCssVar(cssBlock, '--ee-dataviz-axis-label-fg')).toBe(dataViz.axisLabelFg);
        expect(readCssVar(cssBlock, '--ee-dataviz-tooltip-bg')).toBe(dataViz.tooltip.bg);
        expect(readCssVar(cssBlock, '--ee-dataviz-tooltip-fg')).toBe(dataViz.tooltip.fg);
        expect(readCssVar(cssBlock, '--ee-dataviz-tooltip-border')).toBe(dataViz.tooltip.border);
        expect(readCssVar(cssBlock, '--ee-dataviz-cursor-fill')).toBe(dataViz.cursorFill);
      }
    });
  });
});
