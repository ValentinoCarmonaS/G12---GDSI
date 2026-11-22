import React, { type HTMLAttributes, type ReactNode } from 'react';
import type { SemanticVariant } from '../../theme/types';

export interface StatusBadgeProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  readonly variant?: SemanticVariant;
  readonly icon?: ReactNode;
  readonly prefix?: string;
  readonly children: ReactNode;
}

const VARIANT_CLASSES: Record<SemanticVariant, string> = {
  neutral: 'bg-ee-neutral-bg text-ee-neutral-fg border-ee-neutral-border',
  info: 'bg-ee-info-bg text-ee-info-fg border-ee-info-border',
  success: 'bg-ee-success-bg text-ee-success-fg border-ee-success-border',
  warning: 'bg-ee-warning-bg text-ee-warning-fg border-ee-warning-border',
  danger: 'bg-ee-danger-bg text-ee-danger-fg border-ee-danger-border',
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  variant = 'neutral',
  icon,
  prefix,
  className = '',
  style,
  children,
  ...rest
}) => {
  return (
    <span
      data-testid="status-badge"
      className={[
        'inline-flex items-center gap-1.5 rounded-ee-sm px-2.5 py-1 font-ee-mono text-xs font-semibold leading-tight',
        VARIANT_CLASSES[variant],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{
        borderWidth: 'var(--ee-border-width)',
        ...style,
      }}
      {...rest}
    >
      {icon && (
        <span aria-hidden="true" className="inline-flex shrink-0 items-center">
          {icon}
        </span>
      )}
      {prefix && <span className="font-bold">{prefix}</span>}
      <span>{children}</span>
    </span>
  );
};

export default StatusBadge;
