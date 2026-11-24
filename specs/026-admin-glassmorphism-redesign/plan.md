# Plan de implementación: Rediseño ejecutivo Glassmorphism del espacio del Administrador

**Branch**: `feat/026-admin-glassmorphism-redesign` | **Date**: 22/11/2026 | **Spec**: [specs/026-admin-glassmorphism-redesign/spec.md](spec.md)

**Input**: Especificación funcional de `specs/026-admin-glassmorphism-redesign/spec.md`, arquitectura fundacional de `specs/024-ui-theme-architecture/spec.md`, referencia de consistencia `specs/025-diner-bento-redesign/plan.md`, constitución vigente `.specify/memory/constitution.md` (v2.1.1), contrato visual `.specify/memory/designs/admin.DESIGN.md` y verdad de producto en `PRODUCT.md`.

---

## Summary

La especificación `026` rediseña integralmente las **7 pantallas activas** del rol Administrador (`/admin`, `/admin/dishes`, `/admin/dishes/:id/cost-audit`, `/admin/staff`, `/admin/inventory`, `/admin/operations`, `/admin/mi-local`), la redirección transparente `/admin/analytics` → `/admin` y la vista polimórfica de primer ingreso (`/first-login-password-change`) bajo la estética ejecutiva **Glassmorphism** definida en `.specify/memory/designs/admin.DESIGN.md` y en modo **Operate** (Impeccable), como entrega exclusiva de frontend (`single-tier frontend-only`, autorizada expresamente bajo Principio X), sin modificar backend, contratos Axios ni lógica de negocio de `001`–`023`.

El plan se organiza en seis módulos técnicos trazables:

1. **Módulo 0 — Fundaciones Glassmorphism y estado ultraligero de pendientes (`FR-001`–`FR-004`, `FR-006`)**: creación de `GlassCard` y `AdminSidebar` en `src/components/admin/` y de `AdminPendingDishesProvider` (1 consulta inicial mínima, cero polling, sincronización en memoria desde `/admin/dishes`). Reutilización estricta de `AccessibleModal`, `useAccessibleOverlay` y `AuthCardShell` desde `src/components/diner/` (sin tocar archivos de Spec `025`) y de los 5 primitivos base de `024`.
2. **Módulo 1 — US1: Navegación ejecutiva bimodal del Administrador (`FR-005`, `FR-006`)**: extensión controlada de `roleNavigationConfig.ts` (6 secciones agrupadas en `Operación`, `Catálogo y equipo` y `Negocio`, retirando `Métricas`) y `RoleLayoutShell.tsx` (`AdminSidebar` fija y expandida en escritorio `>= 1024px` ocultando el botón hamburguesa; `RoleHamburgerMenu` en móvil/tableta) con insignia accesible de `pendingCount` junto a `Auditoría`.
3. **Módulo 2 — US2: Panel Ejecutivo consolidado en `/admin` y redirección `/admin/analytics` (`FR-007`, `FR-008`, `FR-009`)**: reemplazo del hub placeholder en `AdminWorkspacePage.tsx` por la banda superior de KPIs de Salud Financiera (`JetBrains Mono` tabular) + grilla asimétrica de 2 columnas en escritorio (`FinancialHealthChart` estilizado con `useRoleTheme().dataViz` + modo `"Ver tabla"` a la izquierda ~62 %, y `DishRankingTable` en tarjetas `GlassCard` a la derecha ~38 %), apilado vertical en móvil, eliminación del modo oscuro heredado y redirección `/admin/analytics` → `/admin`.
4. **Módulo 3 — US3: Bandeja de Revisión de Platos y Auditoría de Costos (`FR-009`, `FR-010`)**: composición split-pane en escritorio y flujo maestro → detalle con `Volver a la bandeja` en móvil para `AdminDishReviewPage.tsx`, tarjetas `GlassCard` en bandeja y desglose de Food Cost, sincronización inmediata de `pendingCount` al aprobar/rechazar/cancelar, modales migrados a `AccessibleModal` y rediseño Glassmorphism de `/admin/dishes/:id/cost-audit`.
5. **Módulo 4 — US4: Gestión de Personal e Inventario en tarjetas y modales Glassmorphism (`FR-009`, `FR-011`, `FR-012`)**: migración de `StaffRosterTable` e `IngredientCatalogTable` a grillas responsivas de tarjetas `GlassCard` (cero scroll horizontal en `360px`), filtro destacado `Solo insumos en stock crítico` con señalización redundante (ícono + texto + tríada semántica), y migración de los 6 modales operativos a `AccessibleModal` con presentación monoespaciada de credenciales temporales e historial de auditoría en tarjetas.
6. **Módulo 5 — US5: Operación del Restaurante, Mi Local y Cambio Obligatorio de Contraseña (`FR-009`, `FR-013`, `FR-014`, `FR-015`)**: horarios semanales en tarjetas `GlassCard`, matriz de cupos preservando `role="row"`, interruptores accesibles de medios de pago con manejo de conflictos 409, `AdminBrandingPage` en 2 columnas con aislamiento estricto de la previsualización de marca (Spec `016`), y `FirstLoginPasswordPage` migrada a `AuthCardShell` polimórfico (`--ee-*`).

