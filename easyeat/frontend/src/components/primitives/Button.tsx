import React, { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'outline' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  readonly variant?: ButtonVariant;
  readonly size?: ButtonSize;
  readonly loading?: boolean;
  readonly loadingText?: string;
  readonly leftIcon?: ReactNode;
  readonly rightIcon?: ReactNode;
  readonly fullWidth?: boolean;
  readonly children: ReactNode;
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:
    'border-ee-primary-border bg-ee-primary text-ee-on-primary hover:bg-ee-primary-hover shadow-ee-elevated',
  secondary:
    'border-ee-secondary-border bg-ee-secondary text-ee-on-secondary hover:bg-ee-secondary-hover shadow-ee-elevated',
  ghost:
    'border-transparent bg-transparent text-ee-text hover:border-ee-border-subtle hover:bg-ee-surface-subtle',
  outline:
    'border-ee-border-strong bg-ee-surface-elevated text-ee-text hover:bg-ee-surface-subtle',
  danger:
    'border-ee-danger-border bg-ee-danger-solid text-ee-on-danger hover:bg-ee-danger-hover shadow-ee-elevated',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'min-h-[32px] min-w-[32px] px-3 py-1.5 text-xs gap-1.5',
  md: 'min-h-[44px] min-w-[44px] px-4 py-2 text-sm gap-2',
  lg: 'min-h-[44px] min-w-[44px] px-5 py-3 text-base gap-2.5',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    loadingText,
    leftIcon,
    rightIcon,
    fullWidth = false,
    disabled = false,
    type = 'button',
    className = '',
    style,
    children,
    ...rest
  },
  ref
) {
  const isDisabled = disabled || loading;

  return (
    <button
      ref={ref}
      type={type}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      className={[
        'inline-flex items-center justify-center rounded-ee-sm font-ee-sans font-semibold',
        'transition-colors motion-reduce:transition-none',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring',
        'disabled:cursor-not-allowed disabled:opacity-60',
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        fullWidth ? 'w-full' : '',
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
      {loading ? (
        <Loader2
          data-testid="button-spinner"
          aria-hidden="true"
          className="h-4 w-4 shrink-0 animate-spin motion-reduce:animate-none"
        />
      ) : (
        leftIcon && (
          <span aria-hidden="true" className="inline-flex shrink-0 items-center">
            {leftIcon}
          </span>
        )
      )}
      <span>{loading && loadingText ? loadingText : children}</span>
      {!loading && rightIcon && (
        <span aria-hidden="true" className="inline-flex shrink-0 items-center">
          {rightIcon}
        </span>
      )}
    </button>
  );
});

export default Button;
