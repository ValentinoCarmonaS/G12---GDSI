import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

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
});
