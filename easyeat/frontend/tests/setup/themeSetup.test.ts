import fs from 'node:fs';
import path from 'node:path';
import colors from 'tailwindcss/colors';
import { describe, expect, it } from 'vitest';
// @ts-expect-error tailwind.config.js is untyped JS module
import tailwindConfig from '../../tailwind.config.js';

const FRONTEND_ROOT = path.resolve(__dirname, '../..');

const REQUIRED_FONTSOURCE_PACKAGES = [
  '@fontsource/dm-sans',
  '@fontsource/archivo-black',
  '@fontsource/archivo',
  '@fontsource/darker-grotesque',
  '@fontsource/plus-jakarta-sans',
  '@fontsource/jetbrains-mono',
] as const;

const REQUIRED_FONT_WEIGHT_IMPORTS = [
  '@fontsource/dm-sans/400.css',
  '@fontsource/dm-sans/500.css',
  '@fontsource/dm-sans/600.css',
  '@fontsource/dm-sans/700.css',
  '@fontsource/archivo-black/400.css',
  '@fontsource/archivo/400.css',
  '@fontsource/archivo/500.css',
  '@fontsource/archivo/600.css',
  '@fontsource/archivo/700.css',
  '@fontsource/darker-grotesque/500.css',
  '@fontsource/darker-grotesque/600.css',
  '@fontsource/darker-grotesque/700.css',
  '@fontsource/darker-grotesque/800.css',
  '@fontsource/plus-jakarta-sans/400.css',
  '@fontsource/plus-jakarta-sans/500.css',
  '@fontsource/plus-jakarta-sans/600.css',
  '@fontsource/plus-jakarta-sans/700.css',
  '@fontsource/jetbrains-mono/400.css',
  '@fontsource/jetbrains-mono/500.css',
  '@fontsource/jetbrains-mono/600.css',
] as const;

