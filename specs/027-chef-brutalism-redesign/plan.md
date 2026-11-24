# Plan de implementación: Rediseño Brutalism de la experiencia operativa y administrativa del Chef

**Branch**: `feat/027-chef-brutalism-redesign` | **Date**: 22/11/2026 | **Spec**: [specs/027-chef-brutalism-redesign/spec.md](spec.md)

**Input**: Especificación funcional de `specs/027-chef-brutalism-redesign/spec.md`, arquitectura fundacional de `specs/024-ui-theme-architecture/spec.md`, referencia de consistencia `specs/026-admin-glassmorphism-redesign/plan.md`, constitución vigente `.specify/memory/constitution.md` (v2.1.1), contrato visual `.specify/memory/designs/chef.DESIGN.md`, guía Brutalism (`.agents/skills/brutalism/SKILL.md`) y verdad de producto en `PRODUCT.md`.

---

## Summary

La especificación `027` rediseña integralmente las **5 rutas activas** del rol Chef (`/cocina`, `/cocina/platos`, `/cocina/platos/nuevo`, `/cocina/platos/:id` y `/cocina/ingredientes`) y sus modales asociados bajo la estética **Brutalism** clara de alto contraste definida en `.specify/memory/designs/chef.DESIGN.md` y el modo Impeccable **Operate**, optimizada para uso con guantes, humedad o grasa en cocina móvil/tableta y gestión administrativa fluida en computadora. Se entrega como incremento exclusivo de frontend (`single-tier frontend-only`, autorizado expresamente bajo Principio X), sin modificar el servidor, los contratos HTTP Axios ni las reglas de negocio de `001`–`023`.

El plan se organiza en seis módulos técnicos trazables:

1. **Módulo 0 — Fundaciones Brutalism en `src/components/chef/` (`FR-001`–`FR-004`)**: creación de `BrutalistCard`, `BrutalistModal` (compuesto sobre `useAccessibleOverlay` sin tocar `src/components/diner/`) y `ChefMobileBottomBar`, reutilizando los cinco primitivos base de Spec `024` (`Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`) y migrando `DishStatusBadge` y `DishRejectionFeedbackBanner` a tokens `--ee-*`.
2. **Módulo 1 — US1: Navegación táctil de un toque del Chef (`FR-005`, `FR-006`)**: extensión controlada de `roleNavigationConfig.ts` (`useMobileBottomBar: true` exclusivo para `CHEF`), `RoleLayoutShell.tsx` y `WorkspaceLayout.tsx` (soporte `fullWidth` para `/cocina`, cabecera directa de escritorio `>= 48 px` en `>= 768 px`, `ChefMobileBottomBar` fija `>= 56 px` en `< 768 px` con reserva de espacio inferior `pb-24 md:pb-6`), preservando intactos los roles `COMENSAL`, `ENCARGADO` y `ADMINISTRADOR`.
3. **Módulo 2 — US2: Tablero KDS a pantalla completa para cocina caótica (`FR-006`, `FR-007`, `FR-008`)**: migración total de `KitchenWorkspacePage`, `KitchenKanbanBoard`, `KdsQueuedCard`, `KdsInPreparationCard` y `KitchenTicketDelayControl` al lienzo claro Brutalism, retiro del bloque redundante de ingredientes, tira ultracompacta de 4 KPIs arriba, selector rápido móvil (`>= 56 px`), tarjetas de tres bloques duros con tipografía XXL, `+5` (`>= 56×56 px`) físicamente separado de `EMPEZAR` / `LISTO` (`>= 64 px`), alerta perimetral `4 px` `>= 15 min` con platos neutros y banners Brutalism ante fallos de sondeo o despacho.
4. **Módulo 3 — US3: Gestión del catálogo de platos y pausa rápida en `/cocina/platos` (`FR-009`, `FR-010`)**: reemplazo de la tabla y `<select>` por chips táctiles Brutalism (`>= 48 px`) y grilla responsiva de `BrutalistCard`, interruptor individual `ChefDishPauseToggle` (`>= 56 px`) rotulado bajo `Estado Operativo` (sin pausa masiva por EC-016), y reemplazo de `window.confirm` / `window.alert` por `BrutalistModal` (`>= 56 px`) y `FeedbackState` inline.
5. **Módulo 4 — US4: Editor guiado de fichas técnicas con flujo unificado (`FR-011`, `FR-012`)**: migración limpia de `ChefDishEditorPage`, `DishStepper`, `BasicIdentityStep`, `RecipeBOMStep`, `IngredientSelectorModal`, `AllergenStep` y `ReviewSubmitStep` a `UnifiedDishDraftState` con navegación `"Anterior"` / `"Siguiente"` en pasos 1–3, guardado/envío unificado en Paso 4 orquestando secuencialmente `dishApi` existente (`src/utils/chefDishPersistence.ts`) con retención de `persistedDishId` + `navigate(..., { replace: true })` ante fallos parciales, protección de cambios sin guardar (`StatusBadge` + `beforeunload` + `BrutalistModal`), tarjetas Brutalism en BOM y modal selector, y barra de acción fija coordinada sobre la barra inferior móvil.
6. **Módulo 5 — US5: Catálogo de ingredientes de cocina en tarjetas Brutalism (`FR-009`, `FR-013`)**: migración de `ChefIngredientsPage` y `ChefIngredientTable` a grilla responsiva de `BrutalistCard` (1/2/3 columnas), énfasis inmediato `alert-danger` para `Out of Stock` (conservando etiquetas `In Stock` / `Out of Stock`), buscador/filtro táctil Brutalism y aislamiento financiero absoluto (FR-023).

