import React, { useId, useRef } from 'react';
import { X } from 'lucide-react';
import { useAccessibleOverlay } from './useAccessibleOverlay';

export interface AccessibleModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Accessible title (connected via aria-labelledby) or explicit aria-label. */
  title?: string;
  ariaLabel?: string;
  /** Optional subtitle/eyebrow rendered in the modal header. */
  subtitle?: string;
  /** Max width preset. Defaults to 'md'. */
  size?: 'sm' | 'md' | 'lg';
  /** Allow closing via Escape and backdrop click. Defaults to true. */
  dismissible?: boolean;
  /** Optional custom test id preserved for existing RTL suites. */
  testId?: string;
  backdropTestId?: string;
  closeButtonTestId?: string;
  closeButtonLabel?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

const SIZE_CLASSES: Record<'sm' | 'md' | 'lg', string> = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

export const AccessibleModal: React.FC<AccessibleModalProps> = ({
  isOpen,
  onClose,
  title,
  ariaLabel,
  subtitle,
  size = 'md',
  dismissible = true,
  testId,
  backdropTestId,
  closeButtonTestId,
  closeButtonLabel = 'Cerrar',
  children,
  footer,
}) => {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const titleId = useId();

  const { handleBackdropClick } = useAccessibleOverlay({
    isOpen,
    onClose,
    closeOnEscape: dismissible,
    closeOnBackdrop: dismissible,
    panelRef,
  });

  if (!isOpen) {
    return null;
  }

  return (
    <div
      data-testid={backdropTestId}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 overflow-y-auto transition-opacity duration-150 motion-reduce:transition-none"
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-label={!title ? ariaLabel : undefined}
        tabIndex={-1}
        data-testid={testId}
        className={`w-full ${SIZE_CLASSES[size]} bg-ee-surface-elevated text-ee-text border border-ee-border-subtle rounded-ee-lg shadow-ee-elevated overflow-hidden focus:outline-none`}
      >
        {(title || dismissible) && (
          <div className="flex items-start justify-between gap-4 px-6 py-4 border-b border-ee-border-subtle">
            <div className="min-w-0">
              {subtitle && (
                <p className="font-ee-mono text-xs uppercase tracking-wider text-ee-text-secondary mb-1">
                  {subtitle}
                </p>
              )}
              {title && (
                <h2
                  id={titleId}
                  className="font-ee-display text-lg font-bold text-ee-text leading-snug"
                >
                  {title}
                </h2>
              )}
            </div>
            {dismissible && (
              <button
                type="button"
                onClick={onClose}
                aria-label={closeButtonLabel}
                data-testid={closeButtonTestId}
                className="inline-flex items-center justify-center min-h-[44px] min-w-[44px] -mr-2 -mt-1 rounded-ee-sm text-ee-text-secondary hover:text-ee-text hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring transition-colors duration-150 motion-reduce:transition-none"
              >
                <X className="w-5 h-5" aria-hidden="true" />
              </button>
            )}
          </div>
        )}

        <div className="px-6 py-5 max-h-[75vh] overflow-y-auto">{children}</div>

        {footer && (
          <div className="px-6 py-4 border-t border-ee-border-subtle bg-ee-surface-subtle">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

export default AccessibleModal;
