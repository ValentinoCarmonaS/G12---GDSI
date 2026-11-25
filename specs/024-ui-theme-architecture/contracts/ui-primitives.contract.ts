/**
 * Contrato TypeScript y reglas de accesibilidad WCAG 2.2 AA para los cinco primitivos base
 * polimórficos gobernados exclusivamente por el tema activo (`--ee-*`).
 *
 * Especificación: specs/024-ui-theme-architecture/spec.md (FR-011 a FR-017, SC-004, SC-005)
 * Nota: Artefacto de diseño (Phase 1). No es código ejecutable de producción.
 *
 * REGLA POLIMÓRFICA INVARIANTE (FR-016):
 * Ninguno de estos cinco primitivos puede importar `RoleThemeId`, `EasyEatUserRole`,
 * ni ramificar clases o estilos mediante condicionales por nombre de rol o tema.
 * Toda variación estética proviene exclusivamente de las variables CSS `--ee-*` del tema activo.
 */

import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react';
import type { SemanticVariant } from './theme-tokens.contract';

/* -------------------------------------------------------------------------- */
/* 1. Button (FR-011)                                                         */
/* -------------------------------------------------------------------------- */

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'outline' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  readonly variant?: ButtonVariant;
  /**
   * Tamaños con garantía de objetivo táctil WCAG 2.2 AA:
   * - `'md'` (defecto) y `'lg'`: `min-h-[44px] min-w-[44px]`.
   * - `'sm'` (compacto): nunca inferior a `24×24 px` (`min-h-[32px] min-w-[32px]`).
   */
  readonly size?: ButtonSize;
  /**
   * Cuando es `true`:
   * - Establece `disabled` y `aria-busy="true"` para impedir envíos duplicados.
   * - Muestra indicador de actividad respetando `prefers-reduced-motion` (`motion-reduce:animate-none`).
   * - Anuncia `loadingText` si se provee.
   */
  readonly loading?: boolean;
  readonly loadingText?: string;
  readonly leftIcon?: ReactNode;
  readonly rightIcon?: ReactNode;
  readonly fullWidth?: boolean;
  readonly children: ReactNode;
}

/* -------------------------------------------------------------------------- */
/* 2. StatusBadge (FR-012)                                                    */
/* -------------------------------------------------------------------------- */

export interface StatusBadgeProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  readonly variant?: SemanticVariant;
  /** Ícono decorativo opcional (`aria-hidden="true"`) o prefijo para redundancia no cromática. */
  readonly icon?: ReactNode;
  readonly prefix?: string;
  /** Etiqueta textual obligatoria; nunca depender exclusivamente del color. */
  readonly children: ReactNode;
}

/* -------------------------------------------------------------------------- */
/* 3. FeedbackState (FR-013)                                                  */
/* -------------------------------------------------------------------------- */

export type FeedbackVariant = Exclude<SemanticVariant, 'neutral'>;

export interface FeedbackStateProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  /**
   * Semántica obligatoria de región viva:
   * - `'danger'`: `role="alert"` y `aria-live="assertive"`.
   * - `'info' | 'success' | 'warning'`: `role="status"` y `aria-live="polite"`.
   */
  readonly variant: FeedbackVariant;
  readonly title?: string;
  readonly message: string;
  readonly icon?: ReactNode;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
  readonly onDismiss?: () => void;
  readonly dismissLabel?: string;
}

/* -------------------------------------------------------------------------- */
/* 4. LoadingSkeleton (FR-014)                                                */
/* -------------------------------------------------------------------------- */

export type SkeletonVariant = 'text' | 'block' | 'circle';

export interface LoadingSkeletonProps extends HTMLAttributes<HTMLDivElement> {
  readonly variant?: SkeletonVariant;
  /** Cantidad de líneas cuando `variant === 'text'` (defecto: 1). */
  readonly lines?: number;
  readonly width?: string;
  readonly height?: string;
  /**
   * Si es `true` (defecto), envuelve las figuras decorativas (`aria-hidden="true"`)
   * en un contenedor con `role="status"` y texto `sr-only` (`label`).
   */
  readonly announce?: boolean;
  /** Texto anunciado a lectores de pantalla (defecto: `'Cargando contenido...'`). */
  readonly label?: string;
}

/* -------------------------------------------------------------------------- */
/* 5. EmptyState (FR-015)                                                     */
/* -------------------------------------------------------------------------- */

export interface EmptyStateProps extends HTMLAttributes<HTMLDivElement> {
  readonly icon?: ReactNode;
  readonly title: string;
  readonly description: string;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
  readonly actionVariant?: Extract<ButtonVariant, 'primary' | 'secondary' | 'outline'>;
}