---

## Technical Context

**Language/Version**: TypeScript 5.6.3, React 18.3.1, CSS3 (Tailwind CSS 3.4.13 con utilidades semánticas `ee-*` de Spec `024`).  
**Primary Dependencies**: `react-router-dom` 6.27.0, `lucide-react` 0.453.0, `axios` 1.7.7 (intacto), `@fontsource/darker-grotesque` (pesos 500–800) y `@fontsource/jetbrains-mono` (pesos 400–600) autoalojados en Spec `024`. Cero dependencias nuevas.  
**Storage**: N/A (capa exclusivamente frontend; el estado del asistente unificado vive en memoria de sesión del componente y persiste exclusivamente vía contratos HTTP existentes de `dishApi`).  
**Testing**: Vitest 2.1.3, React Testing Library 16.0.1, `@testing-library/user-event` 14.5.2, `@testing-library/jest-dom` 6.5.0, JSDOM 25.0.1, `tsc --noEmit`, `vite build`, auditoría visual Impeccable (`/critique` en modo **Operate**) y verificación en navegador real a `360 px`, `768 px`, `1024 px` y `1440 px`.  
**Target Platform**: Navegadores web modernos en teléfonos móviles (`>= 360 px`), tabletas de cocina (`768 px` / `1024 px`) y escritorio (`>= 1440 px`), servido como SPA estática sobre Nginx.  
**Project Type**: Aplicación web full-stack (`easyeat/backend` + `easyeat/frontend`) con incremento autorizado estrictamente en `easyeat/frontend/`.  
**Performance Goals**: Respuesta mecánica instantánea sin animaciones lentas (`<= 150 ms`, suprimida bajo `prefers-reduced-motion`), preservación exacta de la cadencia de sondeo KDS (`useKdsQueuePolling` y resumen cada `10 s` sin requests extra), cero dependencias nuevas y cero desbordamiento horizontal en `360 / 768 / 1024 / 1440 px`.  
**Constraints**: WCAG 2.2 AA estricto (`>= 4.5:1` texto normal, `>= 3.0:1` texto grande/bordes/foco `#111827`), objetivos táctiles `>= 48 px` (cabecera/chips/stepper), `>= 56 px` (barra inferior móvil, `+5`, interruptor pausa, botones de modales, pestañas KDS móvil) y `>= 64 px` (`EMPEZAR` / `LISTO`), política Zero AI-Slop, modo **Operate**, aislamiento financiero estricto (FR-023), cero cambios en `easyeat/backend/`, `src/api/`, `src/components/diner/` (Spec `025`) ni pantallas de otros roles.  
**Scale/Scope**: 5 rutas del Chef (4 páginas), ~14 componentes de cocina/platos, 3 bloques nuevos en `src/components/chef/` (`BrutalistCard`, `BrutalistModal`, `ChefMobileBottomBar`), 1 módulo puro de orquestación (`src/utils/chefDishPersistence.ts`), reutilización de `useAccessibleOverlay` (Spec `025`) y los 5 primitivos base de Spec `024`.

---

## Constitution Check

*GATE: Evaluado antes de Phase 0 y reevaluado tras completar Phase 1.*

### Evaluación pre-diseño (antes de Phase 0)

