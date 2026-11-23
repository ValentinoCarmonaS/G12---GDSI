/**
 * Spec 027 — Chef Brutalism Redesign
 * Contract for Brutalism shared UI blocks, token anchors, interaction states,
 * and accessibility thresholds grounded in `.specify/memory/designs/chef.DESIGN.md`.
 */

import type { ReactNode } from 'react';

export type BrutalistCardVariant =
  | 'default'
  | 'elevated'
  | 'subtle'
  | 'alert-warning'
  | 'alert-danger';

export type BrutalistCardPadding = 'none' | 'sm' | 'md' | 'lg';

export interface BrutalistCardProps {
  readonly variant?: BrutalistCardVariant;
  readonly padding?: BrutalistCardPadding;
  readonly interactive?: boolean;
  readonly as?: 'div' | 'section' | 'article' | 'aside';
  readonly className?: string;
  readonly testId?: string;
  readonly children: ReactNode;
}

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

export const CHEF_BRUTALISM_FOUNDATIONS = {
  designContract: '.specify/memory/designs/chef.DESIGN.md',
  themeId: 'chef',
  impeccableMode: 'Operate',
  tokens: {
    surface: '#FFFFFF',
    surfaceElevated: '#F4F4F0',
    surfaceSubtle: '#E7E7E0',
    text: '#111827',
    textSecondary: '#374151',
    borderSubtle: '#737373',
    borderStrong: '#111827',
    primary: '#DD614C',
    onPrimary: '#111827',
    secondary: '#DAA144',
    onSecondary: '#111827',
    dangerSolid: '#B91C1C',
    onDanger: '#FFFFFF',
    focusRing: '#111827',
    borderWidthPx: 2,
    delayedBorderWidthPx: 4,
    shadowElevated: '4px 4px 0px #111827',
    radiusScalePx: [0, 2, 4] as const,
    spacingScalePx: [4, 8, 12, 16, 24, 32] as const,
  },
  typography: {
    primary: 'Darker Grotesque',
    display: 'Darker Grotesque',
    mono: 'JetBrains Mono',
    primaryWeights: [500, 600, 700, 800] as const,
    monoWeights: [400, 500, 600] as const,
  },
  wcag: {
    standard: 'WCAG 2.2 AA',
    minNormalTextContrast: 4.5,
    minLargeAndUiContrast: 3.0,
    minHeaderNavTargetPx: 48,
    minMobileNavAndModalActionPx: 56,
    minKdsPrimaryActionPx: 64,
    minKdsDelayControlPx: 56,
  },
  requiredInteractiveStates: [
    'default',
    'hover',
    'focus-visible',
    'active',
    'disabled',
    'loading',
    'error',
  ] as const,
  reusedFromSpec024Primitives: [
    'Button',
    'StatusBadge',
    'FeedbackState',
    'LoadingSkeleton',
    'EmptyState',
  ] as const,
  reusedFromSpec025WithoutModification: ['useAccessibleOverlay'] as const,
} as const;
