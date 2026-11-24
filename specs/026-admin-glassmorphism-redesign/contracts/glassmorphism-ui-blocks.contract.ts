/**
 * Spec 026 — Admin Glassmorphism Redesign
 * Contract for Glassmorphism UI blocks and reused accessible overlays.
 * Visual authority: .specify/memory/designs/admin.DESIGN.md
 */

import type React from 'react';

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

/**
 * Reused polymorphic building blocks (NOT duplicated in src/components/admin/):
 * - AccessibleModal, useAccessibleOverlay, AuthCardShell from src/components/diner/
 * - Button, StatusBadge, FeedbackState, LoadingSkeleton, EmptyState from src/components/primitives/
 */
export interface AdminGlassmorphismInvariants {
  readonly canvasHex: '#F4F7FC';
  readonly glassSurfaceRgba: 'rgba(255, 255, 255, 0.82)';
  readonly backdropBlur: 'blur(12px)';
  readonly reducedTransparencyFallbackHex: '#FFFFFF';
  readonly maxTransitionMs: 200;
  readonly minTouchTargetPx: 44;
  readonly fontDisplaySans: 'Plus Jakarta Sans';
  readonly fontTabularMono: 'JetBrains Mono';
}
