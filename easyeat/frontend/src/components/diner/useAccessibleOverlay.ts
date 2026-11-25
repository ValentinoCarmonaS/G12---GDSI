import { useEffect, useRef } from 'react';
import type React from 'react';

export interface UseAccessibleOverlayOptions {
  isOpen: boolean;
  onClose: () => void;
  /** Whether Escape key closes the overlay. Defaults to true. */
  closeOnEscape?: boolean;
  /** Whether clicking outside/backdrop closes the overlay. Defaults to true. */
  closeOnBackdrop?: boolean;
  /** Ref to the dialog/drawer panel for initial focus management. */
  panelRef: React.RefObject<HTMLElement | null>;
}

let activeScrollLocks = 0;
let previousBodyOverflow = '';

export function useAccessibleOverlay({
  isOpen,
  onClose,
  closeOnEscape = true,
  closeOnBackdrop = true,
  panelRef,
}: UseAccessibleOverlayOptions) {
  const triggerRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    triggerRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;

    if (activeScrollLocks === 0) {
      previousBodyOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
    }
    activeScrollLocks += 1;

    const panel = panelRef.current;
    if (panel && !panel.contains(document.activeElement)) {
      panel.focus({ preventScroll: true });
    }

    return () => {
      activeScrollLocks = Math.max(0, activeScrollLocks - 1);
      if (activeScrollLocks === 0) {
        document.body.style.overflow = previousBodyOverflow;
      }

      const trigger = triggerRef.current;
      if (trigger && typeof trigger.focus === 'function' && document.contains(trigger)) {
        trigger.focus();
      }
      triggerRef.current = null;
    };
  }, [isOpen, panelRef]);

  useEffect(() => {
    if (!isOpen || !closeOnEscape) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, closeOnEscape, onClose]);

  const handleBackdropClick = (event: React.MouseEvent<HTMLElement>) => {
    if (closeOnBackdrop && event.target === event.currentTarget) {
      onClose();
    }
  };

  return { handleBackdropClick };
}
