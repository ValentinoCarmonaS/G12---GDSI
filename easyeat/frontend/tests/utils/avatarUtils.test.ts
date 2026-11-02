import { describe, it, expect, beforeEach } from 'vitest';
import { getAuthenticatedAvatarUrl } from '../../src/utils/avatarUtils';

describe('avatarUtils (getAuthenticatedAvatarUrl)', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('returns empty string when url is falsy', () => {
    expect(getAuthenticatedAvatarUrl(null)).toBe('');
    expect(getAuthenticatedAvatarUrl(undefined)).toBe('');
    expect(getAuthenticatedAvatarUrl('')).toBe('');
  });

  it('returns blob: and data: urls unchanged', () => {
    expect(getAuthenticatedAvatarUrl('blob:http://localhost:3000/123')).toBe('blob:http://localhost:3000/123');
    expect(getAuthenticatedAvatarUrl('data:image/png;base64,abc')).toBe('data:image/png;base64,abc');
  });

  it('attaches token from sessionStorage and cachebuster when present', () => {
    sessionStorage.setItem('access_token', 'mock-jwt-token');
    const result = getAuthenticatedAvatarUrl('/api/v1/profile/avatar');
    expect(result).toContain('/api/v1/profile/avatar?');
    expect(result).toContain('token=mock-jwt-token');
    expect(result).toContain('_t=');
  });

  it('attaches with ampersand separator when url already has query string', () => {
    sessionStorage.setItem('access_token', 'mock-jwt-token');
    const result = getAuthenticatedAvatarUrl('/api/v1/profile/avatar?foo=bar');
    expect(result).toContain('/api/v1/profile/avatar?foo=bar&token=mock-jwt-token');
  });
});
