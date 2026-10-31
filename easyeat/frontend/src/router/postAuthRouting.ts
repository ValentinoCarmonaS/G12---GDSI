import { Role } from '../types/auth';

export const ROLE_DEFAULT_ROUTES: Record<Role, string> = {
  ENCARGADO: '/mostrador',
  CHEF: '/cocina',
  ADMINISTRADOR: '/admin',
  COMENSAL: '/descubrimiento',
};

export const ROLE_ALLOWED_PREFIXES: Record<Role, string[]> = {
  ENCARGADO: ['/mostrador', '/counter'],
  CHEF: ['/cocina', '/kds'],
  ADMINISTRADOR: ['/admin'],
  COMENSAL: ['/catalogo', '/catalog', '/descubrimiento', '/discovery', '/perfil', '/profile'],
};

/**
 * Returns the operational landing route assigned to a specific user role.
 * For COMENSAL: if an active tenant is selected in sessionStorage, routes to '/catalogo',
 * otherwise routes to '/descubrimiento' (T023, COM-1.5.1).
 */
export function getRoleDefaultRoute(role?: Role | null): string {
  if (!role || !ROLE_DEFAULT_ROUTES[role]) {
    return '/descubrimiento';
  }

  if (role === 'COMENSAL') {
    try {
      const activeTenant =
        typeof window !== 'undefined' && window.sessionStorage
          ? window.sessionStorage.getItem('easyeat_active_tenant')
          : null;
      return activeTenant ? '/catalogo' : '/descubrimiento';
    } catch {
      return '/descubrimiento';
    }
  }

  return ROLE_DEFAULT_ROUTES[role];
}

/**
 * Determines whether a requested path belongs to the authorized perimeter of a role.
 */
export function isRouteAllowedForRole(role: Role, path: string): boolean {
  const allowedPrefixes = ROLE_ALLOWED_PREFIXES[role] || [];
  return allowedPrefixes.some(
    (prefix) => path === prefix || path.startsWith(`${prefix}/`)
  );
}

/**
 * Resolves the final destination URL following authentication:
 * - If mustChangePassword is true, forces redirection to '/first-login-password-change'.
 * - If target URL is provided and authorized for the user's role, it is preserved.
 * - If target URL is unauthorized, root '/', or '/login', it falls back to the role's default workspace.
 */
export function resolvePostAuthRedirect(
  role?: Role | null,
  targetUrl?: string,
  mustChangePassword?: boolean
): string {
  if (mustChangePassword) {
    return '/first-login-password-change';
  }

  const defaultRoute = getRoleDefaultRoute(role);

  if (!targetUrl || targetUrl === '/' || targetUrl === '/login') {
    return defaultRoute;
  }

  if (role && isRouteAllowedForRole(role, targetUrl)) {
    return targetUrl;
  }

  return defaultRoute;
}