| Principio constitucional | Estado | Evidencia / Justificación |
|---|---|---|
| **I. Specification as Source of Truth** | PASA | Todas las decisiones arquitectónicas responden directamente a `US1`–`US5`, `FR-001`–`FR-015` y `SC-001`–`SC-006` de `specs/027-chef-brutalism-redesign/spec.md`. |
| **II. Specification Before Implementation** | PASA | Spec `027` clarificada vía `grill-me`; esta fase genera únicamente artefactos de planificación/contratos y sincroniza `PRODUCT.md`, sin editar código de producto ni crear `tasks.md`. |
| **III. Layered Verification Proportional to Risk** | PASA | Planifica pruebas unitarias (bloques Brutalism, navegación táctil, orquestación con fallo parcial), pruebas por historia (`tests/chef/`), regresión completa `001`–`027`, typecheck/build y auditoría visual Impeccable `/critique` en `360/768/1024/1440 px`. |
| **IV. Simple, Cohesive, and Justified Design** | PASA | Crea únicamente 3 bloques Brutalism (`BrutalistCard`, `BrutalistModal`, `ChefMobileBottomBar`) + 1 módulo puro `chefDishPersistence.ts`, reutilizando `useAccessibleOverlay` y los 5 primitivos base de `024`, y elimina código dual heredado en el editor de platos. |
| **V. Bounded Scope** | PASA | Exclusión estricta de `easyeat/backend/`, `src/api/`, `src/components/diner/` (`025`), `src/components/admin/` (`026`), pantallas de Comensal/Mostrador/Admin y `/first-login-password-change`. |
| **VI. Controlled Technical Debt** | PASA | Erradica el modo oscuro heredado de KDS/receta/modal, las tablas con scroll horizontal, los diálogos nativos `window.confirm`/`window.alert` y la fricción de guardado obligatorio por paso sin introducir nueva deuda técnica. |
| **VII. Traceability, Consistency, Documentation** | PASA | Cadena íntegra `spec.md` → `research.md` → `data-model.md` → `contracts/*` → `quickstart.md` → `plan.md`, con `PRODUCT.md` actualizado vía `/impeccable init`. |
| **VIII. Evidence Over Assumption & Escalation** | PASA | Relevamiento directo de `RoleLayoutShell.tsx`, `AccessibleModal.tsx`, `KitchenWorkspacePage.tsx`, `ChefDishesPage.tsx`, `ChefDishEditorPage.tsx`, `ChefIngredientsPage.tsx` y sus suites de prueba; las 5 decisiones abiertas de arquitectura se resolvieron con Valentino vía `grill-me`. |
| **IX. Engineering Over Convenience** | PASA | Manejo explícito de fallo parcial en Paso 4 (`persistedDishId` + `replace: true`), protección de cambios sin guardar, separación física `+5`/`LISTO` para cocina caótica y preservación de `data-testid` estructurales. |
| **X. Full-Stack Integrity (Vertical Slices)** | PASA (Excepción autorizada) | Excepción `single-tier frontend-only`, rama única `feat/027-chef-brutalism-redesign` con commits atómicos por historia y las cuatro evoluciones deliberadas de UX autorizadas expresamente el 22/11/2026 en `spec.md`. |
| **XI. Role-Scoped Visual Governance & Non-Regressive UI** | PASA | Cumple estrictamente `.specify/memory/designs/chef.DESIGN.md` (remediado en `024`), política Zero AI-Slop, modo **Operate**, auditoría `/critique` obligatoria y preservación de garantías conductuales `001`–`026` (con actualización acotada y documentada en `FR-015`). |

### Reevaluación post-diseño (tras Phase 1)

- **Aislamiento de roles y contratos de servidor**: `useMobileBottomBar` se habilita exclusivamente para `CHEF` y `fullWidth` exclusivamente en `KitchenWorkspacePage`; `COMENSAL`, `ENCARGADO` y `ADMINISTRADOR` conservan intactos sus contenedores y estilos. Ningún archivo bajo `easyeat/backend/` ni `src/api/` sufre cambios.
- **Trazabilidad de pruebas heredadas**: El registro `AUTHORIZED_LEGACY_TEST_UPDATES` en `contracts/chef-screens-audit.contract.ts` acota explícitamente cada archivo heredado autorizado bajo `FR-015` y Q3/Q4, sin debilitar ninguna aserción conductual ni de dominio.
- **Puertas de calidad**: Cero `NEEDS CLARIFICATION`, cero violaciones sin justificar, sin hooks `before_plan` ni `after_plan` configurados en `.specify/extensions.yml`.

---

## Project Structure

### Documentation (this feature)

```text
specs/027-chef-brutalism-redesign/
├── spec.md                                      # Especificación funcional aprobada
├── plan.md                                      # Este archivo (plan arquitectónico /speckit-plan)
├── research.md                                  # Phase 0: decisiones técnicas D-01 a D-10
├── data-model.md                                # Phase 1: entidades Brutalism, estado unificado y trazabilidad
├── quickstart.md                                # Phase 1: guía de validación ejecutable SC-001 a SC-006
├── contracts/
│   ├── brutalism-ui-blocks.contract.ts          # Contrato de BrutalistCard, BrutalistModal y tokens chef.DESIGN.md
│   ├── chef-navigation-and-kds.contract.ts      # Contrato de navegación de un toque y ergonomía KDS
│   ├── chef-unified-dish-editor.contract.ts     # Contrato de UnifiedDishDraftState y orquestación Paso 4
│   └── chef-screens-audit.contract.ts           # Manifiesto de auditoría (360/768/1024/1440 px) y tests autorizados
└── tasks.md                                     # Phase 2 (NO creado en /speckit-plan; generado en /speckit-tasks)
```

### Source Code (repository root)

