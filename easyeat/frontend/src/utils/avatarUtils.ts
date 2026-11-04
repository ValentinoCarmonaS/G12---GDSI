/**
 * Appends authentication token and cache buster to avatar image URLs
 * so standard HTML <img> tags can load secured avatar media.
 */
export function getAuthenticatedAvatarUrl(url?: string | null): string {
  if (!url) return '';
  if (url.startsWith('blob:') || url.startsWith('data:')) return url;
  const token = typeof window !== 'undefined' ? sessionStorage.getItem('access_token') : null;
  const separator = url.includes('?') ? '&' : '?';
  const tokenParam = token ? `token=${token}` : '';
  const timestamp = `_t=${Date.now()}`;
  const params = [tokenParam, timestamp].filter(Boolean).join('&');
  return `${url}${separator}${params}`;
}