---

## Technical Context

**Language/Version**: TypeScript 5.6.3, React 18.3.1, CSS3 (Tailwind CSS 3.4.13 con utilidades semánticas `ee-*` de Spec `024`).  
**Primary Dependencies**: `react-router-dom` 6.27.0, `lucide-react` 0.453.0, `recharts` 3.11 (existente, migrado a `dataViz`), `axios` 1.7.7 (intacto), `@fontsource/plus-jakarta-sans` y `@fontsource/jetbrains-mono` (autoalojados en Spec `024`). Sin nuevas dependencias externas.  
**Storage**: N/A (capa exclusivamente frontend; se preserva el uso existente de `sessionStorage` y contexto de autenticación sin cambios de esquema).  
**Testing**: Vitest 2.1.3, React Testing Library 16.0.1, `@testing-library/user-event` 14.5.2, `@testing-library/jest-dom` 6.5.0, JSDOM 25.0.1, `tsc --noEmit`, `vite build`, auditoría visual Impeccable (`/critique` en modo **Operate**) y verificación en navegador real a `360px`, `768px` y `1440px`.  
**Target Platform**: Navegadores web modernos en móvil (`>= 360px`), tableta (`768px`) y escritorio (`>= 1024px` / `1440px`), servido como SPA estática sobre Nginx.  
**Project Type**: Aplicación web full-stack (`easyeat/backend` + `easyeat/frontend`) con incremento autorizado estrictamente en `easyeat/frontend/`.  
**Performance Goals**: Micro-transiciones `<= 200ms` anuladas bajo `prefers-reduced-motion`, degradación sólida bajo `prefers-reduced-transparency`, cero polling periódico en `AdminPendingDishesProvider`, cero requests extra al backend al decidir platos, cero dependencias nuevas y cero desbordamiento horizontal en `360/768/1440 px`.  
**Constraints**: WCAG 2.2 AA estricto (`>= 4.5:1` texto normal, `>= 3.0:1` texto grande/componentes/foco), objetivos táctiles `>= 44×44 px` en acciones principales, política Zero AI-Slop, modo **Operate** sobrio, cero cambios en backend/Axios/reglas de negocio de `001`–`023`, cero cambios en pantallas de Comensal (`025`), Mostrador o Chef (`027`–`028`).  
**Scale/Scope**: 7 pantallas administrativas + 1 redirección + 1 vista polimórfica de primer ingreso, ~28 componentes administrativos, 2 bloques nuevos en `src/components/admin/` (`GlassCard`, `AdminSidebar`), 1 contexto ultraligero (`AdminPendingDishesContext`), reutilización de 3 bloques de `src/components/diner/` y 5 primitivos de `024`.

---

## Constitution Check

*GATE: Evaluado antes de Phase 0 y reevaluado tras completar Phase 1.*

### Evaluación Pre-Diseño (antes de Phase 0)