```text
PRODUCT.md                                       # Actualizado en /impeccable init (specs 024-026 verificadas, 027 activa)

easyeat/frontend/src/
├── utils/
│   └── chefDishPersistence.ts                   # NUEVO (US4): orquestación secuencial Paso 4 sobre dishApi + recuperación parcial
├── components/
│   ├── chef/
│   │   ├── BrutalistCard.tsx                    # NUEVO (Módulo 0): superficie Brutalism + respuesta táctil mecánica
│   │   ├── BrutalistModal.tsx                   # NUEVO (Módulo 0): diálogo Brutalism sobre useAccessibleOverlay
│   │   ├── ChefMobileBottomBar.tsx              # NUEVO (US1): barra inferior fija móvil < 768px (>= 56px)
│   │   ├── ChefDishPauseToggle.tsx              # US3: interruptor táctil individual >= 56px en tríadas Brutalism
│   │   ├── ChefIngredientTable.tsx              # US5: grilla responsiva de BrutalistCard (conserva símbolo exportado)
│   │   └── DishRejectionFeedbackBanner.tsx      # US2/US4: banner Brutalism de devolución culinaria (cero datos financieros)
│   ├── layout/
│   │   ├── roleNavigationConfig.ts              # US1: flag useMobileBottomBar: true exclusivo para CHEF
│   │   └── RoleLayoutShell.tsx                  # US1/US2: prop fullWidth, cabecera Chef >= 48px, ChefMobileBottomBar móvil
│   ├── common/
│   │   └── WorkspaceLayout.tsx                  # US2: prop opcional fullWidth propagada a RoleLayoutShell
│   ├── kds/
│   │   ├── KitchenKanbanBoard.tsx               # US2: Brutalism claro, selector rápido móvil >= 56px, 2 columnas PC/tableta
│   │   ├── KdsQueuedCard.tsx                    # US2: tarjeta 3 bloques, modalidad, EMPEZAR >= 64px anti doble clic
│   │   └── KdsInPreparationCard.tsx             # US2: tarjeta 3 bloques, platos XXL neutros, alerta 4px >= 15 min, LISTO >= 64px
│   ├── kitchen/
│   │   └── KitchenTicketDelayControl.tsx        # US2: botón +5 >= 56x56 px + badge +N min + error inline opcional
│   ├── dish/
│   │   ├── DishStatusBadge.tsx                  # Módulo 0/US3/US4: migrado a StatusBadge con tokens chef.DESIGN.md
│   │   ├── DishStepper.tsx                      # US4: stepper numerado Brutalism 01-04 (>= 48px) sin bloqueo por dishId
│   │   ├── BasicIdentityStep.tsx                # US4: 2 columnas PC, contadores JetBrains Mono, Anterior/Siguiente
│   │   ├── RecipeBOMStep.tsx                    # US4: tarjetas Brutalism BOM (sin modo oscuro/tabla), Anterior/Siguiente
│   │   ├── IngredientSelectorModal.tsx          # US4: BrutalistModal + tarjetas Brutalism + Filtrar por categoría
│   │   ├── AllergenStep.tsx                     # US4: grillas de tarjetas checkbox + banner confirmación + Anterior/Siguiente
│   │   └── ReviewSubmitStep.tsx                 # US4: checklist + salto rápido + Guardar borrador / Guardar y enviar a revisión
│   ├── primitives/                              # REUTILIZADO INTACTO (Spec 024): Button, StatusBadge, FeedbackState, LoadingSkeleton, EmptyState
│   ├── diner/                                   # INTACTO (Spec 025): se importa únicamente useAccessibleOverlay
│   └── admin/                                   # INTACTO (Spec 026)
└── pages/
    ├── KitchenWorkspacePage.tsx                 # US2: fullWidth, tira compacta 4 KPIs arriba, error polling/despacho, sin bloque ingredientes
    ├── ChefDishesPage.tsx                       # US3: chips táctiles estado, tarjetas Brutalism, pausa >= 56px, BrutalistModal borrado
    ├── ChefDishEditorPage.tsx                   # US4: UnifiedDishDraftState, Paso 4 orquestado, guardia cambios sin guardar, barra móvil
    └── ChefIngredientsPage.tsx                  # US5: grilla tarjetas Brutalism 1/2/3 cols, Out of Stock danger, cero finanzas

easyeat/frontend/tests/
├── chef/                                        # NUEVO: suite trazable dedicada de Spec 027
│   ├── brutalistBlocksAndNav.test.tsx           # US1: BrutalistCard, BrutalistModal, navegación escritorio >= 48px y barra móvil >= 56px
│   ├── kdsBrutalistErgonomics.test.tsx          # US2: full-width, KPIs arriba, selector móvil, >= 15 min, fallo polling y despacho
│   ├── chefDishesBrutalist.test.tsx             # US3: chips estado, tarjetas, pausa >= 56px sin masiva, BrutalistModal borrado
│   ├── dishEditorUnifiedBrutalist.test.tsx      # US4: flujo Anterior/Siguiente, orquestación Paso 4, fallo parcial, cambios sin guardar
│   └── chefIngredientsBrutalist.test.tsx        # US5: tarjetas 1/2/3 cols, Out of Stock danger, aislamiento financiero FR-023
└── [actualizaciones acotadas FR-015]            # KitchenKanbanBoard, KitchenWorkspacePage, RoleLayoutShell, dish/* y us1-us4
```

**Structure Decision**: Se preserva intacta la estructura de `easyeat/frontend/src/`, añadiendo únicamente tres componentes Brutalism en `src/components/chef/` y un módulo puro de orquestación en `src/utils/chefDishPersistence.ts`. Se reutilizan los cinco primitivos base de Spec `024` y `useAccessibleOverlay` de Spec `025` sin modificar ningún archivo de backend, `src/api/`, `src/components/diner/` ni `src/components/admin/`.

---

## Architecture & Implementation Blueprint

### Módulo 0 — Fundaciones Brutalism en `src/components/chef/` (`FR-001`–`FR-004`)

