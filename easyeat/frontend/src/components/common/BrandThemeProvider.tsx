import React, { createContext, useContext } from 'react';
import { useRestaurantBranding, DEFAULT_NEUTRAL_BRANDING } from '../../hooks/useRestaurantBranding';
import { RestaurantBranding } from '../../api/brandingApi';

interface BrandThemeContextValue {
  branding: RestaurantBranding;
  isLoading: boolean;
  error: Error | null;
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
  establishmentId,
  children,
}) => {
  const { branding, isLoading, error } = useRestaurantBranding(establishmentId);

  const cssVariables: Record<string, string> = {
    '--brand-primary': branding.primaryColor,
    '--brand-primary-contrast': branding.primaryContrastColor,
    '--brand-secondary': branding.secondaryColor,
    '--brand-secondary-contrast': branding.secondaryContrastColor,
    '--brand-accent': branding.accentColor,
    '--brand-accent-contrast': branding.accentContrastColor,
  };

  return (
    <BrandThemeContext.Provider value={{ branding, isLoading, error }}>
      <div
        data-testid="brand-theme-container"
        style={cssVariables as React.CSSProperties}
        className="w-full"
      >
        {children}
      </div>
    </BrandThemeContext.Provider>
  );
};