| Principio constitucional | Estado | Evidencia / Justificación |
|---|---|---|
| **I. Specification as Source of Truth** | PASA | Toda decisión responde directamente a `US1`–`US5`, `FR-001`–`FR-017` y `SC-001`–`SC-006` de `specs/026-admin-glassmorphism-redesign/spec.md`. |
| **II. Specification Before Implementation** | PASA | Spec `026` clarificada vía `grill-me` y `/speckit-clarify`; este paso produce únicamente artefactos de diseño/planificación sin código fuente ni `tasks.md`. |
| **III. Layered Verification Proportional to Risk** | PASA | Se planifican pruebas unitarias (bloques Glassmorphism, navegación bimodal, `AdminPendingDishesContext`), integración por pantalla (`US1`–`US5`), regresión completa `001`–`026`, build/typecheck y auditoría Impeccable `/critique` en `360/768/1440 px`. |
| **IV. Simple, Cohesive, and Justified Design** | PASA | Se reutilizan `AccessibleModal`, `useAccessibleOverlay` y `AuthCardShell` (Spec `025`) y los 5 primitivos (`024`), creando únicamente `GlassCard`, `AdminSidebar` y `AdminPendingDishesContext`. |
| **V. Bounded Scope** | PASA | Exclusiones estrictas: cero cambios en backend, Axios, hooks de negocio o pantallas de Comensal (`025`), Mostrador o Chef. |
| **VI. Controlled Technical Debt** | PASA | Elimina el contenedor oscuro heredado (`legacy-dark`) en rutas de Administrador, el hub placeholder y estilos ad-hoc, sin añadir deuda técnica ni polling al servidor. |
| **VII. Traceability, Consistency, Documentation** | PASA | Cadena completa `spec.md` → `research.md` → `data-model.md` → `contracts/*` → `quickstart.md` → `plan.md`, con `PRODUCT.md` sincronizado vía `/impeccable init`. |
| **VIII. Evidence Over Assumption & Escalation** | PASA | Se inspeccionaron `RoleLayoutShell.tsx`, `roleNavigationConfig.ts`, `src/components/diner/index.ts` y carpetas de componentes admin; las decisiones abiertas se resolvieron con Valentino vía `grill-me`. |
| **IX. Engineering Over Convenience** | PASA | Sincronización de `pendingCount` en memoria sin carga extra al servidor de recursos limitados, preservación de excepciones semánticas (`Ver tabla` y `role="row"`), y soporte de `prefers-reduced-transparency`. |
| **X. Full-Stack Integrity (Vertical Slices)** | PASA (Excepción autorizada) | Excepción `single-tier frontend-only`, rama única `feat/026-admin-glassmorphism-redesign` con commits atómicos por historia y evolución de navegación/consolidación `/admin` autorizadas expresamente el 22/11/2026 y documentadas en `spec.md`. |
| **XI. Role-Scoped Visual Governance & Non-Regressive UI** | PASA | Cumple estrictamente `.specify/memory/designs/admin.DESIGN.md` (remediado en `024`), política Zero AI-Slop, modo **Operate**, auditoría `/critique` obligatoria y preservación de garantías conductuales `001`–`025` (con actualización acotada y documentada en `FR-017`). |

### Reevaluación Post-Diseño (tras Phase 1)

- **Aislamiento de roles y de marca**: `RoleLayoutShell` activa `AdminSidebar` únicamente para `ADMINISTRADOR` (`useDesktopSidebar: true`), dejando inalterados `COMENSAL`, `ENCARGADO` y `CHEF`; `/admin/mi-local` mantiene los colores editados aislados en la previsualización.
- **Eficiencia operativa**: `AdminPendingDishesProvider` no realiza polling ni peticiones redundantes tras decisiones en `/admin/dishes`.
- **Puertas de calidad**: cero `NEEDS CLARIFICATION`, cero violaciones sin justificar, sin hooks `before_plan`/`after_plan` en `.specify/extensions.yml`.

---

## Project Structure

### Documentation (this feature)