1. Implementar `BrutalistCard.tsx` conforme a `contracts/brutalism-ui-blocks.contract.ts`: superficie clara `#FFFFFF` / `#F4F4F0` / `#E7E7E0`, borde estructural duro `2 px solid var(--ee-border-strong)` (`#111827`), sombra sólida `shadow-ee-elevated` (`4px 4px 0px #111827`), variantes `alert-warning` (`border-4 border-ee-warning-border`) y `alert-danger` (`border-2 border-ee-danger-border`), y micro-desplazamiento mecánico opcional (`interactive`) desactivado bajo `motion-reduce:transform-none motion-reduce:transition-none`.
2. Implementar `BrutalistModal.tsx` componiendo `useAccessibleOverlay` (`src/components/diner/useAccessibleOverlay.ts`): marco Brutalism `border-2 border-ee-border-strong bg-ee-surface rounded-ee-sm shadow-ee-elevated`, botón de cierre `min-h-[48px] min-w-[48px]` con foco `#111827`, cierre con `Escape` y retorno de foco, cuerpo desplazable `max-h-[75vh]` y pie diseñado para acciones `>= 56 px`.
3. Migrar `DishStatusBadge.tsx` y `DishRejectionFeedbackBanner.tsx` a `StatusBadge`, `BrutalistCard` y tokens `--ee-*` de `chef.DESIGN.md`, preservando el aislamiento estricto sin campos financieros.

### Módulo 1 — US1: Navegación táctil de un toque del Chef (`FR-005`)

1. Extender `RoleNavigationConfig` en `roleNavigationConfig.ts` con `useMobileBottomBar?: boolean` activado solo para `CHEF` (con sus tres secciones exactas: `Comandas KDS`, `Gestionar Platos`, `Ingredientes`).
2. Implementar `ChefMobileBottomBar.tsx`: barra fija inferior `md:hidden fixed inset-x-0 bottom-0 z-30 border-t-2 border-ee-border-strong bg-ee-surface-elevated` con `aria-label="Navegación rápida móvil de cocina"` (distinto de `"Secciones de cocina"` en cabecera de escritorio para preservar `RoleLayoutShell.test.tsx`), tres enlaces táctiles `min-h-[56px]`, íconos claros de `lucide-react`, etiqueta visible (`Comandas KDS`, `Platos`, `Ingredientes`), estado activo `aria-current="page"` en `#DD614C` / `#111827` y relleno inferior compatible con `env(safe-area-inset-bottom)`.
3. Actualizar `WorkspaceLayout.tsx` y `RoleLayoutShell.tsx`:
   - Propagar prop opcional `fullWidth?: boolean` (default `false`).
   - Cuando `config.exposeDirectDesktopNav` es `true` (`CHEF`), renderizar los enlaces de cabecera con `min-h-[48px]`, borde duro `2 px` y estado activo Brutalism de alto contraste.
   - Cuando `config.useMobileBottomBar` es `true`, renderizar `<ChefMobileBottomBar />` y aplicar `pb-24 md:pb-6` (o `pb-24 md:pb-4` con `fullWidth`) al elemento `<main>`.
   - Preservar sin cambios `COMENSAL`, `ENCARGADO` y `ADMINISTRADOR`.

### Módulo 2 — US2: Tablero KDS a pantalla completa para cocina caótica (`FR-006`, `FR-007`, `FR-008`)

1. Refactorizar `KitchenWorkspacePage.tsx` con `<WorkspaceLayout fullWidth ...>`:
   - Retirar por completo el bloque redundante de acceso a ingredientes.
   - Desestructurar `error: pollingError` e `isLoading` de `useKdsQueuePolling()` (sin modificar el hook) y agregar estado `dispatchError` para fallos de `EMPEZAR` / `LISTO`.
   - Renderizar arriba la tira ultracompacta Brutalism con los 4 contadores KPI (`En Cola`, `En Preparación`, `Para Retirar`, `Completadas hoy`) en `font-ee-mono tabular-nums`, seguido de `DishRejectionFeedbackBanner` (cuando `?dishId=...`), alertas `FeedbackState` accionables ante errores de sondeo (con botón `Reintentar` sin borrar `visibleTickets`) o de despacho, y `KitchenKanbanBoard` ocupando todo el ancho.
2. Refactorizar `KitchenKanbanBoard.tsx` en Brutalism claro (`#F4F4F0` / `#FFFFFF`, bordes `2 px #111827`, cero `slate-900`):
   - Agregar barra selectora fija en móvil (`md:hidden`) con dos botones `min-h-[56px]` (`Pendientes (N)` y `En preparación (M)`), conmutando visibilidad por clases responsivas (`hidden md:flex`) para mantener ambos `data-testid` presentes en el DOM y conservar las dos columnas lado a lado con scroll vertical independiente en `>= 768 px`.
   - Utilizar `EmptyState` Brutalism en columnas vacías conservando los mensajes `"Sin pedidos pendientes"` y `"Sin pedidos en preparación"`.
