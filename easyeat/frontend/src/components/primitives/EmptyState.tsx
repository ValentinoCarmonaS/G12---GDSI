import React, { type HTMLAttributes, type ReactNode } from 'react';
import { Button, type ButtonVariant } from './Button';

export interface EmptyStateProps extends HTMLAttributes<HTMLDivElement> {
  readonly icon?: ReactNode;
  readonly title: string;
  readonly description: string;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
  readonly actionVariant?: Extract<ButtonVariant, 'primary' | 'secondary' | 'outline'>;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  actionVariant = 'primary',
  className = '',
  style,
  ...rest
}) => {
  return (
    <div
      data-testid="empty-state"
      className={[
        'flex flex-col items-center justify-center gap-3 rounded-ee-lg border-ee-border-subtle px-6 py-10 text-center font-ee-sans shadow-ee-elevated',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{
        backgroundColor: 'var(--ee-surface-elevated-glass, var(--ee-surface-elevated))',
        backdropFilter: 'var(--ee-backdrop-blur)',
        borderWidth: 'var(--ee-border-width)',
        ...style,
      }}
      {...rest}
    >
      {icon && (
        <div
          aria-hidden="true"
          className="inline-flex h-12 w-12 items-center justify-center rounded-ee-md bg-ee-surface-subtle text-ee-text-secondary"
        >
          {icon}
        </div>
      )}

      <div className="max-w-md space-y-1.5">
        <h3 className="font-ee-display text-lg font-bold tracking-tight text-ee-text">
          {title}
        </h3>
        <p className="text-sm leading-relaxed text-ee-text-secondary">{description}</p>
      </div>

      {actionLabel && onAction && (
        <div className="pt-2">
          <Button variant={actionVariant} size="md" onClick={onAction}>
            {actionLabel}
          </Button>
        </div>
      )}
    </div>
  );
};

export default EmptyState;