```text
specs/026-admin-glassmorphism-redesign/
├── spec.md                                      # Especificación funcional clarificada
├── plan.md                                      # Este archivo (plan arquitectónico /speckit-plan)
├── research.md                                  # Phase 0: decisiones técnicas D-01 a D-10
├── data-model.md                                # Phase 1: entidades Glassmorphism, navegación y trazabilidad
├── quickstart.md                                # Phase 1: guía de validación ejecutable SC-001 a SC-006
├── contracts/
│   ├── glassmorphism-ui-blocks.contract.ts      # Contrato de GlassCard y bloques reutilizados
│   ├── admin-navigation-bimodal.contract.ts     # Contrato de navegación bimodal y pendingCount
│   └── admin-screens-audit.contract.ts          # Manifiesto de auditoría de pantallas (360/768/1440 px)
└── tasks.md                                     # Phase 2 (NO creado en /speckit-plan; generado en /speckit-tasks)
```

### Source Code (repository root)

```text
PRODUCT.md                                       # Actualizado en /impeccable init (referencia Spec 026)

easyeat/frontend/src/
├── context/
│   └── AdminPendingDishesContext.tsx            # NUEVO: estado ultraligero de pendingCount sin polling
├── routes/
│   └── AppRoutes.tsx                            # Redirección /admin/analytics -> /admin + AdminPendingDishesProvider
├── components/
│   ├── admin/
│   │   ├── GlassCard.tsx                        # NUEVO: superficie Glassmorphism + reduced-transparency
│   │   ├── AdminSidebar.tsx                     # NUEVO: barra lateral ejecutiva fija en escritorio
│   │   ├── ColorPickerInput.tsx                 # US5: migrado a Glassmorphism + hex en JetBrains Mono
│   │   └── LogoUploader.tsx                     # US5: migrado a Glassmorphism
│   ├── layout/
│   │   ├── roleNavigationConfig.ts              # US1: 6 secciones agrupadas + useDesktopSidebar para ADMIN
│   │   ├── RoleLayoutShell.tsx                  # US1: soporte bimodal sidebar PC / hamburguesa móvil
│   │   └── RoleHamburgerMenu.tsx                # US1: insignia pendingCount junto a Auditoría en móvil
│   ├── analytics/                               # US2: FinancialHealthSummary, FinancialHealthChart (dataViz), DishRankingTable (tarjetas)
│   ├── dishreview/                              # US3: 11 componentes de bandeja, Food Cost, calculadora y modales
│   ├── staff/                                   # US4: StaffRosterTable (tarjetas) + 3 modales (AccessibleModal)
│   ├── inventory/                               # US4: IngredientCatalogTable (tarjetas) + 3 modales (AccessibleModal)
│   ├── operations/                              # US5: WeeklyScheduleMatrix (tarjetas), SlotCapacityGrid (row), PaymentMethodToggle
│   └── diner/                                   # INTACTO (Spec 025): reutilizamos AccessibleModal, useAccessibleOverlay, AuthCardShell
└── pages/
    ├── AdminWorkspacePage.tsx                   # US2: Panel Ejecutivo consolidado (reemplaza placeholder)
    ├── AdminDishReviewPage.tsx                  # US3: split-pane PC / maestro-detalle móvil
    ├── DishCostAuditPage.tsx                    # US3: auditoría de Food Cost en tarjetas GlassCard
    ├── StaffManagementPage.tsx                  # US4: nómina de personal en tarjetas GlassCard
    ├── InventoryManagementPage.tsx              # US4: catálogo de ingredientes y stock crítico
    ├── AdminOperationsPage.tsx                  # US5: Operación del restaurante Glassmorphism
    ├── AdminBrandingPage.tsx                    # US5: Mi Local 2 columnas con aislamiento de marca
    └── FirstLoginPasswordPage.tsx               # US5: tarjeta polimórfica vía AuthCardShell (--ee-*)

easyeat/frontend/tests/admin/                    # NUEVO: pruebas trazables de Spec 026
├── glassmorphismBlocksAndNav.test.tsx           # US1: GlassCard, AdminSidebar, navegación bimodal, pendingCount
├── executiveDashboardGlass.test.tsx             # US2: /admin consolidado, dataViz, Ver tabla, redirección /admin/analytics
├── dishReviewAndCostAuditGlass.test.tsx         # US3: split-pane/móvil, sincronización pendingCount, cost-audit
├── staffAndInventoryGlass.test.tsx              # US4: tarjetas personal/inventario, stock crítico, 6 modales
└── operationsBrandingFirstLoginGlass.test.tsx   # US5: operaciones (row/409), Mi Local aislado, first-login polimórfico
```