3. Refactorizar `KdsQueuedCard.tsx`, `KdsInPreparationCard.tsx` y `KitchenTicketDelayControl.tsx`:
   - Estructura de tres bloques duros separados con `select-none`: cabecera (`#Ticket` XXL en `JetBrains Mono`, franja horaria, `StatusBadge` de modalidad `Inmediato` / `Programado`, y en preparación cronómetro `MM min` + botón `+5` `min-h-[56px] min-w-[56px]` + badge `+N min`), cuerpo (en preparación: `[Cantidad]x [Nombre]` XXL en `text-ee-text` neutro `#111827`, cero checkboxes, cero notas vacías) y pie (`EMPEZAR` / `LISTO` de ancho completo `min-h-[64px]`, físicamente distanciado de `+5`).
   - Alerta `>= 15 min`: `variant="alert-warning"` (`border-4 border-ee-warning-border`, `data-delayed="true"`) y badge cronómetro en tríada `warning` con ícono/texto redundante manteniendo `text-ee-text` neutro en todos los platos.
   - `LISTO` ejecuta despacho inmediato en un toque sin modal y revierte la tarjeta mostrando `FeedbackState` si falla.

### Módulo 3 — US3: Gestión de platos y pausa rápida en `/cocina/platos` (`FR-009`, `FR-010`)

1. Rediseñar `ChefDishesPage.tsx`:
   - Cabecera Brutalism con título y enlace-botón `Nuevo Plato` (`min-h-[48px]`).
   - Reemplazar `<select>` por barra de chips táctiles Brutalism (`min-h-[48px]`, `aria-pressed`) para `Todos`, `Borrador`, `Pendiente de Aprobación`, `Publicado` y `Descartado`.
   - Reemplazar `<table>` por grilla responsiva de `BrutalistCard` (`grid-cols-1 md:grid-cols-2 xl:grid-cols-3`) con rótulo único de sección `Estado Operativo` a nivel de vista (preservando intacto `ChefDishesPage.test.tsx`), nombre, categoría, `DishStatusBadge`, `ChefDishPauseToggle` individual en tarjetas `PUBLISHED` (sin botones de pausa masiva por EC-016) y acciones táctiles `Editar` (`>= 48 px`) y `Eliminar` (en `DRAFT`).
   - Reemplazar `window.confirm` por `BrutalistModal` con botones `Cancelar` y `Eliminar permanentemente` (`min-h-[56px]`) y reemplazar `window.alert` por `FeedbackState` inline accesible.
   - Usar `LoadingSkeleton`, `EmptyState` y paginación táctil Brutalism (`>= 48 px`).
2. Rediseñar `ChefDishPauseToggle.tsx`: botón `role="switch"` de altura mínima `56 px`, borde duro `2 px`, tríadas accesibles `success` (`Activo para comensales`) y `warning` (`Pausado por cocina`), íconos `PlayCircle` / `PauseCircle` / `Loader2`, actualización optimista con reversión ante error y respuesta mecánica respetuosa de `prefers-reduced-motion`.

### Módulo 4 — US4: Editor guiado de fichas técnicas con flujo unificado (`FR-011`, `FR-012`)

1. Implementar `src/utils/chefDishPersistence.ts` conforme a `contracts/chef-unified-dish-editor.contract.ts`: orquestación secuencial sobre `dishApi` existente (`createDishDraft`/`updateDish` → `uploadPhoto`/`deletePhoto` → `updateRecipe` → `updateDishAllergens` → opcional `submitForReview`), retornando `persistedDishId` aun ante fallo intermedio para permitir reintento idempotente sin duplicación.
2. Refactorizar `ChefDishEditorPage.tsx`:
   - Mantener `UnifiedDishDraftState` completo en memoria, precargando datos de `dishApi.getDishDetail` y propuestas de `chefReviewFeedbackApi.getFeedback` (`FR-042`, sin finanzas) cuando existe `:id`.
   - Sincronizar URL mediante `navigate(\`/cocina/platos/\${createdId}\`, { replace: true })` con bandera de salto de recarga cuando `createDishDraft` crea el borrador durante Paso 4.
   - Exponer insignia `"Cambios sin guardar"`, listener `beforeunload` y `BrutalistModal` de confirmación al pulsar `← Volver a Platos` cuando `isDirty === true` (inactivo tras guardar o en modo solo lectura).
   - Reservar relleno inferior coordinado (`pb-40 md:pb-6`) para que la barra de acción móvil fija del paso (`bottom-[68px] md:static`) nunca colisione con `ChefMobileBottomBar`.
