import { Role } from '../types/auth';

export const ROLE_DEFAULT_ROUTES: Record<Role, string> = {
  ENCARGADO: '/counter',
  CHEF: '/kds',
  ADMINISTRADOR: '/admin',
  COMENSAL: '/catalog',
};

export const ROLE_ALLOWED_PREFIXES: Record<Role, string[]> = {
  ENCARGADO: ['/counter'],
  CHEF: ['/kds'],
  ADMINISTRADOR: ['/admin'],
  COMENSAL: ['/catalog'],
};

/**
 * Returns the operational landing route assigned to a specific user role.
 * Falls back safely to '/catalog' if role is missing or unassigned.
 */
export function getRoleDefaultRoute(role?: Role | null): string {
  if (!role || !ROLE_DEFAULT_ROUTES[role]) {
    return '/catalog';
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
 * - If target URL is provided and authorized for the user's role, it is preserved.
 * - If target URL is unauthorized, root '/', or '/login', it falls back to the role's default workspace.
 */
export function resolvePostAuthRedirect(role?: Role | null, targetUrl?: string): string {
  const defaultRoute = getRoleDefaultRoute(role);

  if (!targetUrl || targetUrl === '/' || targetUrl === '/login') {
    return defaultRoute;
  }

  if (role && isRouteAllowedForRole(role, targetUrl)) {
    return targetUrl;
  }

  return defaultRoute;
}
