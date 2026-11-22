import React from 'react';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ThemeScopeProvider } from '../../src/theme/ThemeScopeProvider';
import { useRoleTheme } from '../../src/theme/useRoleTheme';
import { ROLE_THEME_REGISTRY } from '../../src/theme/themeRegistry';
import {
  RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX,
  RESERVED_ROLE_CSS_PREFIX,
} from '../../src/theme/types';
import { BrandThemeProvider, useBrandTheme } from '../../src/components/common/BrandThemeProvider';
import { DEFAULT_NEUTRAL_BRANDING } from '../../src/hooks/useRestaurantBranding';
import { AuthContext } from '../../src/context/AuthContext';
import * as brandingApi from '../../src/api/brandingApi';
import type { RestaurantBranding } from '../../src/api/brandingApi';
import type { AuthenticatedUser, Role } from '../../src/types/auth';
import {
  getContrastRatio,
  getOptimalContrastColor,
} from '../../src/utils/colorContrastUtils';

vi.mock('../../src/api/brandingApi', () => ({
  getDinerBranding: vi.fn(),
  getAdminBranding: vi.fn(),
  updateAdminBranding: vi.fn(),
  uploadRestaurantLogo: vi.fn(),
}));

const CUSTOM_ESTABLISHMENT_BRANDING: RestaurantBranding = {
  establishmentId: 'est-pizzeria-roma',
  fantasyName: 'Pizzería Roma',
  description: 'Pizza a la piedra',
  logoUrl: '/uploads/logos/est-pizzeria-roma/logo.png',
  primaryColor: '#B91C1C',
  secondaryColor: '#7F1D1D',
  accentColor: '#F59E0B',
  primaryContrastColor: '#FFFFFF',
  secondaryContrastColor: '#FFFFFF',
  accentContrastColor: '#000000',
  updatedAt: '2026-11-21T12:00:00Z',
  isDefaultFallback: false,
};

const NEUTRAL_ESTABLISHMENT_BRANDING: RestaurantBranding = {
  ...DEFAULT_NEUTRAL_BRANDING,
  establishmentId: 'est-neutral',
  updatedAt: '2026-11-21T12:00:00Z',
  isDefaultFallback: true,
};

function makeAuthValue(role: Role | null, tenantId: string | null = null) {
  const user: AuthenticatedUser | null = role
    ? {
        id: 'user-1',
        email: `${role.toLowerCase()}@easyeat.test`,
        role,
        tenantId,
        tokenVersion: 1,
        mustChangePassword: false,
      }
    : null;

  return {
    user,
    token: user ? 'mock.jwt.token' : null,
    isAuthenticated: Boolean(user),
    setToken: vi.fn(),
    clearAuth: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
  };
}

function getInlineCssProperties(el: HTMLElement): string[] {
  const props: string[] = [];
  for (let i = 0; i < el.style.length; i += 1) {
    props.push(el.style.item(i));
  }
  return props;
}

let navigateFn: ReturnType<typeof useNavigate>;

const RoleThemeProbe: React.FC<{ label?: string }> = ({ label = 'probe' }) => {
  const { themeId, contract } = useRoleTheme();
  navigateFn = useNavigate();

  return (
    <div data-testid={`role-probe-${label}`}>
      <span data-testid="active-theme-id">{themeId}</span>
      <span data-testid="active-surface-token">{contract.colors.surface}</span>
      <span data-testid="active-design-name">{contract.designName}</span>
    </div>
  );
};

const BrandedCatalogProbe: React.FC = () => {
  const { branding } = useBrandTheme();
  return (
    <div>
      <RoleThemeProbe label="catalog" />
      <span data-testid="brand-fantasy-name">{branding.fantasyName}</span>
    </div>
  );
};