3. Refactorizar los 5 subcomponentes de `src/components/dish/` (preservando explícitamente los nombres de props `initialData` en `BasicIdentityStep`, `initialRecipe` en `RecipeBOMStep` e `initialRestrictions` en `AllergenStep`, alimentados desde `UnifiedDishDraftState`, para mantener intacto `ChefDishEditorPage.test.tsx` de Spec `007`):
   - `DishStepper.tsx`: stepper numerado Brutalism (`01 Identidad`, `02 Receta (BOM)`, `03 Alérgenos`, `04 Revisión y Envío`, botones `min-h-[48px]`, indicadores de completitud, navegable sin requerir `dishId` previo).
   - `BasicIdentityStep.tsx`: composición 2 columnas en escritorio (`lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]`, 1 columna móvil/tableta), contadores `JetBrains Mono` (`0/100`, `0/500`), validaciones existentes (nombre obligatorio, `<= 5 MB`, JPEG/PNG/WebP), botones `"Siguiente"` (sin botón `"Guardar"` individual).
   - `RecipeBOMStep.tsx` e `IngredientSelectorModal.tsx`: eliminar todo modo oscuro y tablas; representar líneas BOM y resultados del selector como `BrutalistCard` claras con entrada numérica grande, unidad `JetBrains Mono`, validación `> 0` y enteros `'u'`, alerta `FeedbackState` ante duplicados, `<select>` accesible `Filtrar por categoría` en `BrutalistModal` y botones `"Anterior"` / `"Siguiente"`.
   - `AllergenStep.tsx`: grillas táctiles de tarjetas checkbox Brutalism para `Estilos de Vida Dietarios` y `Alérgenos Comunes`, bloque destacado de confirmación explícita `Confirmar sección de alérgenos` (válido con 0 etiquetas) y botones `"Anterior"` / `"Siguiente"`.
   - `ReviewSubmitStep.tsx`: lista de verificación Brutalism de los 3 criterios con acción de salto rápido a cada paso incompleto, banner bloqueado en `PENDING_PRICE` / `PUBLISHED` / `CANCELLED`, botón `"Anterior"` y las dos acciones finales explícitas: `Guardar borrador` y `Guardar y enviar a revisión` (`>= 56 px` en móvil).

### Módulo 5 — US5: Catálogo de ingredientes de cocina en tarjetas Brutalism (`FR-009`, `FR-013`)

1. Refactorizar `ChefIngredientsPage.tsx`: enlace táctil `← Volver al Panel KDS de Cocina`, buscador Brutalism (`placeholder="Buscar materia prima..."`, altura `>= 48 px`), selector accesible de categoría (`Todas`, `Produce`, `Meat`, `Dairy`, `Dry Goods`, `Spices`), `FeedbackState` para errores y paginación táctil Brutalism (`>= 48 px`).
2. Refactorizar `ChefIngredientTable.tsx` (conservando el nombre exportado para compatibilidad): renderizar grilla responsiva de `BrutalistCard` (`grid-cols-1 md:grid-cols-2 lg:grid-cols-3`), `LoadingSkeleton` en carga, `EmptyState` en listado vacío, existencia y unidad en `JetBrains Mono` tabular, `StatusBadge` preservando `In Stock` / `Out of Stock`, énfasis inmediato `alert-danger` con ícono + texto en ítems `Out of Stock` y supresión absoluta de datos financieros (FR-023).

---

## Guía de sistema de diseño Brutalism y checklist de QA

**Declaración de intención**: Construir un puesto de trabajo culinario Brutalism funcional, luminoso y de alto contraste (`chef.DESIGN.md`) que priorice la lectura a distancia, la operación con guantes o manos húmedas en un solo toque y la accesibilidad WCAG 2.2 AA en modo **Operate** sobre cualquier ornamento decorativo.

### Tokens y fundamentos obligatorios (`chef.DESIGN.md`)

- **Superficies y tinta**: lienzo `#FFFFFF` (`--ee-surface`), elevado `#F4F4F0` (`--ee-surface-elevated`), sutil `#E7E7E0` (`--ee-surface-subtle`); texto principal `#111827` (17.74:1), texto secundario `#374151` (10.31:1).
- **Acentos y semántica**: primario terracota `#DD614C` con tinta `#111827` (4.97:1, hover `#E67966` 6.16:1); secundario ocre `#DAA144` con tinta `#111827` (7.74:1); peligro sólido `#B91C1C` con texto `#FFFFFF` (6.47:1); tríadas `neutral`, `info`, `success`, `warning` y `danger` consumidas exclusivamente vía tokens `--ee-*`.
- **Geometría y elevación**: bordes `2 px` en `#111827` (`4 px` en alerta de demora KDS `>= 15 min`), sombra dura `4px 4px 0px #111827`, radios `0 / 2 / 4 px`, escala de espaciado `4 / 8 / 12 / 16 / 24 / 32 px`.
- **Tipografía**: `Darker Grotesque` (500–800) para encabezados, navegación y texto operativo; `JetBrains Mono` (400–600) con `tabular-nums` para `#Ticket`, cronómetros, `+5`/`+N min`, KPIs, stock, unidades y contadores.

### Reglas de estados, contenido y anti-patrones

