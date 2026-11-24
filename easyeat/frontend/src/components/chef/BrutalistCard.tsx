import React, { type ReactNode } from 'react';

export type BrutalistCardVariant =
  | 'default'
  | 'elevated'
  | 'subtle'
  | 'alert-warning'
  | 'alert-danger';

export type BrutalistCardPadding = 'none' | 'sm' | 'md' | 'lg';

export interface BrutalistCardProps {
  readonly variant?: BrutalistCardVariant;
  readonly padding?: BrutalistCardPadding;
  readonly interactive?: boolean;
  readonly as?: 'div' | 'section' | 'article' | 'aside';
  readonly className?: string;
  readonly testId?: string;
  readonly children: ReactNode;
}

const VARIANT_CLASSES: Record<BrutalistCardVariant, string> = {
  default: 'border-2 border-ee-border-strong bg-ee-surface text-ee-text',
  elevated: 'border-2 border-ee-border-strong bg-ee-surface-elevated text-ee-text shadow-ee-elevated',
  subtle: 'border-2 border-ee-border-strong bg-ee-surface-subtle text-ee-text',
  'alert-warning': 'border-4 border-ee-warning-border bg-ee-warning-bg text-ee-warning-fg shadow-ee-elevated',
  'alert-danger': 'border-2 border-ee-danger-border bg-ee-danger-bg text-ee-danger-fg shadow-ee-elevated',
};

const PADDING_CLASSES: Record<BrutalistCardPadding, string> = {
  none: 'p-0',
  sm: 'p-3',
  md: 'p-4',
  lg: 'p-6',
};

export const BrutalistCard: React.FC<BrutalistCardProps> = ({
  variant = 'default',
  padding = 'md',
  interactive = false,
  as: Component = 'div',
  className = '',
  testId,
  children,
}) => {
  const interactiveClasses = interactive
    ? 'transition-transform duration-100 hover:-translate-x-0.5 hover:-translate-y-0.5 active:translate-x-0.5 active:translate-y-0.5 motion-reduce:transition-none motion-reduce:transform-none'
    : '';

  return (
    <Component
      data-testid={testId}
      className={[
        'rounded-ee-sm font-ee-sans',
        VARIANT_CLASSES[variant],
        PADDING_CLASSES[padding],
        interactiveClasses,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </Component>
  );
};

export default BrutalistCard;
