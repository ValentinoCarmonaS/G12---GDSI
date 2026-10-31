import React from 'react';
import type { DishStatus } from '@/types/dish';

export interface DishStatusBadgeProps {
  status: DishStatus | string;
  className?: string;
}

interface StatusConfig {
  label: string;
  classes: string;
}

const STATUS_CONFIGS: Record<string, StatusConfig> = {
  DRAFT: {
    label: 'Borrador',
    classes: 'bg-zinc-100 text-zinc-800 border-zinc-300 dark:bg-zinc-800 dark:text-zinc-200 dark:border-zinc-700',
  },
  PENDING_PRICE: {
    label: 'Pendiente de Precio',
    classes: 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-950/60 dark:text-amber-300 dark:border-amber-800',
  },
  ACTIVE: {
    label: 'Activo',
    classes: 'bg-emerald-100 text-emerald-800 border-emerald-300 dark:bg-emerald-950/60 dark:text-emerald-300 dark:border-emerald-800',
  },
  INACTIVE: {
    label: 'Inactivo',
    classes: 'bg-zinc-200 text-zinc-600 border-zinc-300 dark:bg-zinc-800 dark:text-zinc-400 dark:border-zinc-700',
  },
  PUBLISHED: {
    label: 'Publicado',
    classes: 'bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-950/60 dark:text-blue-300 dark:border-blue-800',
  },
};

export const DishStatusBadge: React.FC<DishStatusBadgeProps> = ({ status, className = '' }) => {
  const normalizedKey = typeof status === 'string' ? status.toUpperCase() : 'DRAFT';
  const config = STATUS_CONFIGS[normalizedKey] || {
    label: status,
    classes: 'bg-zinc-100 text-zinc-700 border-zinc-300',
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${config.classes} ${className}`}
      data-testid="dish-status-badge"
    >
      {config.label}
    </span>
  );
};

export default DishStatusBadge;
