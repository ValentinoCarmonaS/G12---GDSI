import React from 'react';

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
  return (
    <div
      data-testid="auth-card-shell"
      className="min-h-screen w-full bg-ee-surface text-ee-text flex flex-col items-center justify-center px-4 py-8 sm:px-6"
    >
      <div className={`w-full ${MAX_WIDTH_CLASSES[maxWidth]}`}>
        <div className="bg-ee-surface-elevated border border-ee-border-subtle rounded-ee-lg shadow-ee-elevated p-6 sm:p-8">
          <header className="mb-6 space-y-2">
            <p className="font-ee-mono text-xs font-semibold uppercase tracking-widest text-ee-text-secondary">
              {brandLabel}
            </p>
            <h1 className="font-ee-display text-2xl sm:text-3xl font-bold tracking-tight text-ee-text">
              {title}
            </h1>
            {subtitle && (
              <p className="text-sm text-ee-text-secondary leading-relaxed">{subtitle}</p>
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