describe('Phase 1 Setup — Theme architecture shared infrastructure (Spec 024)', () => {
  describe('T001 — Self-hosted @fontsource packages (FR-002)', () => {
    it('declares all 6 required @fontsource packages in package.json with zero Inter packages and resolves all 20 weight CSS files', () => {
      const packageJsonPath = path.join(FRONTEND_ROOT, 'package.json');
      const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf-8')) as {
        dependencies?: Record<string, string>;
        devDependencies?: Record<string, string>;
      };

      const deps = packageJson.dependencies ?? {};
      const allDeps = { ...deps, ...(packageJson.devDependencies ?? {}) };

      for (const pkg of REQUIRED_FONTSOURCE_PACKAGES) {
        expect(deps[pkg], `Missing dependency ${pkg} in package.json`).toBeDefined();
      }

      const interPackages = Object.keys(allDeps).filter((name) =>
        name.toLowerCase().includes('inter'),
      );
      expect(interPackages, 'Inter font packages are prohibited by Zero AI-Slop policy').toEqual(
        [],
      );

      for (const cssImport of REQUIRED_FONT_WEIGHT_IMPORTS) {
        const cssPath = path.join(FRONTEND_ROOT, 'node_modules', ...cssImport.split('/'));
        expect(fs.existsSync(cssPath), `Expected font CSS asset at ${cssPath}`).toBe(true);
      }
    });
  });

  describe('T002 — Strict 20 weight CSS imports in main.tsx (FR-002)', () => {
    it('imports strictly the 20 required @fontsource weight CSS files and no other fontsource weights', () => {
      const mainTsxPath = path.join(FRONTEND_ROOT, 'src', 'main.tsx');
      const source = fs.readFileSync(mainTsxPath, 'utf-8');

      const importedFontsource = Array.from(
        source.matchAll(/import\s+['"](@fontsource[^'"]+)['"]\s*;/g),
        (match) => match[1],
      );

      expect(importedFontsource).toHaveLength(20);
      expect([...importedFontsource].sort()).toEqual([...REQUIRED_FONT_WEIGHT_IMPORTS].sort());
      expect(
        importedFontsource.some((entry) => entry.toLowerCase().includes('inter')),
        'Inter font imports are prohibited',
      ).toBe(false);
    });
  });

  describe('T003 — Clean entry HTML for --ee-* inheritance (FR-002, FR-005)', () => {
    it('sets Spanish <title>EasyEat</title>, omits external font CDN links, and removes hardcoded bg-gray-50/text-gray-900 from <body>', () => {
      const indexHtmlPath = path.join(FRONTEND_ROOT, 'index.html');
      const html = fs.readFileSync(indexHtmlPath, 'utf-8');

      expect(html).toMatch(/<html\s+lang="es"\s*>/i);
      expect(html).toMatch(/<title>EasyEat<\/title>/);
      expect(html).not.toMatch(/fonts\.googleapis\.com|fonts\.gstatic\.com/i);

      const bodyMatch = html.match(/<body([^>]*)>/i);
      expect(bodyMatch, 'Expected <body> tag in index.html').not.toBeNull();
      const bodyAttrs = bodyMatch?.[1] ?? '';
      expect(bodyAttrs).not.toMatch(/\bbg-gray-50\b/);
      expect(bodyAttrs).not.toMatch(/\btext-gray-900\b/);
      expect(bodyAttrs).toMatch(/\bantialiased\b/);
      expect(bodyAttrs).toMatch(/\bmin-h-screen\b/);
    });
  });

  describe('T004 — Tailwind semantic ee-* utilities preserving legacy tokens (FR-003, FR-006, FR-018)', () => {
    it('extends tailwind.config.js with ee-* colors, fonts, radii, and shadows while preserving colors.primary and colors.brand intact', () => {
      const extend = (
        tailwindConfig as {
          theme?: {
            extend?: {
              colors?: Record<string, unknown>;
              fontFamily?: Record<string, unknown>;
              borderRadius?: Record<string, unknown>;
              boxShadow?: Record<string, unknown>;
            };
          };
        }
      ).theme?.extend;
      expect(extend).toBeDefined();

      // Legacy tokens from specs 001–023 MUST remain intact
      expect(extend?.colors?.primary).toEqual(colors.emerald);
      expect(extend?.colors?.brand).toEqual({
        dark: '#0b0b0d',
        accent: '#c9a96e',
        text: '#f0ede8',
      });

      // Semantic ee-* color tokens
      const eeColors = extend?.colors?.ee as Record<string, unknown> | undefined;
      expect(eeColors).toBeDefined();
      expect(eeColors?.surface).toBe('var(--ee-surface)');
      expect(eeColors?.['surface-elevated']).toBe('var(--ee-surface-elevated)');
      expect(eeColors?.['surface-subtle']).toBe('var(--ee-surface-subtle)');
      expect(eeColors?.text).toBe('var(--ee-text)');
      expect(eeColors?.['text-secondary']).toBe('var(--ee-text-secondary)');
      expect(eeColors?.['border-subtle']).toBe('var(--ee-border-subtle)');
      expect(eeColors?.['border-strong']).toBe('var(--ee-border-strong)');
      expect(eeColors?.primary).toBe('var(--ee-primary)');
      expect(eeColors?.['on-primary']).toBe('var(--ee-on-primary)');
      expect(eeColors?.['primary-hover']).toBe('var(--ee-primary-hover)');
      expect(eeColors?.['primary-border']).toBe('var(--ee-primary-border)');
      expect(eeColors?.secondary).toBe('var(--ee-secondary)');
      expect(eeColors?.['on-secondary']).toBe('var(--ee-on-secondary)');
      expect(eeColors?.['secondary-hover']).toBe('var(--ee-secondary-hover)');
      expect(eeColors?.['secondary-border']).toBe('var(--ee-secondary-border)');
      expect(eeColors?.['danger-solid']).toBe('var(--ee-danger-solid)');
      expect(eeColors?.['on-danger']).toBe('var(--ee-on-danger)');
      expect(eeColors?.['danger-hover']).toBe('var(--ee-danger-hover)');
      expect(eeColors?.['focus-ring']).toBe('var(--ee-focus-ring)');

      for (const variant of ['neutral', 'info', 'success', 'warning', 'danger'] as const) {
        expect(eeColors?.[variant]).toEqual({
          bg: `var(--ee-${variant}-bg)`,
          fg: `var(--ee-${variant}-fg)`,
          border: `var(--ee-${variant}-border)`,
        });
      }

      // Semantic ee-* typography, radii, and shadows
      expect(extend?.fontFamily?.['ee-display']).toEqual(['var(--ee-font-display)']);
      expect(extend?.fontFamily?.['ee-sans']).toEqual(['var(--ee-font-sans)']);
      expect(extend?.fontFamily?.['ee-mono']).toEqual(['var(--ee-font-mono)']);
      expect(extend?.borderRadius?.['ee-sm']).toBe('var(--ee-radius-sm)');
      expect(extend?.borderRadius?.['ee-md']).toBe('var(--ee-radius-md)');
      expect(extend?.borderRadius?.['ee-lg']).toBe('var(--ee-radius-lg)');
      expect(extend?.boxShadow?.['ee-elevated']).toBe('var(--ee-shadow-elevated)');

      // Strict layer isolation: ee-* tokens MUST NOT reference --brand-* (Spec 016)
      expect(JSON.stringify(eeColors)).not.toContain('--brand-');
    });
  });
});
