import React, { type HTMLAttributes, type ReactNode } from 'react';
import { X } from 'lucide-react';
import type { SemanticVariant } from '../../theme/types';
import { Button } from './Button';

export type FeedbackVariant = Exclude<SemanticVariant, 'neutral'>;

export interface FeedbackStateProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  readonly variant: FeedbackVariant;
  readonly title?: string;
  readonly message: string;
  readonly icon?: ReactNode;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
  readonly onDismiss?: () => void;
  readonly dismissLabel?: string;
}

const VARIANT_CLASSES: Record<FeedbackVariant, string> = {
  info: 'bg-ee-info-bg text-ee-info-fg border-ee-info-border',
  success: 'bg-ee-success-bg text-ee-success-fg border-ee-success-border',
  warning: 'bg-ee-warning-bg text-ee-warning-fg border-ee-warning-border',
  danger: 'bg-ee-danger-bg text-ee-danger-fg border-ee-danger-border',
};

export const FeedbackState: React.FC<FeedbackStateProps> = ({
  variant,
  title,
  message,
  icon,
  actionLabel,
  onAction,
  onDismiss,
  dismissLabel = 'Cerrar aviso',
  className = '',
  style,
  ...rest
}) => {
  const isDanger = variant === 'danger';

  return (
    <div
      role={isDanger ? 'alert' : 'status'}
      aria-live={isDanger ? 'assertive' : 'polite'}
      data-testid="feedback-state"
      className={[
        'flex flex-col gap-3 rounded-ee-md p-4 font-ee-sans sm:flex-row sm:items-start sm:justify-between',
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
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span aria-hidden="true" className="mt-0.5 inline-flex shrink-0 items-center">
            {icon}
          </span>
        )}
        <div className="min-w-0 space-y-1">
          {title && <p className="text-sm font-bold leading-snug">{title}</p>}
          <p className="text-sm leading-relaxed">{message}</p>
        </div>
      </div>

      {(onAction || onDismiss) && (
        <div className="flex shrink-0 items-center gap-2">
          {onAction && actionLabel && (
            <Button variant="outline" size="sm" onClick={onAction}>
              {actionLabel}
            </Button>
          )}
          {onDismiss && (
            <Button
              variant="ghost"
              size="md"
              onClick={onDismiss}
              aria-label={dismissLabel}
              className="min-h-[44px] min-w-[44px] px-0 text-current hover:bg-ee-surface-subtle"
            >
              <X aria-hidden="true" className="h-4 w-4" />
            </Button>
          )}
        </div>
      )}
    </div>
  );
};

export default FeedbackState;