- **Estados obligatorios**: todo control interactivo MUST definir visualmente `default`, `hover`, `focus-visible` (`outline: 2px solid #111827`, offset `2px`), `active` (desplazamiento mecánico suprimido bajo `prefers-reduced-motion`), `disabled`, `loading` (con `aria-busy` y bloqueo de reenvío) y `error`.
- **Tono y microcopy**: español directo, conciso y accionable (por ejemplo: `"EMPEZAR"`, `"LISTO"`, `"Guardar borrador"`, `"Guardar y enviar a revisión"`, `"Cambios sin guardar"`), conservando las etiquetas contractuales `"In Stock"` / `"Out of Stock"` y los textos verificados por pruebas de dominio.
- **Prohibiciones absolutas (Do / Don't)**:
  - MUST usar tokens semánticos `--ee-*`; DON'T introducir clases heredadas `bg-slate-900`, `bg-gray-900`, `dark:bg-zinc-900`, degradados decorativos ni tipografía `Inter`.
  - MUST renderizar todas las listas del Chef como tarjetas `BrutalistCard`; DON'T mantener etiquetas `<table>` con desplazamiento horizontal ni anidar tarjetas dentro de tarjetas sin función operativa.
  - MUST separar físicamente `+5` (cabecera) de `LISTO` (pie) con al menos `12`–`16 px` de margen respecto de controles vecinos; DON'T agrupar acciones destructivas o de demora junto al botón de despacho.
  - MUST usar `BrutalistModal` y `FeedbackState`; DON'T invocar `window.confirm` ni `window.alert`.
  - MUST garantizar contraste `>= 4.5:1` en texto normal y `>= 3.0:1` en bordes/foco; DON'T transmitir alertas (`>= 15 min`, `Out of Stock`, `Pausado por cocina`) solo mediante color.

### Checklist de QA para code review

- [ ] Ningún archivo del Chef usa colores oscuros heredados (`slate-900`, `gray-900`, `zinc-900`) ni hexadecimales fuera de `chef.DESIGN.md`.
- [ ] Cero tablas `<table>` con scroll horizontal y cero `window.confirm` / `window.alert` en las 5 rutas del Chef.
- [ ] Objetivos táctiles verificados: cabecera/chips/stepper `>= 48 px`, barra inferior móvil / `+5` / pausa / botones de modal / tabs KDS móvil `>= 56 px`, botones `EMPEZAR` y `LISTO` `>= 64 px`.
- [ ] Cero desbordamiento horizontal en `360 px`, `768 px`, `1024 px` y `1440 px`, sin solapamiento entre `ChefMobileBottomBar` y la barra fija móvil del editor.
- [ ] Alerta KDS `>= 15 min` mantiene los platos en tinta `#111827` neutra y `LISTO` despacha sin modal ni checkboxes por plato.
- [ ] Paso 4 retiene `persistedDishId` ante fallo intermedio y no duplica el plato al reintentar; `← Volver a Platos` confirma solo si `isDirty === true`.
- [ ] Cero datos financieros (costos, márgenes, PVP o símbolos monetarios) en toda vista `/cocina/*`.
- [ ] `prefers-reduced-motion` suprime desplazamientos y animaciones continuas; foco visible y tecla `Escape` operan en modales y menú hamburguesa.
- [ ] Cero cambios en `easyeat/backend/`, `src/api/`, `src/components/diner/`, `src/components/admin/` ni en pantallas de otros roles.

---

## Control estricto de exclusiones

- **Prohibido** modificar cualquier archivo bajo `easyeat/backend/` o `easyeat/frontend/src/api/`.
- **Prohibido** modificar archivos de Spec `025` (`src/components/diner/*`, pantallas del Comensal), Spec `026` (`src/components/admin/*`, pantallas del Administrador, `FirstLoginPasswordPage.tsx`) o pantallas del Encargado del Mostrador.
- **Prohibido** agregar dependencias nuevas en `package.json`, botones de pausa masiva (EC-016), checkboxes por plato en KDS o cualquier campo financiero en vistas del Chef (FR-023).
- **Prohibido** debilitar aserciones conductuales en suites `001`–`026` fuera de las actualizaciones acotadas y registradas en `contracts/chef-screens-audit.contract.ts` (`FR-015`).

---

## Complexity Tracking

| Elemento | Por qué es necesario | Alternativa más simple rechazada |
|---|---|---|
| `BrutalistCard`, `BrutalistModal` y `ChefMobileBottomBar` en `src/components/chef/` | Encapsulan el marco duro `2 px #111827`, sombra `4px 4px 0px #111827`, objetivos táctiles `>= 56 px`, micro-desplazamiento con `prefers-reduced-motion` y navegación inferior móvil exclusiva del Chef sin tocar Spec `025` (`AccessibleModal`). | Reutilizar `AccessibleModal` tal cual (viola bordes `2 px #111827` y cierre/acciones de cocina) o duplicar clases Brutalism en 4 páginas y ~14 componentes. |
| `src/utils/chefDishPersistence.ts` | Aísla la orquestación secuencial del Paso 4 sobre los endpoints existentes de `dishApi` y la recuperación idempotente de `persistedDishId` ante fallos parciales, haciendo el flujo directamente testeable de forma unitaria. | Meter toda la máquina de estados y manejo de fallos parciales inline dentro de `ChefDishEditorPage.tsx` (alta complejidad cognitiva y menor testabilidad). |
| Prop opcional `fullWidth` en `WorkspaceLayout` / `RoleLayoutShell` | Permite que `/cocina` ocupe el 100 % del ancho del viewport para operación KDS (`FR-006`) manteniendo `max-w-7xl` en el resto de pantallas y roles sin hacks de márgenes negativos. | Márgenes negativos en `KitchenWorkspacePage` (propensos a desbordamiento horizontal en `360 px`). |
