import type {
  EasyEatUserRole,
  ResolveThemeForRoute,
  ResolvedThemeScope,
  RoleThemeId,
  ThemeResolverInput,
} from './types';

const MOSTRADOR_PREFIXES = ['/mostrador', '/counter'] as const;
const CHEF_PREFIXES = ['/cocina', '/kds'] as const;
const ADMIN_PREFIXES = ['/admin'] as const;

const PUBLIC_AUTH_PREFIXES = [
  '/register',
  '/forgot-password',
  '/reset-password',
  '/auth/reset-password',
  '/verificar-email',
  '/verify-email',
] as const;

const COMENSAL_PREFIXES = [
  '/descubrimiento',
  '/discovery',
  '/catalogo',
  '/catalog',
  '/perfil',
  '/profile',
  '/checkout',
  '/pedidos',
  '/seguimiento',
] as const;

function normalizePathname(rawPath: string): string {
  const withoutQueryOrHash = rawPath.split(/[?#]/)[0] ?? '';
  const trimmed = withoutQueryOrHash.trim().toLowerCase();
  if (!trimmed || trimmed === '/') {
    return '/';
  }
  const withLeadingSlash = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
  return withLeadingSlash.replace(/\/+$/, '') || '/';
}

function matchesAnyPrefix(pathname: string, prefixes: readonly string[]): boolean {
  return prefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

function mapRoleToThemeId(role: EasyEatUserRole): RoleThemeId {
  switch (role) {
    case 'ENCARGADO':
      return 'mostrador';
    case 'CHEF':
      return 'chef';
    case 'ADMINISTRADOR':
      return 'admin';
    case 'COMENSAL':
      return 'comensal';
  }
}

function resolveStaffTargetTheme(rawFromPath: string | null | undefined): RoleThemeId | null {
  if (!rawFromPath) {
    return null;
  }
  const normalizedFrom = normalizePathname(rawFromPath);
  if (matchesAnyPrefix(normalizedFrom, MOSTRADOR_PREFIXES)) {
    return 'mostrador';
  }
  if (matchesAnyPrefix(normalizedFrom, CHEF_PREFIXES)) {
    return 'chef';
  }
  if (matchesAnyPrefix(normalizedFrom, ADMIN_PREFIXES)) {
    return 'admin';
  }
  return null;
}

export const resolveThemeForRoute: ResolveThemeForRoute = (
  input: ThemeResolverInput
): ResolvedThemeScope => {
  const pathname = normalizePathname(input.pathname);
  const userRole = input.userRole ?? null;

  // 1. Mandatory first-login password change inherits authenticated role (fallback: admin)
  if (pathname === '/first-login-password-change') {
    if (userRole) {
      return { themeId: mapRoleToThemeId(userRole), reason: 'first-login-user-role' };
    }
    return { themeId: 'admin', reason: 'first-login-fallback-admin' };
  }

  // 2. Root redirect inherits authenticated user's target role theme
  if (pathname === '/') {
    if (userRole) {
      return { themeId: mapRoleToThemeId(userRole), reason: 'root-role-redirect' };
    }
    return { themeId: 'comensal', reason: 'universal-fallback-comensal' };
  }

  // 3. Login route applies target staff role theme when receiving a staff deep-link
  if (pathname === '/login') {
    const staffTheme = resolveStaffTargetTheme(input.fromPath);
    if (staffTheme) {
      return { themeId: staffTheme, reason: 'login-staff-deeplink' };
    }
    return { themeId: 'comensal', reason: 'route-public-auth' };
  }

  // 4. Role domain routes
  if (matchesAnyPrefix(pathname, MOSTRADOR_PREFIXES)) {
    return { themeId: 'mostrador', reason: 'route-mostrador' };
  }

  if (matchesAnyPrefix(pathname, CHEF_PREFIXES)) {
    return { themeId: 'chef', reason: 'route-chef' };
  }

  if (matchesAnyPrefix(pathname, ADMIN_PREFIXES)) {
    return { themeId: 'admin', reason: 'route-admin' };
  }

  // 5. Public auth & Comensal domain routes
  if (matchesAnyPrefix(pathname, PUBLIC_AUTH_PREFIXES)) {
    return { themeId: 'comensal', reason: 'route-public-auth' };
  }

  if (matchesAnyPrefix(pathname, COMENSAL_PREFIXES)) {
    return { themeId: 'comensal', reason: 'route-comensal' };
  }

  // 6. Universal fallback
  return { themeId: 'comensal', reason: 'universal-fallback-comensal' };
};
