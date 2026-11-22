import React from 'react';
import { render, screen, act } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { resolveThemeForRoute } from '../../src/theme/routeThemeResolver';
import { ThemeScopeProvider } from '../../src/theme/ThemeScopeProvider';
import { useDataVizTokens, useRoleTheme } from '../../src/theme/useRoleTheme';
import { ROLE_THEME_REGISTRY } from '../../src/theme/themeRegistry';
import { AuthContext } from '../../src/context/AuthContext';
import App from '../../src/App';
import type { AuthenticatedUser, Role } from '../../src/types/auth';
import type { RoleThemeId } from '../../src/theme/types';

function makeAuthValue(role: Role | null) {
  const user: AuthenticatedUser | null = role
    ? {
        id: 'user-1',
        email: `${role.toLowerCase()}@easyeat.test`,
        role,
        tenantId: role === 'COMENSAL' ? null : 'tenant-1',
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

let navigateFn: ReturnType<typeof useNavigate>;

const ThemeProbe: React.FC = () => {
  const { themeId, contract, dataViz, resolutionReason } = useRoleTheme();
  const hookDataViz = useDataVizTokens();
  navigateFn = useNavigate();

  return (
    <div>
      <span data-testid="probe-theme-id">{themeId}</span>
      <span data-testid="probe-design-name">{contract.designName}</span>
      <span data-testid="probe-reason">{resolutionReason}</span>
      <span data-testid="probe-dataviz-s1">{dataViz.series[0]}</span>
      <span data-testid="probe-hook-dataviz-match">
        {String(hookDataViz === dataViz && dataViz === ROLE_THEME_REGISTRY[themeId].dataViz)}
      </span>
    </div>
  );
};

describe('Deterministic Route Theme Resolution & ThemeScopeProvider (Spec 024 - US1)', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme');
    sessionStorage.clear();
  });

  describe('resolveThemeForRoute pure function — 100% AppRoutes coverage (FR-004)', () => {
    it('resolves all Mostrador routes and aliases to mostrador', () => {
      const paths = ['/mostrador', '/mostrador/', '/mostrador/despacho', '/counter', '/counter/orders'];
      for (const pathname of paths) {
        const result = resolveThemeForRoute({ pathname });
        expect(result.themeId, pathname).toBe('mostrador');
        expect(result.reason, pathname).toBe('route-mostrador');
      }
    });

    it('resolves all Chef / Cocina routes and aliases to chef', () => {
      const paths = [
        '/cocina',
        '/cocina/',
        '/cocina/ingredientes',
        '/cocina/platos',
        '/cocina/platos/nuevo',
        '/cocina/platos/dish-42',
        '/kds',
        '/kds/ingredients',
      ];
      for (const pathname of paths) {
        const result = resolveThemeForRoute({ pathname });
        expect(result.themeId, pathname).toBe('chef');
        expect(result.reason, pathname).toBe('route-chef');
      }
    });

    it('resolves all Admin routes to admin', () => {
      const paths = [
        '/admin',
        '/admin/',
        '/admin/staff',
        '/admin/inventory',
        '/admin/dishes',
        '/admin/dishes/dish-99/cost-audit',
        '/admin/operations',
        '/admin/mi-local',
        '/admin/analytics',
      ];
      for (const pathname of paths) {
        const result = resolveThemeForRoute({ pathname });
        expect(result.themeId, pathname).toBe('admin');
        expect(result.reason, pathname).toBe('route-admin');
      }
    });

    it('resolves all public auth/recovery/verification routes without staff deep-link to comensal', () => {
      const paths = [
        '/login',
        '/register',
        '/forgot-password',
        '/reset-password',
        '/auth/reset-password',
        '/verificar-email',
        '/verify-email',
      ];
      for (const pathname of paths) {
        const result = resolveThemeForRoute({ pathname });
        expect(result.themeId, pathname).toBe('comensal');
        expect(result.reason, pathname).toBe('route-public-auth');
      }
    });

    it('resolves all Comensal domain routes and aliases to comensal', () => {
      const paths = [
        '/perfil',
        '/profile',
        '/descubrimiento',
        '/discovery',
        '/checkout',
        '/checkout/payment-return',
        '/checkout/confirmacion/order-1',
        '/pedidos/order-1/seguimiento',
        '/seguimiento/order-1',
        '/catalogo',
        '/catalogo/est-1',
        '/catalog',
        '/catalog/est-1',
      ];
      for (const pathname of paths) {
        const result = resolveThemeForRoute({ pathname });
        expect(result.themeId, pathname).toBe('comensal');
        expect(result.reason, pathname).toBe('route-comensal');
      }
    });

    it('resolves root "/" for authenticated users to their role target theme and unauthenticated to comensal', () => {
      expect(resolveThemeForRoute({ pathname: '/', userRole: 'ENCARGADO' })).toEqual({
        themeId: 'mostrador',
        reason: 'root-role-redirect',
      });
      expect(resolveThemeForRoute({ pathname: '/', userRole: 'CHEF' })).toEqual({
        themeId: 'chef',
        reason: 'root-role-redirect',
      });
      expect(resolveThemeForRoute({ pathname: '/', userRole: 'ADMINISTRADOR' })).toEqual({
        themeId: 'admin',
        reason: 'root-role-redirect',
      });
      expect(resolveThemeForRoute({ pathname: '/', userRole: 'COMENSAL' })).toEqual({
        themeId: 'comensal',
        reason: 'root-role-redirect',
      });
      expect(resolveThemeForRoute({ pathname: '/', userRole: null })).toEqual({
        themeId: 'comensal',
        reason: 'universal-fallback-comensal',
      });
    });

    it('resolves /login with staff deep-links via fromPath and falls back to comensal otherwise', () => {
      const cases: Array<[string, RoleThemeId]> = [
        ['/mostrador', 'mostrador'],
        ['/mostrador/pendientes?tab=1', 'mostrador'],
        ['/counter', 'mostrador'],
        ['/cocina', 'chef'],
        ['/cocina/platos', 'chef'],
        ['/kds/ingredients', 'chef'],
        ['/admin', 'admin'],
        ['/admin/analytics', 'admin'],
      ];
      for (const [fromPath, expectedTheme] of cases) {
        expect(resolveThemeForRoute({ pathname: '/login', fromPath }), fromPath).toEqual({
          themeId: expectedTheme,
          reason: 'login-staff-deeplink',
        });
      }

      expect(resolveThemeForRoute({ pathname: '/login', fromPath: '/perfil' })).toEqual({
        themeId: 'comensal',
        reason: 'route-public-auth',
      });
      expect(resolveThemeForRoute({ pathname: '/login', fromPath: null })).toEqual({
        themeId: 'comensal',
        reason: 'route-public-auth',
      });
    });

    it('resolves /first-login-password-change to authenticated role theme or admin fallback', () => {
      expect(resolveThemeForRoute({ pathname: '/first-login-password-change', userRole: 'ENCARGADO' })).toEqual({
        themeId: 'mostrador',
        reason: 'first-login-user-role',
      });
      expect(resolveThemeForRoute({ pathname: '/first-login-password-change', userRole: 'CHEF' })).toEqual({
        themeId: 'chef',
        reason: 'first-login-user-role',
      });
      expect(
        resolveThemeForRoute({ pathname: '/first-login-password-change', userRole: 'ADMINISTRADOR' })
      ).toEqual({
        themeId: 'admin',
        reason: 'first-login-user-role',
      });
      expect(resolveThemeForRoute({ pathname: '/first-login-password-change', userRole: 'COMENSAL' })).toEqual({
        themeId: 'comensal',
        reason: 'first-login-user-role',
      });
      expect(resolveThemeForRoute({ pathname: '/first-login-password-change', userRole: null })).toEqual({
        themeId: 'admin',
        reason: 'first-login-fallback-admin',
      });
    });

    it('resolves unknown wildcard paths to comensal fallback', () => {
      expect(resolveThemeForRoute({ pathname: '/ruta-inexistente/123' })).toEqual({
        themeId: 'comensal',
        reason: 'universal-fallback-comensal',
      });
    });
  });

  describe('ThemeScopeProvider DOM lifecycle, hooks, and zero-residue transitions (FR-005, FR-019)', () => {
    it('synchronizes data-theme on documentElement and theme-scope-root across mostrador -> login -> chef -> admin -> comensal transitions without residue', () => {
      render(
        <MemoryRouter initialEntries={['/mostrador']}>
          <AuthContext.Provider value={makeAuthValue('ENCARGADO')}>
            <ThemeScopeProvider>
              <ThemeProbe />
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      const assertActiveTheme = (expectedTheme: RoleThemeId) => {
        const root = screen.getByTestId('theme-scope-root');
        expect(document.documentElement.getAttribute('data-theme')).toBe(expectedTheme);
        expect(root.getAttribute('data-theme')).toBe(expectedTheme);
        expect(screen.getByTestId('probe-theme-id').textContent).toBe(expectedTheme);
        expect(screen.getByTestId('probe-design-name').textContent).toBe(
          ROLE_THEME_REGISTRY[expectedTheme].designName
        );
        expect(screen.getByTestId('probe-dataviz-s1').textContent).toBe(
          ROLE_THEME_REGISTRY[expectedTheme].dataViz.series[0]
        );
        expect(screen.getByTestId('probe-hook-dataviz-match').textContent).toBe('true');
      };

      assertActiveTheme('mostrador');

      act(() => {
        navigateFn('/login');
      });
      assertActiveTheme('comensal');

      act(() => {
        navigateFn('/cocina/platos');
      });
      assertActiveTheme('chef');

      act(() => {
        navigateFn('/admin/analytics');
      });
      assertActiveTheme('admin');

      act(() => {
        navigateFn('/catalogo');
      });
      assertActiveTheme('comensal');
    });

    it('reads location.state.from on /login to apply staff deep-link theme', () => {
      render(
        <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/admin/staff' } }]}>
          <AuthContext.Provider value={makeAuthValue(null)}>
            <ThemeScopeProvider>
              <ThemeProbe />
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(document.documentElement.getAttribute('data-theme')).toBe('admin');
      expect(screen.getByTestId('theme-scope-root').getAttribute('data-theme')).toBe('admin');
      expect(screen.getByTestId('probe-reason').textContent).toBe('login-staff-deeplink');
    });

    it('applies authenticated role theme on /first-login-password-change and falls back to admin when unauthenticated', () => {
      const { unmount } = render(
        <MemoryRouter initialEntries={['/first-login-password-change']}>
          <AuthContext.Provider value={makeAuthValue('CHEF')}>
            <ThemeScopeProvider>
              <ThemeProbe />
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(document.documentElement.getAttribute('data-theme')).toBe('chef');
      expect(screen.getByTestId('probe-reason').textContent).toBe('first-login-user-role');
      unmount();

      render(
        <MemoryRouter initialEntries={['/first-login-password-change']}>
          <AuthContext.Provider value={makeAuthValue(null)}>
            <ThemeScopeProvider>
              <ThemeProbe />
            </ThemeScopeProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      );

      expect(document.documentElement.getAttribute('data-theme')).toBe('admin');
      expect(screen.getByTestId('probe-reason').textContent).toBe('first-login-fallback-admin');
    });

    it('integrates ThemeScopeProvider inside App wrapping AppRoutes', () => {
      window.history.pushState({}, '', '/login');
      render(<App />);

      const scopeRoot = screen.getByTestId('theme-scope-root');
      expect(scopeRoot).toBeInTheDocument();
      expect(scopeRoot.getAttribute('data-theme')).toBe('comensal');
      expect(document.documentElement.getAttribute('data-theme')).toBe('comensal');
    });
  });
});
