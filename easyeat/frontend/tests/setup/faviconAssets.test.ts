import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

const FRONTEND_ROOT = path.resolve(__dirname, '../..');

describe('Favicon & public brand assets integrity', () => {
  it('ensures every favicon href declared in index.html exists in public/ with valid binary header', () => {
    const indexHtml = fs.readFileSync(path.join(FRONTEND_ROOT, 'index.html'), 'utf-8');
    const iconLinkRegex = /<link[^>]+rel="(?:icon|shortcut icon|apple-touch-icon)"[^>]+href="([^"]+)"/g;
    const hrefs = Array.from(indexHtml.matchAll(iconLinkRegex), (m) => m[1]);

    expect(hrefs.length).toBeGreaterThanOrEqual(4);

    for (const href of hrefs) {
      const cleanPath = href.split('?')[0].replace(/^\//, '');
      const fullPath = path.join(FRONTEND_ROOT, 'public', cleanPath);
      expect(fs.existsSync(fullPath), `Missing public asset: ${cleanPath}`).toBe(true);

      const bytes = fs.readFileSync(fullPath);
      expect(bytes.length).toBeGreaterThan(100);

      if (cleanPath.endsWith('.png')) {
        // PNG magic bytes: 89 50 4E 47
        expect(Array.from(bytes.subarray(0, 4))).toEqual([0x89, 0x50, 0x4e, 0x47]);
      } else if (cleanPath.endsWith('.ico')) {
        // ICO magic bytes: 00 00 01 00
        expect(Array.from(bytes.subarray(0, 4))).toEqual([0x00, 0x00, 0x01, 0x00]);
      }
    }
  });

  it('ensures Dockerfile copies public/ so favicons and neutral brand fallback ship in Docker images', () => {
    const dockerfile = fs.readFileSync(path.join(FRONTEND_ROOT, 'Dockerfile'), 'utf-8');
    expect(dockerfile).toMatch(/COPY\s+public\/\s+\.\/public\//);
  });
});