**Structure Decision**: Se mantiene intacta la organización de `easyeat/frontend/src/`, añadiendo únicamente `GlassCard.tsx`, `AdminSidebar.tsx` en `src/components/admin/` y `AdminPendingDishesContext.tsx` en `src/context/`, reutilizando los bloques polimórficos ya creados en Spec `024` y `025` sin modificar ningún archivo de `easyeat/backend/`, `src/api/` ni `src/components/diner/`.

---

## Architecture & Implementation Blueprint

### Módulo 0 — Fundaciones Glassmorphism y contexto ultraligero (`FR-001`–`FR-004`, `FR-006`)

1. Implementar `GlassCard.tsx` en `src/components/admin/` conforme a `contracts/glassmorphism-ui-blocks.contract.ts`, aplicando cristal esmerilado (`rgba(255,255,255,0.82)` + `blur(12px)`), borde `1px`, sombra azulada suave, micro-transiciones `<= 200ms` y fallback sólido `#FFFFFF` bajo `prefers-reduced-transparency`.
2. Implementar `AdminPendingDishesContext.tsx` en `src/context/` conforme a `contracts/admin-navigation-bimodal.contract.ts` (una consulta mínima inicial al montar `/admin/*`, sin polling, con `setPendingCount` y `decrementPendingCount` para sincronización en memoria).

### Módulo 1 — US1: Navegación ejecutiva bimodal del Administrador (`FR-005`, `FR-006`)

1. Actualizar `roleNavigationConfig.ts` para `ADMINISTRADOR`: reducir a 6 secciones agrupadas (`Operación`, `Catálogo y equipo`, `Negocio`), retirar `admin-metricas` y habilitar `useDesktopSidebar: true`.
2. Implementar `AdminSidebar.tsx` fija y siempre expandida en escritorio (`lg:flex`) con los 3 grupos, estado activo inequívoco, insignia accesible de `pendingCount` junto a `Auditoría` (visible solo si `pendingCount > 0`) y cierre de sesión.
3. Extender `RoleLayoutShell.tsx` y `RoleHamburgerMenu.tsx` para ocultar el botón hamburguesa en `lg:` cuando `useDesktopSidebar` está activo, mostrar `pendingCount` en el menú móvil, endurecer la campana de notificaciones administrativas para WCAG 2.2 AA y garantizar el indicador no navegable de cuenta (sin enlace a `/perfil`), dejando inalterados `COMENSAL`, `ENCARGADO` y `CHEF`.

### Módulo 2 — US2: Panel Ejecutivo consolidado en `/admin` (`FR-007`, `FR-008`, `FR-009`)

1. Configurar en `AppRoutes.tsx` el envoltorio `AdminPendingDishesProvider` para `/admin/*` y la redirección `<Navigate to="/admin" replace />` en `/admin/analytics`.
2. Reemplazar el tablero placeholder de `AdminWorkspacePage.tsx` por el Panel Ejecutivo consolidado (preservando la verificación de vigencia de sesión al montar): banda superior de KPIs de Salud Financiera + selector `Día`/`Semana`/`Mes`; debajo, grilla `lg:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]` con `FinancialHealthChart` a la izquierda y `DishRankingTable` a la derecha, apilados en móvil.
3. Migrar `FinancialHealthChart.tsx` a `useRoleTheme().dataViz` eliminando el modo oscuro heredado y conservando el botón alternable `"Ver tabla"` / `"Ver gráfico"` en tabla semántica accesible; migrar `DishRankingTable.tsx` a tarjetas `GlassCard` con cifras en `JetBrains Mono`.

### Módulo 3 — US3: Bandeja de Revisión de Platos y Auditoría de Costos (`FR-009`, `FR-010`)

