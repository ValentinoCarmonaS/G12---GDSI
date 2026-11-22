import React, { useId, useRef } from 'react';
import { X } from 'lucide-react';
import { useAccessibleOverlay } from './useAccessibleOverlay';

export interface AccessibleDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  /** Slide-over side. Defaults to 'right' (CartDrawer) or 'left' (mobile filters). */
  side?: 'left' | 'right';
  title: string;
  ariaLabel?: string;
  headerBadge?: React.ReactNode;
  dismissible?: boolean;
  testId?: string;
  backdropTestId?: string;
  closeButtonTestId?: string;
  closeButtonLabel?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export const AccessibleDrawer: React.FC<AccessibleDrawerProps> = ({
  isOpen,
  onClose,
  side = 'right',
  title,
  ariaLabel,
  headerBadge,
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

  const sideAlignment = side === 'left' ? 'justify-start' : 'justify-end';
  const sideBorder = side === 'left' ? 'border-r' : 'border-l';

  return (
    <div
      data-testid={backdropTestId}
      onClick={handleBackdropClick}
      className={`fixed inset-0 z-50 flex ${sideAlignment} bg-black/50 transition-opacity duration-200 motion-reduce:transition-none`}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-label={ariaLabel}
        data-side={side}
        tabIndex={-1}
        data-testid={testId}
        className={`relative flex flex-col w-full max-w-md h-full bg-ee-surface-elevated text-ee-text ${sideBorder} border-ee-border-subtle shadow-ee-elevated focus:outline-none transition-transform duration-200 motion-reduce:transition-none`}
      >
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-ee-border-subtle">
          <div className="flex items-center gap-2 min-w-0">
            <h2
              id={titleId}
              className="font-ee-display text-lg font-bold text-ee-text truncate"
            >
              {title}
            </h2>
            {headerBadge}
          </div>
          {dismissible && (
            <button
              type="button"
              onClick={onClose}
              aria-label={closeButtonLabel}
              data-testid={closeButtonTestId}
              className="inline-flex items-center justify-center min-h-[44px] min-w-[44px] -mr-2 rounded-ee-sm text-ee-text-secondary hover:text-ee-text hover:bg-ee-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring transition-colors duration-150 motion-reduce:transition-none"
            >
              <X className="w-5 h-5" aria-hidden="true" />
            </button>
          )}
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>

        {footer && (
          <div className="px-5 py-4 border-t border-ee-border-subtle bg-ee-surface-subtle">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

export default AccessibleDrawer;