describe('Brand Layer Isolation — --ee-* vs --brand-* (Spec 024 - US3 / FR-006 / SC-006)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.cssText = '';
  });

  it('(1) custom establishment applies only --brand-* on data-brand-layer="establishment" without mutating --ee-* of [data-theme="comensal"]', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce(CUSTOM_ESTABLISHMENT_BRANDING);

    render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <AuthContext.Provider value={makeAuthValue('COMENSAL')}>
          <ThemeScopeProvider>
            <BrandThemeProvider establishmentId="est-pizzeria-roma">
              <BrandedCatalogProbe />
            </BrandThemeProvider>
          </ThemeScopeProvider>
        </AuthContext.Provider>
      </MemoryRouter>
    );

    const themeRoot = screen.getByTestId('theme-scope-root');
    const brandContainer = screen.getByTestId('brand-theme-container');

    await waitFor(() => {
      expect(brandContainer.style.getPropertyValue('--brand-primary')).toBe('#B91C1C');
    });

    // Explicit layer boundary attribute
    expect(brandContainer).toHaveAttribute('data-brand-layer', 'establishment');

    // Only --brand-* inline properties exist on brand-theme-container; zero --ee-* mutations
    const brandInlineProps = getInlineCssProperties(brandContainer);
    expect(brandInlineProps.length).toBeGreaterThan(0);
    for (const prop of brandInlineProps) {
      expect(prop.startsWith(RESERVED_ESTABLISHMENT_BRAND_CSS_PREFIX)).toBe(true);
      expect(prop.startsWith(RESERVED_ROLE_CSS_PREFIX)).toBe(false);
    }

    // Role theme layer remains strictly Comensal (Bento) with zero --brand-* leakage
    expect(document.documentElement).toHaveAttribute('data-theme', 'comensal');
    expect(themeRoot).toHaveAttribute('data-theme', 'comensal');
    expect(screen.getByTestId('active-theme-id')).toHaveTextContent('comensal');
    expect(screen.getByTestId('active-surface-token')).toHaveTextContent(
      ROLE_THEME_REGISTRY.comensal.colors.surface
    );
    expect(getInlineCssProperties(document.documentElement)).toEqual([]);
    expect(getInlineCssProperties(themeRoot)).toEqual([]);
  });

  it('(2) switching to a neutral establishment immediately clears stale custom --brand-* and navigating outside /catalogo unmounts the brand layer preserving Bento', async () => {
    vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce(CUSTOM_ESTABLISHMENT_BRANDING);

    let resolveNextFetch!: (value: RestaurantBranding) => void;
    const pendingNeutralFetch = new Promise<RestaurantBranding>((resolve) => {
      resolveNextFetch = resolve;
    });

    const Harness: React.FC<{ establishmentId?: string }> = ({ establishmentId }) => (
      <Routes>
        <Route
          path="/catalogo"
          element={
            <BrandThemeProvider establishmentId={establishmentId}>
              <BrandedCatalogProbe />
            </BrandThemeProvider>
          }
        />
        <Route path="/descubrimiento" element={<RoleThemeProbe label="discovery" />} />
      </Routes>
    );

    const { rerender } = render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <AuthContext.Provider value={makeAuthValue('COMENSAL')}>
          <ThemeScopeProvider>
            <Harness establishmentId="est-pizzeria-roma" />
          </ThemeScopeProvider>
        </AuthContext.Provider>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(
        screen.getByTestId('brand-theme-container').style.getPropertyValue('--brand-primary')
      ).toBe('#B91C1C');
    });

    // Switch to another establishment while fetch is still pending — stale custom color must NOT remain
    vi.mocked(brandingApi.getDinerBranding).mockReturnValueOnce(pendingNeutralFetch);

    rerender(
      <MemoryRouter initialEntries={['/catalogo']}>
        <AuthContext.Provider value={makeAuthValue('COMENSAL')}>
          <ThemeScopeProvider>
            <Harness establishmentId="est-neutral" />
          </ThemeScopeProvider>
        </AuthContext.Provider>
      </MemoryRouter>
    );

    expect(
      screen.getByTestId('brand-theme-container').style.getPropertyValue('--brand-primary')
    ).toBe(DEFAULT_NEUTRAL_BRANDING.primaryColor);

    await act(async () => {
      resolveNextFetch(NEUTRAL_ESTABLISHMENT_BRANDING);
    });

    await waitFor(() => {
      expect(
        screen.getByTestId('brand-theme-container').style.getPropertyValue('--brand-primary')
      ).toBe(DEFAULT_NEUTRAL_BRANDING.primaryColor);
    });

    // Navigate away from catalog — BrandThemeProvider unmounts, Bento remains intact
    await act(async () => {
      navigateFn('/descubrimiento');
    });

    expect(screen.queryByTestId('brand-theme-container')).not.toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('data-theme', 'comensal');
    expect(screen.getByTestId('theme-scope-root')).toHaveAttribute('data-theme', 'comensal');
    expect(screen.getByTestId('active-design-name')).toHaveTextContent('Bento');
    expect(getInlineCssProperties(document.documentElement)).toEqual([]);
  });

  it('(3) BrandThemeProvider does not alter the role theme of /admin/mi-local, /mostrador, or /cocina', async () => {
    const staffCases: Array<{
      path: string;
      role: Role;
      expectedTheme: 'admin' | 'mostrador' | 'chef';
      expectedSurface: string;
    }> = [
      {
        path: '/admin/mi-local',
        role: 'ADMINISTRADOR',
        expectedTheme: 'admin',
        expectedSurface: ROLE_THEME_REGISTRY.admin.colors.surface,
      },
      {
        path: '/mostrador',
        role: 'ENCARGADO',
        expectedTheme: 'mostrador',
        expectedSurface: ROLE_THEME_REGISTRY.mostrador.colors.surface,
      },
      {
        path: '/cocina',
        role: 'CHEF',
        expectedTheme: 'chef',
        expectedSurface: ROLE_THEME_REGISTRY.chef.colors.surface,
      },
    ];

    for (const testCase of staffCases) {
      vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce(CUSTOM_ESTABLISHMENT_BRANDING);

      const { unmount } = render(
        <MemoryRouter initialEntries={['/catalogo']}>
          <AuthContext.Provider value={makeAuthValue(testCase.role, 'est-pizzeria-roma')}>
            <ThemeScopeProvider>
              <Routes>
                <Route
                  path="/catalogo"
                  element={
                    <BrandThemeProvider establishmentId="est-pizzeria-roma">
                      <BrandedCatalogProbe />
                    </BrandThemeProvider>
                  }
                />
                <Route path={testCase.path} element={<RoleThemeProbe label={testCase.expectedTheme} />} />
              </Routes>
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(
          screen.getByTestId('brand-theme-container').style.getPropertyValue('--brand-primary')
        ).toBe('#B91C1C');
      });

      await act(async () => {
        navigateFn(testCase.path);
      });

      expect(screen.queryByTestId('brand-theme-container')).not.toBeInTheDocument();
      expect(document.documentElement).toHaveAttribute('data-theme', testCase.expectedTheme);
      expect(screen.getByTestId('theme-scope-root')).toHaveAttribute('data-theme', testCase.expectedTheme);
      expect(screen.getByTestId('active-theme-id')).toHaveTextContent(testCase.expectedTheme);
      expect(screen.getByTestId('active-surface-token')).toHaveTextContent(testCase.expectedSurface);
      expect(getInlineCssProperties(document.documentElement)).toEqual([]);

      unmount();
    }
  });

  it('(4) extreme establishment colors maintain protected WCAG contrast via colorContrastUtils and safe fallback when contrast tokens are missing or invalid', async () => {
    const extremeHexes = ['#FFFFFF', '#FFFF00', '#000000', '#050505'];
    for (const bg of extremeHexes) {
      const optimalFg = getOptimalContrastColor(bg);
      expect(getContrastRatio(bg, optimalFg)).toBeGreaterThanOrEqual(4.5);
    }

    const extremeMalformedBranding: RestaurantBranding = {
      establishmentId: 'est-extreme',
      fantasyName: 'Neon Bistro',
      description: 'Colores extremos',
      logoUrl: null,
      primaryColor: '#FFFF00',
      secondaryColor: '#050505',
      accentColor: '#FFFFFF',
      primaryContrastColor: '',
      secondaryContrastColor: 'not-a-hex',
      accentContrastColor: '',
      updatedAt: '2026-11-21T12:00:00Z',
      isDefaultFallback: false,
    };

    vi.mocked(brandingApi.getDinerBranding).mockResolvedValueOnce(extremeMalformedBranding);

    render(
      <MemoryRouter initialEntries={['/catalogo']}>
        <AuthContext.Provider value={makeAuthValue('COMENSAL')}>
          <ThemeScopeProvider>
            <BrandThemeProvider establishmentId="est-extreme">
              <BrandedCatalogProbe />
            </BrandThemeProvider>
          </ThemeScopeProvider>
        </AuthContext.Provider>
      </MemoryRouter>
    );

    const brandContainer = screen.getByTestId('brand-theme-container');

    await waitFor(() => {
      expect(brandContainer.style.getPropertyValue('--brand-primary')).toBe('#FFFF00');
    });

    const primaryFg = brandContainer.style.getPropertyValue('--brand-primary-contrast');
    const secondaryFg = brandContainer.style.getPropertyValue('--brand-secondary-contrast');
    const accentFg = brandContainer.style.getPropertyValue('--brand-accent-contrast');

    expect(primaryFg).toBe('#000000');
    expect(secondaryFg).toBe('#FFFFFF');
    expect(accentFg).toBe('#000000');

    expect(getContrastRatio('#FFFF00', primaryFg)).toBeGreaterThanOrEqual(4.5);
    expect(getContrastRatio('#050505', secondaryFg)).toBeGreaterThanOrEqual(4.5);
    expect(getContrastRatio('#FFFFFF', accentFg)).toBeGreaterThanOrEqual(4.5);

    // Bento role theme remains unaffected by extreme brand colors
    expect(document.documentElement).toHaveAttribute('data-theme', 'comensal');
    expect(screen.getByTestId('active-surface-token')).toHaveTextContent('#FFF5E6');
  });
});
