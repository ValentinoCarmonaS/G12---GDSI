import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChefHat, ClipboardList, Package } from 'lucide-react';
import { isRoleNavItemActive, type RoleNavItem } from '../layout/roleNavigationConfig';

export interface ChefMobileBottomBarProps {
  readonly className?: string;
}

interface ChefMobileNavEntry {
  readonly item: RoleNavItem;
  readonly mobileLabel: string;
  readonly Icon: React.ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
}

const CHEF_MOBILE_DESTINATIONS: readonly ChefMobileNavEntry[] = [
  {
    item: {
      id: 'chef-kds',
      label: 'Comandas KDS',
      to: '/cocina',
      matchStrategy: 'exact',
    },
    mobileLabel: 'Comandas KDS',
    Icon: ClipboardList,
  },
  {
    item: {
      id: 'chef-platos',
      label: 'Gestionar Platos',
      to: '/cocina/platos',
      matchStrategy: 'prefix',
    },
    mobileLabel: 'Platos',
    Icon: ChefHat,
  },
  {
    item: {
      id: 'chef-ingredientes',
      label: 'Ingredientes',
      to: '/cocina/ingredientes',
      matchStrategy: 'prefix',
    },
    mobileLabel: 'Ingredientes',
    Icon: Package,
  },
];

export const ChefMobileBottomBar: React.FC<ChefMobileBottomBarProps> = ({ className = '' }) => {
  const { pathname, hash } = useLocation();

  return (
    <nav
      aria-label="Navegación rápida móvil de cocina"
      className={`md:hidden fixed inset-x-0 bottom-0 z-40 border-t-2 border-ee-border-strong bg-ee-surface pb-[env(safe-area-inset-bottom)] ${className}`.trim()}
    >
      <div className="grid grid-cols-3 divide-x-2 divide-ee-border-strong">
        {CHEF_MOBILE_DESTINATIONS.map(({ item, mobileLabel, Icon }) => {
          const active = isRoleNavItemActive(item, pathname, hash);
          return (
            <Link
              key={item.id}
              to={item.to}
              aria-current={active ? 'page' : undefined}
              className={`min-h-[56px] flex flex-col items-center justify-center gap-1 px-2 py-2 text-xs font-bold tracking-wide transition-colors motion-reduce:transition-none ${
                active
                  ? 'bg-ee-primary text-ee-text-on-primary'
                  : 'bg-ee-surface text-ee-text hover:bg-ee-surface-subtle'
              }`}
            >
              <Icon className="h-5 w-5 shrink-0" aria-hidden={true} />
              <span className="truncate max-w-full">{mobileLabel}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
};
