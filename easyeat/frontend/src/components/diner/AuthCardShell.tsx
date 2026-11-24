import React from 'react';
import { EasyEatBrandMark } from '../common/EasyEatBrandMark';

export interface AuthCardShellProps {
  /** Main page heading rendered inside the centered card header. */
  title: string;
  /** Optional concise explanatory subtitle under the heading. */
  subtitle?: string;
  /** Optional eyebrow label (e.g., "EasyEat" or flow context). Defaults to "EasyEat". */
  brandLabel?: string;
  /** Main form or status body. */
  children: React.ReactNode;
  /** Optional footer navigation links (e.g., register / login switch). */
  footer?: React.ReactNode;
  /** Optional max width modifier for wider forms like registration. */
  maxWidth?: 'sm' | 'md';
}

const MAX_WIDTH_CLASSES: Record<'sm' | 'md', string> = {
  sm: 'max-w-md',
  md: 'max-w-lg',
};

export const AuthCardShell: React.FC<AuthCardShellProps> = ({
  title,
  subtitle,
  brandLabel = 'EasyEat',
  children,
  footer,
  maxWidth = 'sm',
}) => {
  const isEasyEatTitle = title.trim().toLowerCase() === 'easyeat';
  const isDefaultBrandLabel = brandLabel.trim().toLowerCase() === 'easyeat';

  return (
    <div
      data-testid="auth-card-shell"
      className="min-h-screen w-full bg-ee-surface text-ee-text flex flex-col items-center justify-center px-4 py-6 sm:px-6 sm:py-8 lg:py-10"
    >
      <div className={`w-full ${MAX_WIDTH_CLASSES[maxWidth]}`}>
        <div className="bg-ee-surface-elevated border border-ee-border-subtle rounded-ee-lg shadow-ee-elevated p-5 sm:p-7 lg:p-8">
          <header className="mb-5 flex flex-col items-center text-center sm:mb-6">
            <EasyEatBrandMark
              variant="full"
              size="auth"
              decorative={isEasyEatTitle}
              alt="EasyEat"
              className="mb-3 sm:mb-4"
            />
            {isDefaultBrandLabel ? (
              <span className="sr-only">{brandLabel}</span>
            ) : (
              <p className="mb-1 font-ee-mono text-xs font-semibold uppercase tracking-widest text-ee-text-secondary">
                {brandLabel}
              </p>
            )}
            <h1 className="font-ee-display text-2xl font-bold tracking-tight text-ee-text sm:text-3xl">
              {title}
            </h1>
            {subtitle && (
              <p className="mt-1.5 max-w-prose text-sm leading-relaxed text-ee-text-secondary">
                {subtitle}
              </p>
            )}
          </header>

          <div>{children}</div>

          {footer && (
            <footer className="mt-6 pt-5 border-t border-ee-border-subtle text-sm text-ee-text-secondary">
              {footer}
            </footer>
          )}
        </div>
      </div>
    </div>
  );
};

export default AuthCardShell;
