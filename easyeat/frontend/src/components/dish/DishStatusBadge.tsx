import React from 'react';
import { StatusBadge } from '@/components/primitives/StatusBadge';
import type { SemanticVariant } from '@/theme/types';
import type { DishStatus } from '@/types/dish';

export interface DishStatusBadgeProps {
  status: DishStatus | string;
  className?: string;
}

interface StatusConfig {
  label: string;
  variant: SemanticVariant;
}

const STATUS_CONFIGS: Record<string, StatusConfig> = {
  DRAFT: {
    label: 'Borrador',
    variant: 'neutral',
  },
  PENDING_PRICE: {
    label: 'Pendiente de Precio',
    variant: 'warning',
  },
  ACTIVE: {
    label: 'Activo',
    variant: 'success',
  },
  INACTIVE: {
    label: 'Inactivo',
    variant: 'neutral',
  },
  PUBLISHED: {
    label: 'Publicado',
    variant: 'info',
  },
  CANCELLED: {
    label: 'Cancelado',
    variant: 'danger',
  },
};

export const DishStatusBadge: React.FC<DishStatusBadgeProps> = ({ status, className = '' }) => {
  const normalizedKey = typeof status === 'string' ? status.toUpperCase() : 'DRAFT';
  const config = STATUS_CONFIGS[normalizedKey] || {
    label: status,
    variant: 'neutral' as SemanticVariant,
  };

  return (
    <StatusBadge
      variant={config.variant}
      className={className}
      data-testid="dish-status-badge"
    >
      {config.label}
    </StatusBadge>
  );
};

export default DishStatusBadge;
