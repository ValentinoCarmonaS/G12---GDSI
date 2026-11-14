/**
 * Resolves relative backend media paths (/api/...) against the origin of
 * VITE_API_BASE_URL so <img> tags work when frontend and backend are hosted
 * on separate origins, while preserving relative paths for local dev/tests.
 */
export function resolveApiMediaUrl(
  url?: string | null,
  apiBaseUrl: string = import.meta.env.VITE_API_BASE_URL || ''
): string {
  if (!url) return '';
  if (
    url.startsWith('blob:') ||
    url.startsWith('data:') ||
    url.startsWith('//') ||
    /^https?:\/\//i.test(url)
  ) {
    return url;
  }

  const trimmedBase = apiBaseUrl.trim();
  if (/^https?:\/\//i.test(trimmedBase)) {
    try {
      const origin = new URL(trimmedBase).origin;
      return url.startsWith('/') ? `${origin}${url}` : `${origin}/${url}`;
    } catch {
      return url;
    }
  }

  return url;
}

