import { resolveApiMediaUrl } from './mediaUtils';

/**
 * Resolves relative avatar URLs and appends authentication token and cache buster
 * so standard HTML <img> tags can load secured avatar media across origins.
 */
export function getAuthenticatedAvatarUrl(url?: string | null): string {
  const resolvedUrl = resolveApiMediaUrl(url);
  if (!resolvedUrl) return '';
  if (resolvedUrl.startsWith('blob:') || resolvedUrl.startsWith('data:')) return resolvedUrl;
  const token = typeof window !== 'undefined' ? sessionStorage.getItem('access_token') : null;
  const separator = resolvedUrl.includes('?') ? '&' : '?';
  const tokenParam = token ? `token=${token}` : '';
  const timestamp = `_t=${Date.now()}`;
  const params = [tokenParam, timestamp].filter(Boolean).join('&');
  return `${resolvedUrl}${separator}${params}`;
}
