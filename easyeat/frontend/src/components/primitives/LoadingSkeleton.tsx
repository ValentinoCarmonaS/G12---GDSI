import React, { type CSSProperties, type HTMLAttributes } from 'react';

export type SkeletonVariant = 'text' | 'block' | 'circle';

export interface LoadingSkeletonProps extends HTMLAttributes<HTMLDivElement> {
  readonly variant?: SkeletonVariant;
  readonly lines?: number;
  readonly width?: string;
  readonly height?: string;
  readonly announce?: boolean;
  readonly label?: string;
}

const DEFAULT_SHAPE_CLASSES: Record<SkeletonVariant, string> = {
  text: 'h-4 w-full rounded-ee-sm',
  block: 'h-24 w-full rounded-ee-md',
  circle: 'h-12 w-12 rounded-full',
};

export const LoadingSkeleton: React.FC<LoadingSkeletonProps> = ({
  variant = 'text',
  lines = 1,
  width,
  height,
  announce = true,
  label = 'Cargando contenido...',
  className = '',
  ...rest
}) => {
  const lineCount = variant === 'text' ? Math.max(1, Math.floor(lines)) : 1;

  const shapeStyle: CSSProperties = {
    ...(width ? { width } : {}),
    ...(height ? { height } : {}),
  };

  const baseShapeClass = [
    'bg-ee-surface-subtle animate-pulse motion-reduce:animate-none',
    DEFAULT_SHAPE_CLASSES[variant],
  ].join(' ');

  return (
    <div
      role={announce ? 'status' : undefined}
      aria-busy={announce ? 'true' : undefined}
      data-testid="loading-skeleton"
      className={['flex flex-col gap-2', className].filter(Boolean).join(' ')}
      {...rest}
    >
      {announce && <span className="sr-only">{label}</span>}
      {Array.from({ length: lineCount }, (_, index) => {
        const isLastMultiLine = variant === 'text' && lineCount > 1 && index === lineCount - 1;
        return (
          <div
            key={index}
            data-testid="skeleton-shape"
            aria-hidden="true"
            className={[baseShapeClass, isLastMultiLine && !width ? 'w-4/5' : '']
              .filter(Boolean)
              .join(' ')}
            style={shapeStyle}
          />
        );
      })}
    </div>
  );
};

export default LoadingSkeleton;
