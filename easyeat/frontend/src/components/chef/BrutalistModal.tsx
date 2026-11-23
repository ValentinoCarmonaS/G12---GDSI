import React, { useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { useAccessibleOverlay } from '../diner/useAccessibleOverlay';

export interface BrutalistModalProps {
  readonly isOpen: boolean;
  readonly onClose: () => void;
  readonly title: string;
  readonly subtitle?: string;
  readonly size?: 'sm' | 'md' | 'lg';
  readonly dismissible?: boolean;
  readonly testId?: string;
  readonly closeButtonLabel?: string;
  readonly footer?: ReactNode;
  readonly children: ReactNode;
}

const SIZE_CLASSES: Record<NonNullable<BrutalistModalProps['size']>, string> = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

export const BrutalistModal: React.FC<BrutalistModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  size = 'md',
  dismissible = true,
  testId = 'brutalist-modal',
  closeButtonLabel = 'Cerrar',
  footer,
  children,
}) => {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const subtitleId = useId();

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
      data-testid={`${testId}-backdrop`}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-50 flex items-center justify-center bg-ee-text/60 p-4"
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={subtitle ? subtitleId : undefined}
        tabIndex={-1}
        data-testid={testId}
        className={[
          'w-full rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface text-ee-text shadow-ee-elevated',
          'flex max-h-[90vh] flex-col font-ee-sans focus:outline-none',
          SIZE_CLASSES[size],
        ].join(' ')}
      >
        <div className="flex items-start justify-between gap-4 border-b-2 border-ee-border-strong bg-ee-surface-elevated px-5 py-4">
          <div className="min-w-0">
            <h2 id={titleId} className="font-ee-display text-xl font-extrabold leading-tight text-ee-text">
              {title}
            </h2>
            {subtitle && (
              <p id={subtitleId} className="mt-1 text-sm font-medium text-ee-text-secondary">
                {subtitle}
              </p>
            )}
          </div>
          {dismissible && (
            <button
              type="button"
              onClick={onClose}
              aria-label={closeButtonLabel}
              className="inline-flex min-h-[48px] min-w-[48px] shrink-0 items-center justify-center rounded-ee-sm border-2 border-ee-border-strong bg-ee-surface text-ee-text transition-colors hover:bg-ee-surface-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring motion-reduce:transition-none"
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          )}
        </div>

        <div className="overflow-y-auto px-5 py-4">{children}</div>

        {footer && (
          <div className="flex flex-wrap items-center justify-end gap-3 border-t-2 border-ee-border-strong bg-ee-surface-elevated px-5 py-4 [&>button]:min-h-[56px]">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};

export default BrutalistModal;
