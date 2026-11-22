/**
 * Spec 025 — Diner Bento Redesign
 * Interface contract for shared Bento layout blocks and accessible overlays.
 *
 * All visual styling MUST be driven exclusively by `--ee-*` tokens from Spec 024
 * so components remain polymorphic across role themes (e.g., staff deep-links on /login).
 */

import type React from 'react';

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
