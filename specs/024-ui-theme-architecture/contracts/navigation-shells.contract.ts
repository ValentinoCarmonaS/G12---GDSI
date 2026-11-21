/**
 * Contrato de contenedores de navegación minimalistas por rol (Role Layout Shells)
 * y menú desplegable tipo hamburguesa accesible WCAG 2.2 AA.
 *
 * Especificación: specs/024-ui-theme-architecture/spec.md (FR-007 a FR-010, FR-018)
 * Nota: Artefacto de diseño (Phase 1). No es código ejecutable de producción.
 */

import type { ReactNode } from 'react';
import type { EasyEatUserRole, RoleThemeId } from './theme-tokens.contract';

export interface RoleNavItem {
  readonly id: string;
  readonly label: string;
  readonly to: string;
  /**
   * Regla determinista para determinar si la sección está activa (`aria-current="page"`).
   * Ejemplo: `/cocina` usa coincidencia exacta para que `/cocina/platos/nuevo` active `Gestionar Platos`;
   * `/perfil#pedidos` valida `pathname === '/perfil' && hash === '#pedidos'`.
   */
  readonly matchStrategy: 'exact' | 'prefix' | 'hash';
  readonly hash?: string;
}

export interface RoleNavigationConfig {
  readonly role: EasyEatUserRole;
  readonly themeId: RoleThemeId;
  readonly ariaLabel: string;
  readonly items: readonly RoleNavItem[];
  /**
   * Excepción explícita Spec 005 C-08 (FR-009):
   * `true` únicamente para `CHEF`, manteniendo sus 3 accesos de primer nivel
   * directamente visibles en la cabecera de escritorio (`>= 768px`) y en el menú hamburguesa en móvil.
   */
  readonly exposeDirectDesktopNav: boolean;
  readonly showNotificationBell: boolean;
  readonly showTenantBadge: boolean;
}

export interface HamburgerMenuControls {
  readonly isOpen: boolean;
  readonly panelId: string;
  readonly toggle: () => void;
  readonly close: (options?: { readonly restoreFocus?: boolean }) => void;
}

/**
 * Propiedades compatibles con el `WorkspaceLayout` vigente (`easyeat/frontend/src/components/common/WorkspaceLayout.tsx`)
 * para evitar cambios en las ~15 páginas heredadas que lo invocan directamente.
 */
export interface WorkspaceLayoutProps {
  readonly title: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
  /**
   * Contenedor transitorio de aislamiento cromático para pantallas heredadas
   * pendientes de refactorización en specs 025–028 (FR-018).
   * - `'none'`: renderizado normal sobre `--ee-surface`.
   * - `'legacy-light'`: protege contenido heredado claro (ej. CounterWorkspacePage hasta Spec 026).
   * - `'legacy-dark'`: protege contenido heredado oscuro (ej. AdminAnalyticsPage hasta Spec 028).
   */
  readonly legacySurfaceMode?: 'none' | 'legacy-light' | 'legacy-dark';
}

/**
 * Contrato de accesibilidad obligatoria del menú desplegable hamburguesa (FR-010, SC-003, SC-004):
 *
 * 1. Disparador (`<button type="button">`):
 *    - Área táctil mínima: `44×44 px` (`min-h-[44px] min-w-[44px]`).
 *    - Atributos obligatorios: `aria-expanded={isOpen}`, `aria-controls={panelId}`,
 *      `aria-label={isOpen ? 'Cerrar menú de navegación' : 'Abrir menú de navegación'}`.
 *    - Indicador de foco visible con `--ee-focus-ring` (contraste >= 3:1).
 * 2. Panel desplegable (`<nav id={panelId} aria-label={config.ariaLabel}>`):
 *    - Enlaces alcanzables con `Tab` / `Shift+Tab`.
 *    - Ítem activo marcado con `aria-current="page"` y distinción visual no dependiente solo del color.
 *    - Incluye acción `Cerrar sesión` para usuarios autenticados (además del botón accesible en cabecera).
 * 3. Cierre determinista:
 *    - Al seleccionar un enlace o cambiar `pathname`/`hash`.
 *    - Al presionar tecla `Escape`, devolviendo el foco al botón disparador (`restoreFocus: true`).
 *    - Al hacer clic o toque fuera del contenedor del disparador + panel.
 * 4. Estado invitado (`user === null` en rutas públicas como `/catalogo`):
 *    - Oculta el menú de rol autenticado y `Cerrar sesión`.
 *    - Presenta accesos directos claros a `Iniciar sesión` (`/login`) y `Registrarse` (`/register`).
 */
export const ROLE_NAVIGATION_INVARIANT = {
  comensalSectionCount: 4, // Descubrimiento, Catálogo, Mis pedidos, Mi perfil
  mostradorSectionCount: 1, // Despacho en mostrador (+ acceso perfil/sesión)
  chefSectionCount: 3, // Comandas KDS, Gestionar Platos, Ingredientes (C-08)
  adminSectionCount: 7, // Panel, Auditoría, Personal, Inventario, Operaciones, Mi local, Métricas
  maxInteractionsMobileAndDesktop: 2,
  maxInteractionsChefDesktop: 1,
} as const;
