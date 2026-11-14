import { describe, it, expect, afterEach, vi } from 'vitest';
import { resolveApiMediaUrl } from '../../src/utils/mediaUtils';

describe('mediaUtils (resolveApiMediaUrl)', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('returns empty string for falsy values', () => {
    expect(resolveApiMediaUrl(null)).toBe('');
    expect(resolveApiMediaUrl(undefined)).toBe('');
    expect(resolveApiMediaUrl('')).toBe('');
  });

  it('preserves blob:, data:, protocol-relative, and absolute http(s) URLs', () => {
    const apiBase = 'https://easyeat-backend.onrender.com/api/v1';
    expect(resolveApiMediaUrl('blob:https://easyeat-frontend.onrender.com/123', apiBase)).toBe(
      'blob:https://easyeat-frontend.onrender.com/123'
    );
    expect(resolveApiMediaUrl('data:image/png;base64,abc', apiBase)).toBe('data:image/png;base64,abc');
    expect(resolveApiMediaUrl('//cdn.example.com/a.png', apiBase)).toBe('//cdn.example.com/a.png');
    expect(resolveApiMediaUrl('https://cdn.example.com/a.png', apiBase)).toBe('https://cdn.example.com/a.png');
    expect(resolveApiMediaUrl('http://localhost:8080/api/v1/a.png', apiBase)).toBe(
      'http://localhost:8080/api/v1/a.png'
    );
  });

  it('keeps relative path untouched when apiBaseUrl is relative or empty', () => {
    expect(resolveApiMediaUrl('/api/v1/dishes/1/photo', '/api/v1')).toBe('/api/v1/dishes/1/photo');
    expect(resolveApiMediaUrl('/api/v1/dishes/1/photo', '')).toBe('/api/v1/dishes/1/photo');
  });

  it('prepends origin from explicit absolute apiBaseUrl', () => {
    expect(
      resolveApiMediaUrl('/api/v1/dishes/1/photo', 'https://easyeat-backend.onrender.com/api/v1')
    ).toBe('https://easyeat-backend.onrender.com/api/v1/dishes/1/photo');
    expect(
      resolveApiMediaUrl('api/v1/profile/avatar', 'https://easyeat-backend.onrender.com/api/v1/')
    ).toBe('https://easyeat-backend.onrender.com/api/v1/profile/avatar');
  });

  it('reads VITE_API_BASE_URL from environment when explicit parameter is omitted', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://easyeat-backend.onrender.com/api/v1');
    expect(resolveApiMediaUrl('/api/v1/dishes/1/photo')).toBe(
      'https://easyeat-backend.onrender.com/api/v1/dishes/1/photo'
    );
  });
});

