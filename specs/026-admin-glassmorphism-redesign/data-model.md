# Modelo de datos y entidades de UI (Phase 1): Spec 026

**Feature**: `specs/026-admin-glassmorphism-redesign/spec.md`  
**Date**: 22/11/2026  
**Scope**: Capa exclusivamente frontend (sin cambios en entidades de dominio, base de datos ni contratos Axios de `001`–`023`).

---

## 1. Entidades de interfaz y estado local

### 1.1 `ExecutiveGlassSurface` (`GlassCard`)
Contenedor modular Glassmorphism que encapsula las superficies del Administrador conforme a `.specify/memory/designs/admin.DESIGN.md`.

| Campo / Prop | Tipo | Reglas y restricciones |
|---|---|---|
| `variant` | `'elevated' \| 'subtle' \| 'interactive' \| 'accent'` | `elevated`: cristal esmerilado principal (`rgba(255,255,255,0.82)` + `blur(12px)`, borde `1px` `#CBD5E1`, sombra azulada); `subtle`: `#E8EEF8` para agrupaciones secundarias sin cards-in-cards; `interactive`: añade estado hover/focus-visible `<= 200ms`; `accent`: borde/resalte primario `#1856FF`. |
| `padding` | `'none' \| 'sm' \| 'md' \| 'lg'` | Mapeado a la escala `0 / 12px / 16px / 24px`. |
| `radius` | `'sm' \| 'md' \| 'lg'` | Mapeado a `6px / 12px / 16px` (`--ee-radius-*`). |
| `as` | `'div' \| 'section' \| 'article' \| 'aside'` | Semántica HTML apropiada; evita anidamiento redundante de tarjetas. |

### 1.2 `BimodalExecutiveNavigation` (`AdminSidebar` + `RoleHamburgerMenu`)
Estructura de navegación del Administrador derivada de `ROLE_NAVIGATION_CONFIG.ADMINISTRADOR`.

- **Grupos y secciones (6 ítems exactos)**:
  - `Operación`: `Panel` (`/admin`, `exact`), `Operaciones` (`/admin/operations`, `prefix`), `Inventario` (`/admin/inventory`, `prefix`).
  - `Catálogo y equipo`: `Auditoría` (`/admin/dishes`, `prefix`, muestra insignia `pendingCount`), `Personal` (`/admin/staff`, `prefix`).
  - `Negocio`: `Mi local` (`/admin/mi-local`, `prefix`).
- **Comportamiento responsivo**:
  - Escritorio (`>= 1024px`, `lg:`): `AdminSidebar` fija y siempre expandida; botón hamburguesa oculto (`lg:hidden`).
  - Móvil y tableta (`< 1024px`): `AdminSidebar` oculta; `RoleHamburgerMenu` visible con los mismos 6 ítems agrupados y el indicador `pendingCount` junto a `Auditoría`.

### 1.3 `AdminPendingDishesState` (`AdminPendingDishesContext`)
Estado compartido ultraligero para la insignia de platos pendientes en navegación (`FR-006`).

| Campo | Tipo | Semántica |
|---|---|---|
| `pendingCount` | `number \| null` | `null` durante carga inicial o error (impide mostrar un cero falso); `>= 0` cuando está resuelto. La insignia se renderiza solo cuando `pendingCount !== null && pendingCount > 0`. |
| `setPendingCount` | `(count: number) => void` | Actualización directa en memoria desde `AdminDishReviewPage` usando `totalElements` ya obtenido (cero requests extra). |
| `decrementPendingCount` | `() => void` | Decremento optimista/inmediato (`Math.max(0, prev - 1)`) tras aprobar, rechazar o cancelar. |
| `refreshPendingCount` | `() => Promise<void>` | Recarga explícita bajo demanda (sin polling periódico). |

### 1.4 `ConsolidatedExecutiveDashboard` (`/admin`)
Composición de estado en `AdminWorkspacePage`:
- **Validación de sesión**: conserva la verificación de vigencia de sesión al montar.
- **Salud Financiera** (`FinancialHealthSummary` + `FinancialHealthChart`): período independiente (`DAY | WEEK | MONTH`), KPIs en `JetBrains Mono` tabular, modo visualización (`chart | table`), paleta `dataViz` de `useRoleTheme()`.
- **Ranking de Platos Más Vendidos** (`DishRankingTable` en tarjetas Glassmorphism): período independiente (`GLOBAL | DAY | WEEK | MONTH`), estados independientes de carga/vacío/error con reintento.

### 1.5 `AdministrativeRecordCard`
Patrón unificado de tarjeta Glassmorphism aplicado a listas administrativas:
- **Ranking de platos**: posición (`#1`, `#2`...), nombre, unidades vendidas en `JetBrains Mono`.
- **Personal**: nombre, correo, insignia de rol, `StatusBadge` de estado (`Activo`, `Pendiente de primer login`, `Suspendido`), acciones táctiles `>= 44×44 px`.
- **Inventario**: nombre, categoría, stock actual vs. umbral mínimo y costo unitario en `JetBrains Mono`, alerta redundante (ícono + texto + tríada `warning`/`danger`) para stock crítico, acciones táctiles `>= 44×44 px`.
- **Bandeja de revisión y Food Cost**: estado del plato, nombre, resumen de costo/margen en `JetBrains Mono`, alertas de insumos sin precio.
- **Horarios semanales**: tarjeta por día con controles de apertura, cierre y cerrado.

---

## 2. Trazabilidad Requisitos → Componentes y Tests

| Requisito | Componentes / Archivos principales | Cobertura de verificación |
|---|---|---|
| `FR-001`, `FR-002`, `FR-003`, `FR-004` | `GlassCard.tsx`, primitivos `src/components/primitives/*`, `admin.DESIGN.md` | `tests/admin/glassmorphismBlocksAndNav.test.tsx`, auditoría Impeccable `/critique` |
| `FR-005`, `FR-006` | `RoleLayoutShell.tsx`, `AdminSidebar.tsx`, `RoleHamburgerMenu.tsx`, `roleNavigationConfig.ts`, `AdminPendingDishesContext.tsx` | `tests/admin/glassmorphismBlocksAndNav.test.tsx` |
| `FR-007`, `FR-008` | `AdminWorkspacePage.tsx`, `AppRoutes.tsx` (`/admin/analytics` → `/admin`), `FinancialHealthChart.tsx`, `FinancialHealthSummary.tsx`, `DishRankingTable.tsx` | `tests/admin/executiveDashboardGlass.test.tsx` |
| `FR-009`, `FR-010` | `AdminDishReviewPage.tsx`, `src/components/dishreview/*`, `AdminDishCostAuditPage` (si aplica ruta `:id/cost-audit`) | `tests/admin/dishReviewAndCostAuditGlass.test.tsx` |
| `FR-011`, `FR-012` | `src/components/staff/*`, `src/components/inventory/*`, `AccessibleModal` (reutilizado de `diner/`) | `tests/admin/staffAndInventoryGlass.test.tsx` |
| `FR-013`, `FR-014`, `FR-015` | `AdminOperationsPage.tsx`, `src/components/operations/*`, `AdminBrandingPage.tsx`, `src/components/admin/ColorPickerInput.tsx`, `LogoUploader.tsx`, `FirstLoginPasswordPage.tsx`, `AuthCardShell` (reutilizado de `diner/`) | `tests/admin/operationsBrandingFirstLoginGlass.test.tsx` |
| `FR-016`, `FR-017` | Suite completa `001`–`026`, actualización acotada de aserciones de navegación/hub | `npm test`, `tsc --noEmit`, `vite build`, navegador `360/768/1440 px` |
