import React from 'react';

export type GlassCardVariant = 'elevated' | 'subtle' | 'interactive' | 'accent';
export type GlassCardPadding = 'none' | 'sm' | 'md' | 'lg';
export type GlassCardRadius = 'sm' | 'md' | 'lg';

export interface GlassCardProps extends React.HTMLAttributes<HTMLElement> {
  readonly variant?: GlassCardVariant;
  readonly padding?: GlassCardPadding;
  readonly radius?: GlassCardRadius;
  readonly as?: 'div' | 'section' | 'article' | 'aside';
  readonly children: React.ReactNode;
}

const GLASS_BASE =
  'backdrop-blur-[12px] [@media(prefers-reduced-transparency:reduce)]:bg-ee-surface-elevated [@media(prefers-reduced-transparency:reduce)]:backdrop-blur-none';

const VARIANT_CLASSES: Record<GlassCardVariant, string> = {
  elevated: `bg-[rgba(255,255,255,0.82)] border border-ee-border-subtle shadow-ee-elevated ${GLASS_BASE}`,
  subtle: `bg-ee-surface-subtle border border-ee-border-subtle ${GLASS_BASE}`,
  interactive: `bg-[rgba(255,255,255,0.82)] border border-ee-border-subtle shadow-ee-elevated transition-[border-color,box-shadow,transform] duration-150 motion-reduce:transition-none hover:border-ee-border-strong focus-within:ring-2 focus-within:ring-ee-focus-ring ${GLASS_BASE}`,
  accent: `bg-[rgba(255,255,255,0.82)] border border-ee-primary shadow-ee-elevated ${GLASS_BASE}`,
};

const PADDING_CLASSES: Record<GlassCardPadding, string> = {
  none: 'p-0',
  sm: 'p-2',
  md: 'p-4',
  lg: 'p-8',
};

const RADIUS_CLASSES: Record<GlassCardRadius, string> = {
  sm: 'rounded-ee-sm',
  md: 'rounded-ee-md',
  lg: 'rounded-ee-lg',
};

export const GlassCard: React.FC<GlassCardProps> = ({
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

export default GlassCard;
