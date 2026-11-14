import { describe, it, expect } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';

describe('Frontend .dockerignore', () => {
  it('should exist and contain mandatory exclusion patterns', () => {
    const dockerignorePath = path.resolve(process.cwd(), '.dockerignore');
    expect(fs.existsSync(dockerignorePath)).toBe(true);

    const content = fs.readFileSync(dockerignorePath, 'utf-8');
    const lines = content
      .split('\n')
      .map((l) => l.trim())
      .filter((l) => l.length > 0 && !l.startsWith('#'));

    expect(lines).toContain('node_modules/');
    expect(lines).toContain('dist/');
    expect(lines).toContain('.git/');
    expect(lines).toContain('coverage/');
  });
});

