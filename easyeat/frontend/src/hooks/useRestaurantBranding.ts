import { useState, useEffect } from 'react';
import { getDinerBranding, RestaurantBranding } from '../api/brandingApi';

export const DEFAULT_NEUTRAL_BRANDING: RestaurantBranding = {
  establishmentId: '',
  fantasyName: 'Restaurante EasyEat',
  description: 'Restaurante asociado a EasyEat',
  logoUrl: '/assets/brand/easyeat-logo-neutral.svg',
  primaryColor: '#10B981',
  secondaryColor: '#065F46',
  accentColor: '#F59E0B',
  primaryContrastColor: '#FFFFFF',
  secondaryContrastColor: '#FFFFFF',
  accentContrastColor: '#000000',
  updatedAt: new Date().toISOString(),
  isDefaultFallback: true,
};

/**
 * Hook to retrieve restaurant branding triad and visual identity,
 * applying neutral platform fallback if absent or upon network error (INV-010, CB-011).
 */
export function useRestaurantBranding(establishmentId?: string) {
  const [branding, setBranding] = useState<RestaurantBranding>(DEFAULT_NEUTRAL_BRANDING);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!establishmentId) {
      setBranding(DEFAULT_NEUTRAL_BRANDING);
      setIsLoading(false);
      return;
    }

    let isMounted = true;
    setIsLoading(true);

    getDinerBranding(establishmentId)
      .then((data) => {
        if (isMounted) {
          setBranding(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setBranding(DEFAULT_NEUTRAL_BRANDING);
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
    };
  }, [establishmentId]);

  return { branding, isLoading, error };
}
