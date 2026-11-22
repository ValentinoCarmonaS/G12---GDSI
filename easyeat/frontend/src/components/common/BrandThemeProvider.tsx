import React, { createContext, useContext, useEffect, useMemo, useRef } from 'react';
import { useRestaurantBranding, DEFAULT_NEUTRAL_BRANDING } from '../../hooks/useRestaurantBranding';
import { RestaurantBranding } from '../../api/brandingApi';
import { TenantContext } from '../../context/TenantContext';
import { AuthContext } from '../../context/AuthContext';
import { getOptimalContrastColor, isValidHexColor } from '../../utils/colorContrastUtils';

interface BrandThemeContextValue {
  branding: RestaurantBranding;
  isLoading: boolean;
  error: Error | null;
}

const BRAND_CSS_KEYS = [
  '--brand-primary',
  '--brand-primary-contrast',
  '--brand-secondary',
  '--brand-secondary-contrast',
  '--brand-accent',
  '--brand-accent-contrast',
] as const;

function resolveBrandColor(candidate: string | undefined, fallback: string): string {
  return candidate && isValidHexColor(candidate) ? candidate : fallback;
}

function resolveBrandContrast(candidate: string | undefined, backgroundHex: string): string {
  if (candidate && isValidHexColor(candidate)) {
    return candidate;
  }
  return getOptimalContrastColor(backgroundHex);
}

const BrandThemeContext = createContext<BrandThemeContextValue>({
  branding: DEFAULT_NEUTRAL_BRANDING,
  isLoading: false,
  error: null,
});

export const useBrandTheme = () => useContext(BrandThemeContext);

interface BrandThemeProviderProps {
  establishmentId?: string;
  children: React.ReactNode;
}

export const BrandThemeProvider: React.FC<BrandThemeProviderProps> = ({
  establishmentId: explicitEstablishmentId,
  children,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const tenantContext = useContext(TenantContext);
  const authContext = useContext(AuthContext);
  const user = authContext?.user;

  const resolvedId =
    explicitEstablishmentId ||
    tenantContext?.tenant?.id ||
    user?.tenantId ||
    undefined;

  const { branding, isLoading, error } = useRestaurantBranding(resolvedId);

  const effectiveBranding = useMemo<RestaurantBranding>(() => {
    if (!resolvedId) {
      return DEFAULT_NEUTRAL_BRANDING;
    }
    if (branding.establishmentId !== resolvedId && !branding.isDefaultFallback) {
      return DEFAULT_NEUTRAL_BRANDING;
    }
    return branding;
  }, [resolvedId, branding]);

  const primaryColor = resolveBrandColor(
    effectiveBranding.primaryColor,
    DEFAULT_NEUTRAL_BRANDING.primaryColor
  );
  const secondaryColor = resolveBrandColor(
    effectiveBranding.secondaryColor,
    DEFAULT_NEUTRAL_BRANDING.secondaryColor
  );
  const accentColor = resolveBrandColor(
    effectiveBranding.accentColor,
    DEFAULT_NEUTRAL_BRANDING.accentColor
  );

  const cssVariables: Record<(typeof BRAND_CSS_KEYS)[number], string> = {
    '--brand-primary': primaryColor,
    '--brand-primary-contrast': resolveBrandContrast(
      effectiveBranding.primaryContrastColor,
      primaryColor
    ),
    '--brand-secondary': secondaryColor,
    '--brand-secondary-contrast': resolveBrandContrast(
      effectiveBranding.secondaryContrastColor,
      secondaryColor
    ),
    '--brand-accent': accentColor,
    '--brand-accent-contrast': resolveBrandContrast(
      effectiveBranding.accentContrastColor,
      accentColor
    ),
  };

  useEffect(() => {
    const node = containerRef.current;
    return () => {
      if (!node) return;
      for (const key of BRAND_CSS_KEYS) {
        node.style.removeProperty(key);
      }
    };
  }, []);

  return (
    <BrandThemeContext.Provider value={{ branding: effectiveBranding, isLoading, error }}>
      <div
        ref={containerRef}
        data-testid="brand-theme-container"
        data-brand-layer="establishment"
        style={cssVariables as React.CSSProperties}
        className="w-full"
      >
        {children}
      </div>
    </BrandThemeContext.Provider>
  );
};
