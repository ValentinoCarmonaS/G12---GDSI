import React from 'react';

export type BentoCardVariant = 'elevated' | 'subtle' | 'interactive' | 'hero';
export type BentoCardPadding = 'none' | 'sm' | 'md' | 'lg';
export type BentoCardRadius = 'md' | 'lg';

export interface BentoCardProps extends React.HTMLAttributes<HTMLElement> {
  /** Surface hierarchy level. Never nest `elevated` inside `elevated`. */
  variant?: BentoCardVariant;
  /** Spacing mapped to the Bento scale (0 / 16 / 24 / 32 px). */
  padding?: BentoCardPadding;
  /** Corner radius mapped to `--ee-radius-md` (14px) or `--ee-radius-lg` (20px). */
  radius?: BentoCardRadius;
  /** Semantic container tag. Defaults to 'div'. */
  as?: 'div' | 'section' | 'article' | 'aside';
  children: React.ReactNode;
}

const VARIANT_CLASSES: Record<BentoCardVariant, string> = {
  elevated: 'bg-ee-surface-elevated border border-ee-border-subtle shadow-ee-elevated',
  subtle: 'bg-ee-surface-subtle border border-ee-border-subtle',
  interactive:
    'bg-ee-surface-elevated border border-ee-border-subtle shadow-ee-elevated transition-[border-color,box-shadow,transform] duration-150 motion-reduce:transition-none hover:border-ee-border-strong focus-within:ring-2 focus-within:ring-ee-focus-ring',
  hero: 'bg-ee-surface-elevated border-2 border-ee-border-strong shadow-ee-elevated',
};

const PADDING_CLASSES: Record<BentoCardPadding, string> = {
  none: 'p-0',
  sm: 'p-4',
  md: 'p-6',
  lg: 'p-8',
};

const RADIUS_CLASSES: Record<BentoCardRadius, string> = {
  md: 'rounded-ee-md',
  lg: 'rounded-ee-lg',
};

export const BentoCard: React.FC<BentoCardProps> = ({
  variant = 'elevated',
  padding = 'md',
  radius = 'md',
  as = 'div',
  className = '',
  children,
  ...rest
}) => {
  const Component = as;
  const classes = [
    'text-ee-text',
    VARIANT_CLASSES[variant],
    PADDING_CLASSES[padding],
    RADIUS_CLASSES[radius],
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <Component className={classes} {...rest}>
      {children}
    </Component>
  );
};

export default BentoCard;
