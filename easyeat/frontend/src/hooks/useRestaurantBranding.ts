import { useState, useEffect } from 'react';
import { getDinerBranding, RestaurantBranding } from '../api/brandingApi';
import { useClientCache } from '../context/ClientCacheContext';

export const DEFAULT_NEUTRAL_BRANDING: RestaurantBranding = {
  establishmentId: '',
  fantasyName: 'Restaurante EasyEat',
  description: 'Restaurante asociado a EasyEat',
  logoUrl: '/assets/brand/easyeat-logo-neutral.png',
  primaryColor: '#FAD4C0',
  secondaryColor: '#80A1C1',
  accentColor: '#9A4A24',
  primaryContrastColor: '#111827',
  secondaryContrastColor: '#111827',
  accentContrastColor: '#FFFFFF',
  updatedAt: new Date().toISOString(),
  isDefaultFallback: true,
};

/**
 * Hook to retrieve restaurant branding triad and visual identity,
 * applying neutral platform fallback if absent or upon network error (INV-010, CB-011).
 * Integrated with ClientSWRStore for in-flight deduplication and instant SWR across views (FR-007, FR-008).
 */
export function useRestaurantBranding(establishmentId?: string) {
  const cache = useClientCache();
  const cacheKey = establishmentId ? `branding:diner:${establishmentId}` : '';

  const cachedEntry = cacheKey ? cache.get<RestaurantBranding>(cacheKey)?.data : undefined;
  const [branding, setBranding] = useState<RestaurantBranding>(cachedEntry ?? DEFAULT_NEUTRAL_BRANDING);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(establishmentId) && !cachedEntry);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!establishmentId) {
      setBranding(DEFAULT_NEUTRAL_BRANDING);
      setIsLoading(false);
      return;
    }

    const key = `branding:diner:${establishmentId}`;
    const existing = cache.get<RestaurantBranding>(key)?.data;
    if (existing) {
      setBranding(existing);
      setIsLoading(false);
    } else {
      setIsLoading(true);
    }

    let isMounted = true;
    const unsubscribe = cache.subscribe(key, () => {
      if (!isMounted) return;
      const entry = cache.get<RestaurantBranding>(key);
      if (entry?.data) {
        setBranding(entry.data);
      }
    });

    cache
      .fetchDeduped(key, () => getDinerBranding(establishmentId))
      .then((data) => {
        if (isMounted) {
          setBranding(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (isMounted) {
          if (!cache.get<RestaurantBranding>(key)?.data) {
            setBranding(DEFAULT_NEUTRAL_BRANDING);
          }
          setError(err instanceof Error ? err : new Error(String(err)));
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
      unsubscribe();
    };
  }, [establishmentId, cache]);

  return { branding, isLoading, error };
}