1. Rediseñar `AdminDishReviewPage.tsx` con split-pane en escritorio (`lg:grid-cols-[380px_minmax(0,1fr)]`) y flujo maestro → detalle en móvil/tableta con botón `Volver a la bandeja` (`>= 44×44 px`) preservando pestaña y página activas.
2. Migrar `ReviewInboxTabs`, `ReviewInboxList`, `ReviewInboxRow`, `InboxPagination`, `DishAuditPanel`, `FoodCostBreakdown`, `UnpricedIngredientsWarning`, `MarginPriceCalculator` y `DishDecisionActions` a `GlassCard` y primitivos base, y migrar `PublicationConfirmModal` y `RejectionDialog` a `AccessibleModal`.
3. Conectar las acciones de aprobar, rechazar y cancelar en `/admin/dishes` con `setPendingCount` / `decrementPendingCount` para sincronización inmediata en navegación sin requests extra.
4. Rediseñar `/admin/dishes/:id/cost-audit` en tarjetas `GlassCard` con total de Food Cost en `JetBrains Mono` y alertas semánticas WCAG 2.2 AA.

### Módulo 4 — US4: Gestión de Personal e Inventario (`FR-009`, `FR-011`, `FR-012`)

1. Migrar `/admin/staff` (`StaffRosterTable`) a tarjetas `GlassCard` responsivas con barra de filtros Glassmorphism, `StatusBadge`, acciones táctiles `>= 44×44 px` y sus 3 modales (`StaffFormModal`, `StaffStatusModal`, `TemporaryCredentialsModal` con bloque monoespaciado) a `AccessibleModal`.
2. Migrar `/admin/inventory` (`IngredientCatalogTable`) a tarjetas `GlassCard` responsivas con conmutador destacado `Solo insumos en stock crítico`, señalización redundante (ícono + texto + tríada semántica), cifras en `JetBrains Mono` y sus 3 modales (`IngredientFormModal`, `StockAdjustmentModal`, `StockAuditModal` con historial en tarjetas) a `AccessibleModal`.

### Módulo 5 — US5: Operación, Mi Local y Cambio Obligatorio de Contraseña (`FR-009`, `FR-013`–`FR-015`)

1. Migrar `AdminOperationsPage.tsx` y sus componentes: `WeeklyScheduleMatrix` a tarjetas `GlassCard` por día, `SlotCapacityGrid` a superficie Glassmorphism preservando `role="row"`, y `PaymentMethodToggle` con interruptores accesibles y manejo de conflicto 409 con acción de recarga.
2. Migrar `AdminBrandingPage.tsx` (`/admin/mi-local`), `ColorPickerInput.tsx` y `LogoUploader.tsx` a layout Glassmorphism de 2 columnas en escritorio y 1 en móvil, aislando los colores editados únicamente en el bloque de previsualización en vivo.
3. Migrar `FirstLoginPasswordPage.tsx` a `AuthCardShell` + primitivos base (`--ee-*`) con checklist de requisitos de contraseña en tiempo real WCAG 2.2 AA.

---

## Control estricto de exclusiones

- **Prohibido** modificar cualquier archivo bajo `easyeat/backend/` o `easyeat/frontend/src/api/`.
- **Prohibido** modificar archivos de Spec `025` (`src/components/diner/*`, pantallas del Comensal) o pantallas de Mostrador y Chef.
- **Prohibido** agregar polling periódico en `AdminPendingDishesContext` o nuevas dependencias externas.
- **Prohibido** debilitar aserciones conductuales en suites `001`–`025` fuera de la actualización acotada de navegación (7 → 6 ítems) y del antiguo hub placeholder autorizada en `FR-017`.

---

## Complexity Tracking

| Elemento | Por qué es necesario | Alternativa más simple rechazada |
|---|---|---|
| `GlassCard` y `AdminSidebar` en `src/components/admin/` | Encapsulan el acabado Glassmorphism (`blur(12px)`, `prefers-reduced-transparency`) y la barra lateral fija de escritorio sin contaminar otros roles ni duplicar clases. | Duplicar estilos Glassmorphism en 7 páginas y ~28 componentes (alto riesgo de inconsistencia visual y accesibilidad). |
| `AdminPendingDishesContext` sin polling | Permite mostrar `pendingCount` junto a `Auditoría` en cualquier pantalla `/admin/*` y sincronizarlo al instante desde `/admin/dishes` sin recargar ni sobrecargar el servidor. | Polling periódico (consume recursos limitados del servidor) o props drilling en 7 páginas (alto acoplamiento). |
