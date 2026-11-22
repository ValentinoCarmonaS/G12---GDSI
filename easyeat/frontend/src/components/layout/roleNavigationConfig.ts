import type { EasyEatUserRole, RoleThemeId } from '../../theme/types';

export interface RoleNavItem {
  readonly id: string;
  readonly label: string;
  readonly to: string;
  readonly matchStrategy: 'exact' | 'prefix' | 'hash';
  readonly hash?: string;
}

export interface RoleNavigationConfig {
  readonly role: EasyEatUserRole;
  readonly themeId: RoleThemeId;
  readonly displayRoleLabel: string;
  readonly ariaLabel: string;
  readonly items: readonly RoleNavItem[];
  readonly exposeDirectDesktopNav: boolean;
  readonly showNotificationBell: boolean;
  readonly showTenantBadge: boolean;
  readonly allowProfileLink: boolean;
}

export const ROLE_NAVIGATION_CONFIG: Readonly<Record<EasyEatUserRole, RoleNavigationConfig>> = {
  COMENSAL: {
    role: 'COMENSAL',
    themeId: 'comensal',
    displayRoleLabel: 'Comensal',
    ariaLabel: 'Navegación de comensal',
    exposeDirectDesktopNav: false,
    showNotificationBell: false,
    showTenantBadge: false,
    allowProfileLink: true,
    items: [
      {
        id: 'comensal-descubrimiento',
        label: 'Descubrimiento',
        to: '/descubrimiento',
        matchStrategy: 'prefix',
      },
      {
        id: 'comensal-catalogo',
        label: 'Catálogo',
        to: '/catalogo',
        matchStrategy: 'prefix',
      },
      {
        id: 'comensal-pedidos',
        label: 'Mis pedidos',
        to: '/perfil#pedidos',
        matchStrategy: 'hash',
        hash: '#pedidos',
      },
      {
        id: 'comensal-perfil',
        label: 'Mi perfil',
        to: '/perfil',
        matchStrategy: 'exact',
      },
    ],
  },
  ENCARGADO: {
    role: 'ENCARGADO',
    themeId: 'mostrador',
    displayRoleLabel: 'Encargado del Mostrador',
    ariaLabel: 'Navegación de mostrador',
    exposeDirectDesktopNav: false,
    showNotificationBell: false,
    showTenantBadge: true,
    allowProfileLink: false,
    items: [
      {
        id: 'mostrador-despacho',
        label: 'Despacho en mostrador',
        to: '/mostrador',
        matchStrategy: 'prefix',
      },
    ],
  },
  CHEF: {
    role: 'CHEF',
    themeId: 'chef',
    displayRoleLabel: 'Chef',
    ariaLabel: 'Secciones de cocina',
    exposeDirectDesktopNav: true,
    showNotificationBell: false,
    showTenantBadge: true,
    allowProfileLink: false,
    items: [
      {
        id: 'chef-kds',
        label: 'Comandas KDS',
        to: '/cocina',
        matchStrategy: 'exact',
      },
      {
        id: 'chef-platos',
        label: 'Gestionar Platos',
        to: '/cocina/platos',
        matchStrategy: 'prefix',
      },
      {
        id: 'chef-ingredientes',
        label: 'Ingredientes',
        to: '/cocina/ingredientes',
        matchStrategy: 'prefix',
      },
    ],
  },
  ADMINISTRADOR: {
    role: 'ADMINISTRADOR',
    themeId: 'admin',
    displayRoleLabel: 'Administrador',
    ariaLabel: 'Navegación de administración',
    exposeDirectDesktopNav: false,
    showNotificationBell: true,
    showTenantBadge: true,
    allowProfileLink: false,
    items: [
      {
        id: 'admin-panel',
        label: 'Panel',
        to: '/admin',
        matchStrategy: 'exact',
      },
      {
        id: 'admin-auditoria',
        label: 'Auditoría',
        to: '/admin/dishes',
        matchStrategy: 'prefix',
      },
      {
        id: 'admin-personal',
        label: 'Personal',
        to: '/admin/staff',
        matchStrategy: 'prefix',
      },
      {
        id: 'admin-inventario',
        label: 'Inventario',
        to: '/admin/inventory',
        matchStrategy: 'prefix',
      },
      {
        id: 'admin-operaciones',
        label: 'Operaciones',
        to: '/admin/operations',
        matchStrategy: 'prefix',
      },
      {
        id: 'admin-mi-local',
        label: 'Mi local',
        to: '/admin/mi-local',
        matchStrategy: 'prefix',
      },
      {
        id: 'admin-metricas',
        label: 'Métricas',
        to: '/admin/analytics',
        matchStrategy: 'prefix',
      },
    ],
  },
};

function normalizePath(path: string): string {
  if (!path || path === '/') return '/';
  return path.replace(/\/+$/, '');
}

export function isRoleNavItemActive(
  item: RoleNavItem,
  pathname: string,
  hash = ''
): boolean {
  const currentPath = normalizePath(pathname);
  const [rawTargetPath, targetHashFromTo] = item.to.split('#');
  const targetPath = normalizePath(rawTargetPath);

  if (item.matchStrategy === 'hash') {
    const expectedHash = item.hash ?? (targetHashFromTo ? `#${targetHashFromTo}` : '');
    return currentPath === targetPath && hash === expectedHash;
  }

  if (item.matchStrategy === 'exact') {
    if (currentPath !== targetPath) return false;
    if (targetPath === '/perfil' && hash === '#pedidos') {
      return false;
    }
    return true;
  }

  return currentPath === targetPath || currentPath.startsWith(`${targetPath}/`);
}
