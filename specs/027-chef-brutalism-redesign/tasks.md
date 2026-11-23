# Tasks: Rediseño Brutalism de la experiencia operativa y administrativa del Chef

**Input**: Documentos de diseño en `specs/027-chef-brutalism-redesign/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`) y auditoría base Impeccable `/critique` sobre `easyeat/frontend/src/pages/KitchenWorkspacePage.tsx` y 18 archivos del rol Chef (`12/40` — Poor: P0 errores silenciosos y ausencia de `isLoading` skeleton / feedback `aria-live`, P0 objetivos táctiles `< 44 px` + contraste WCAG 2.2 AA fallido + alerta `>= 15 min` solo por color, P1 ruptura Brutalism `chef.DESIGN.md` + 4 hallazgos `side-tab` `border-l-4` en `KitchenWorkspacePage.tsx` L133–L148, P2 scroll anidado y cabecera sin `flex-wrap` en `360 px`).

**Prerequisites**: `plan.md` (aprobado), `spec.md` (`US1`–`US5`), `research.md`, `data-model.md`, `contracts/brutalism-ui-blocks.contract.ts`, `contracts/chef-navigation-and-kds.contract.ts`, `contracts/chef-unified-dish-editor.contract.ts`, `contracts/chef-screens-audit.contract.ts`, `quickstart.md`.

**Tests (TDD obligatorio)**: Todas las fases funcionales siguen ciclos verticales **TDD-RED (escribir prueba primero y verificar fallo inicial) → TDD-GREEN (implementación mínima que hace pasar la prueba)** en `easyeat/frontend/tests/chef/`, seguidos de la verificación de regresión sobre la suite heredada (`001`–`026`). **El backend (`easyeat/backend/`), los servicios Axios (`easyeat/frontend/src/api/`), los archivos de Spec `025` (`easyeat/frontend/src/components/diner/`) y los archivos de Spec `026` (`easyeat/frontend/src/components/admin/`) NO se modifican** (excepción `single-tier frontend-only` autorizada bajo Principio X).

**Organization**: Tareas atómicas ordenadas por dependencias y agrupadas por User Story (`US1` a `US5`) para permitir implementación, verificación independiente y commits atómicos por historia dentro de `feat/027-chef-brutalism-redesign`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Ejecutable en paralelo (archivos distintos, sin dependencia sobre tareas incompletas).
- **[Story]**: User Story a la que pertenece la tarea (`[US1]` a `[US5]`). Las fases de Setup, Foundational y Polish no llevan etiqueta de historia.
- Todas las tareas incluyen rutas exactas de archivo dentro del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)
> **GitHub Issue**: [#497](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/497)

**Purpose**: Verificar la línea base limpia de la rama `feat/027-chef-brutalism-redesign` y preparar el directorio de pruebas trazables de Spec `027` sin modificar dependencias ni backend.

- [x] T001 Verificar estado limpio de compilación y suite base (`npx tsc --noEmit` y `npx vitest run`) en `easyeat/frontend/` antes de iniciar cambios de Spec `027`
- [x] T002 [P] Crear directorio de pruebas trazables `easyeat/frontend/tests/chef/` para las suites TDD de Spec `027`

---

## Phase 2: Foundational (Blocking Prerequisites)
> **GitHub Issue**: [#498](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/498)

**Purpose**: Crear los bloques base Brutalism en `easyeat/frontend/src/components/chef/` (`BrutalistCard`, `BrutalistModal` sobre `useAccessibleOverlay`) y migrar los componentes transversales del Chef (`DishStatusBadge`, `DishRejectionFeedbackBanner`) a `chef.DESIGN.md`, reutilizando los cinco primitivos base de Spec `024` sin modificar `src/components/diner/` ni `src/components/admin/`.

**CRITICAL**: Ninguna User Story puede comenzar hasta completar Phase 2 en verde.

### Slice TDD 0 — Fundaciones Brutalism y componentes transversales (`FR-001`–`FR-004`)

> **RED FIRST**: Escribir las pruebas unitarias/de contrato y confirmar fallo inicial antes de implementar los bloques fundacionales.

- [x] T003 [TDD-RED] Escribir pruebas unitarias en `easyeat/frontend/tests/chef/brutalistBlocksAndNav.test.tsx` (bloque 1: fundaciones) cubriendo: (1) `BrutalistCard` variantes `default`/`elevated`/`subtle`/`alert-warning`/`alert-danger`, borde `2 px #111827` (`4 px` en `alert-warning`), sombra dura `shadow-ee-elevated`, respuesta mecánica táctil (`interactive`) anulada bajo `motion-reduce`; (2) `BrutalistModal` compuesto sobre `useAccessibleOverlay` con `role="dialog"`, `aria-modal="true"`, cierre por `Escape` y backdrop, retorno de foco, botón de cierre `>= 48×48 px` y acciones de pie `>= 56 px`; (3) `DishStatusBadge` migrado a `StatusBadge` con etiquetas en español y tokens `--ee-*`; y (4) `DishRejectionFeedbackBanner` en estética Brutalism con propuestas precargadas y cero campos financieros; ejecutar `npx vitest run tests/chef/brutalistBlocksAndNav.test.tsx` y confirmar fallo inicial
- [x] T004 [P] [TDD-GREEN] Implementar `BrutalistCard` conforme a `specs/027-chef-brutalism-redesign/contracts/brutalism-ui-blocks.contract.ts` en `easyeat/frontend/src/components/chef/BrutalistCard.tsx`
- [x] T005 [P] [TDD-GREEN] Implementar `BrutalistModal` componiendo `useAccessibleOverlay` desde `easyeat/frontend/src/components/diner/useAccessibleOverlay.ts` (sin modificar Spec `025`) conforme a `specs/027-chef-brutalism-redesign/contracts/brutalism-ui-blocks.contract.ts` en `easyeat/frontend/src/components/chef/BrutalistModal.tsx`
- [x] T006 [P] [TDD-GREEN] Migrar `DishStatusBadge` a `StatusBadge` y tríadas semánticas WCAG 2.2 AA de `chef.DESIGN.md` en `easyeat/frontend/src/components/dish/DishStatusBadge.tsx`
- [x] T007 [P] [TDD-GREEN] Migrar `DishRejectionFeedbackBanner` a `BrutalistCard` y tokens `--ee-*` de `chef.DESIGN.md` preservando el aislamiento sin datos financieros en `easyeat/frontend/src/components/chef/DishRejectionFeedbackBanner.tsx`
- [x] T008 [TDD-GREEN] Ejecutar `npx vitest run tests/chef/brutalistBlocksAndNav.test.tsx tests/components/dish/DishStatusBadge.test.tsx tests/components/chef/DishRejectionFeedbackBanner.test.tsx` y confirmar verde total de fundaciones (depende de T004, T005, T006, T007)

**Checkpoint**: Fundaciones Brutalism y componentes transversales listos; las 5 User Stories ya pueden avanzar.

---

## Phase 3: User Story 1 — Arquitectura visual Brutalism y navegación táctil de un toque del Chef (Priority: P1) 🎯 MVP
> **GitHub Issue**: [#499](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/499)

**Goal**: Extender el contenedor de navegación exclusivamente para `CHEF` elevando los tres accesos directos de cabecera en tableta/escritorio (`>= 768 px`, Spec `005` C-08) a `min-h-[48px]`, incorporando la barra inferior fija Brutalism (`ChefMobileBottomBar`, `>= 56 px`) en móvil (`< 768 px`) con reserva inferior en `<main>` y añadiendo la prop opcional `fullWidth` para `/cocina`, sin alterar `COMENSAL`, `ENCARGADO` ni `ADMINISTRADOR`.

**Independent Test**: Montar `RoleLayoutShell` con usuario `CHEF` y otros roles verificando `ROLE_NAVIGATION_CONFIG.CHEF`, enlaces de cabecera `>= 48 px` con `aria-current="page"`, `ChefMobileBottomBar` con 3 accesos `>= 56 px`, ausencia de enlace `/perfil` en menú hamburguesa, comportamiento de `fullWidth` y cero cambios en los contenedores de `COMENSAL`, `ENCARGADO` y `ADMINISTRADOR`.

### Slice TDD 1 — Seam de navegación táctil de un toque (`FR-005`, `FR-006`)

> **RED FIRST**: Ampliar `brutalistBlocksAndNav.test.tsx` con los escenarios de US1 y confirmar fallo inicial antes de modificar el shell.

- [x] T009 [US1] [TDD-RED] Ampliar `easyeat/frontend/tests/chef/brutalistBlocksAndNav.test.tsx` (bloque 2: navegación) verificando: (1) `ROLE_NAVIGATION_CONFIG.CHEF` activa `useMobileBottomBar: true` y mantiene los 3 destinos (`Comandas KDS`, `Gestionar Platos`, `Ingredientes`) mientras `COMENSAL`, `ENCARGADO` y `ADMINISTRADOR` no activan barra inferior; (2) `ChefMobileBottomBar` renderiza los 3 accesos con `min-h-[56px]`, `aria-current="page"` y estilo Brutalism activo; (3) `RoleLayoutShell` aplica `min-h-[48px]` a los enlaces directos de cabecera del Chef, reserva `pb-24 md:pb-6` en `<main>` y elimina `max-w-7xl` cuando `fullWidth` es `true`; y (4) el menú hamburguesa del Chef conserva metadatos de cuenta y `Cerrar sesión` sin enlace a `/perfil`; ejecutar `npx vitest run tests/chef/brutalistBlocksAndNav.test.tsx` y confirmar fallo inicial
- [x] T010 [P] [US1] [TDD-GREEN] Actualizar `easyeat/frontend/src/components/layout/roleNavigationConfig.ts` agregando `useMobileBottomBar?: boolean` habilitado exclusivamente para `CHEF`
- [x] T011 [P] [US1] [TDD-GREEN] Implementar `ChefMobileBottomBar` (`md:hidden`, fija al borde inferior, `aria-label="Navegación rápida móvil de cocina"`, 3 botones `min-h-[56px]`, estado activo Brutalism `aria-current="page"`, soporte `safe-area-inset-bottom`) conforme a `specs/027-chef-brutalism-redesign/contracts/chef-navigation-and-kds.contract.ts` en `easyeat/frontend/src/components/chef/ChefMobileBottomBar.tsx`
- [x] T012 [US1] [TDD-GREEN] Extender `easyeat/frontend/src/components/layout/RoleLayoutShell.tsx` y `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` con prop opcional `fullWidth?: boolean`, enlaces directos de cabecera Chef en `min-h-[48px]`, renderizado de `ChefMobileBottomBar` exclusivo para `CHEF` y relleno inferior móvil `pb-24 md:pb-6` (depende de T010, T011)
- [x] T013 [US1] [TDD-GREEN] Actualizar de forma acotada la regex de altura en cabecera Chef (`min-h-[(44|48)px]`) en `easyeat/frontend/tests/components/RoleLayoutShell.test.tsx` conforme a `FR-015(d)` / `FR-005` y ejecutar `npx vitest run tests/chef/brutalistBlocksAndNav.test.tsx tests/components/RoleLayoutShell.test.tsx tests/components/WorkspaceLayout.test.tsx` confirmando verde total (depende de T012)

**Checkpoint**: US1 completa y verificable de forma independiente (MVP de navegación táctil de un toque listo para commit atómico).

---

## Phase 4: User Story 2 — Tablero KDS a pantalla completa para operación caótica en cocina (Priority: P1)
> **GitHub Issue**: [#500](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/500)

**Goal**: Migrar integralmente `/cocina` y sus componentes KDS al lienzo claro Brutalism a pantalla completa (`fullWidth`), retirando el bloque redundante de ingredientes y los 4 hallazgos `side-tab` (`border-l-4`), colocando arriba la tira ultracompacta de 4 KPIs, resolviendo todos los hallazgos P0–P2 del `/impeccable critique` base (`12/40`) compatibles con Spec `027` (`LoadingSkeleton` inicial, `FeedbackState` de error/reintento sin borrar comandas, `"DESPACHANDO..."` y `aria-live`, `+5` `>= 56×56 px` separado de `EMPEZAR`/`LISTO` `>= 64 px`, alerta `>= 15 min` con borde `4 px` + `DEMORA` y platos neutros `#111827`, selector rápido móvil `>= 56 px`, cabecera `flex-wrap`), preservando `LISTO` en un toque sin modal y `KdsQueuedCard` compacto (`FR-007`).

**Independent Test**: Montar `KitchenWorkspacePage` y `KitchenKanbanBoard` con comandas `QUEUED` e `IN_PREPARATION` (inmediatas, programadas, `< 15 min`, `>= 15 min`, con `+5` aplicado, en carga inicial y ante fallos de polling/despacho) verificando todos los requisitos ergonómicos, visuales y de resiliencia sin clases de modo oscuro ni `border-l-4`.

### Slice TDD 2 — Seam del tablero KDS Brutalism y resiliencia de cocina (`FR-006`, `FR-007`, `FR-008`, critique P0–P2)

> **RED FIRST**: Escribir la suite de integración y ergonomía de US2 y confirmar fallo inicial antes de refactorizar el KDS.

- [x] T014 [US2] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/chef/kdsBrutalistErgonomics.test.tsx` verificando: (1) `/cocina` usa `fullWidth`, elimina el bloque redundante de ingredientes, renderiza arriba la tira ultracompacta de 4 KPIs (`En Cola`, `En Preparación`, `Para Retirar`, `Completadas hoy`) en `JetBrains Mono` sin `border-l-4` ni `slate-900` y muestra `LoadingSkeleton` durante la carga inicial; (2) selector rápido móvil Brutalism (`>= 56 px`) conmutando entre `Pendientes` y `En preparación` manteniendo ambos `data-testid` de columna en el DOM; (3) tarjetas de 3 bloques duros con `#Ticket` XXL, franja horaria, insignia `Inmediato`/`Programado`, cabecera con `flex-wrap`, botón `+5` `min-h-[56px] min-w-[56px]` separado físicamente de `EMPEZAR` / `LISTO` `min-h-[64px]`, texto `"DESPACHANDO..."` en carga de `LISTO`, despacho `LISTO` en 1 toque sin modal y cero checkboxes por plato; (4) alerta `>= 15 min` con `border-4`, `data-delayed="true"`, etiqueta/ícono `DEMORA` y platos en tinta neutra `text-ee-text` (`#111827`); y (5) banner `FeedbackState` con botón `Reintentar` ante fallo de polling (conservando comandas visibles) y alerta `role="alert"` / `aria-live` ante fallo de `EMPEZAR`, `LISTO` (restituyendo la tarjeta) o `+5`; ejecutar `npx vitest run tests/chef/kdsBrutalistErgonomics.test.tsx` y confirmar fallo inicial
- [x] T015 [P] [US2] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/kitchen/KitchenTicketDelayControl.tsx` con botón `+5` de `min-h-[56px] min-w-[56px]` en estética Brutalism táctil, insignia acumulada `+N min` vía `StatusBadge`, anuncio `aria-live="polite"` y callback opcional de error inline
- [x] T016 [P] [US2] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/kds/KdsQueuedCard.tsx` y `easyeat/frontend/src/components/kds/KdsInPreparationCard.tsx` en Brutalism claro de 3 bloques duros (`#Ticket` XXL, `Inmediato`/`Programado`, cabecera `flex-wrap`, platos XXL en `text-ee-text` neutro sin checkboxes ni notas vacías, alerta `>= 15 min` con `border-4 border-ee-warning-border` + `DEMORA`, y botones `EMPEZAR` / `LISTO` `min-h-[64px]` con `"DESPACHANDO..."`)
- [x] T017 [US2] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/kds/KitchenKanbanBoard.tsx` en Brutalism claro eliminando `bg-slate-900`, incorporando selector rápido móvil (`>= 56 px`) con clases responsivas `hidden md:flex` (manteniendo ambos `data-testid` de columna), soporte `isLoading` con `LoadingSkeleton` y `EmptyState` en columnas vacías (depende de T015, T016)
- [x] T018 [US2] [TDD-GREEN] Refactorizar `easyeat/frontend/src/pages/KitchenWorkspacePage.tsx` usando `<WorkspaceLayout fullWidth>`, retirando el bloque redundante de ingredientes y las 4 tarjetas `border-l-4`, ubicando la tira compacta Brutalism de 4 KPIs arriba, consumiendo `isLoading` y `error` de `useKdsQueuePolling()` para mostrar `FeedbackState` con reintento sin borrar comandas, y mostrando errores inline con `aria-live` ante fallos de `EMPEZAR`, `LISTO` o `+5` (depende de T017)
- [x] T019 [US2] [TDD-GREEN] Aplicar actualizaciones acotadas autorizadas por `FR-015(a)(b)` en `easyeat/frontend/tests/components/KitchenKanbanBoard.test.tsx` (clases Brutalism claras en alerta `>= 15 min`) y `easyeat/frontend/tests/pages/KitchenWorkspacePage.test.tsx` (retiro del bloque redundante de ingredientes embebido) (depende de T018)
- [x] T020 [US2] [TDD-GREEN] Ejecutar `npx vitest run tests/chef/kdsBrutalistErgonomics.test.tsx tests/components/KitchenKanbanBoard.test.tsx tests/components/KitchenTicketReadyDispatch.test.tsx tests/pages/KitchenWorkspacePage.test.tsx src/test/kitchen/KitchenTicketDelayControl.test.tsx` confirmando verde total (depende de T019)

**Checkpoint**: US2 completa y verificable de forma independiente (KDS de grado cocina listo para commit atómico).

---

## Phase 5: User Story 3 — Gestión del catálogo de platos y pausa rápida en servicio (Priority: P1)
> **GitHub Issue**: [#501](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/501)

**Goal**: Migrar `/cocina/platos` a tarjetas Brutalism responsivas sin tablas ni scroll horizontal, reemplazar el filtro `<select>` por chips táctiles (`>= 48 px`), llevar el interruptor individual `Estado Operativo` a `>= 56 px` (sin pausa masiva por EC-016) y reemplazar `window.confirm` / `window.alert` por `BrutalistModal` (`>= 56 px`) y `FeedbackState` inline.

**Independent Test**: Montar `ChefDishesPage` con platos `DRAFT`, `PENDING_PRICE`, `PUBLISHED` (activo y pausado) y `CANCELLED` verificando chips de estado, tarjetas sin `<table>`, interruptor individual optimista con reversión ante error, modal Brutalism de eliminación de borrador operable por teclado y paginación/estados vacíos/error.

### Slice TDD 3 — Seam del catálogo de platos y pausa táctil (`FR-009`, `FR-010`)

> **RED FIRST**: Escribir la suite de integración de US3 y confirmar fallo inicial antes de migrar `/cocina/platos`.

- [x] T021 [US3] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/chef/chefDishesBrutalist.test.tsx` verificando: (1) cabecera con enlace `Nuevo Plato` (`>= 48 px`), rótulo único `Estado Operativo` a nivel de vista y barra de chips táctiles Brutalism (`>= 48 px`, `aria-pressed`) para los 5 estados; (2) grilla responsiva de `BrutalistCard` sin `<table>` con `DishStatusBadge` y acciones táctiles (`>= 48 px`); (3) interruptor individual `ChefDishPauseToggle` (`min-h-[56px]`, `role="switch"`) por plato `PUBLISHED` con etiquetas `Activo para comensales` / `Pausado por cocina`, actualización optimista/reversión y cero botones de pausa masiva (EC-016); (4) eliminación de borrador `DRAFT` vía `BrutalistModal` (`Escape`, foco, botones `>= 56 px`, sin invocar `window.confirm` ni `window.alert`) con error inline en `FeedbackState`; y (5) `LoadingSkeleton`, `EmptyState` y paginación Brutalism; ejecutar `npx vitest run tests/chef/chefDishesBrutalist.test.tsx` y confirmar fallo inicial
- [x] T022 [P] [US3] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/chef/ChefDishPauseToggle.tsx` como botón `role="switch"` de `min-h-[56px]` con borde duro `2 px #111827`, tríadas accesibles `success`/`warning` + íconos, micro-desplazamiento táctil respetuoso de `motion-reduce` y actualización optimista con reversión ante fallo
- [x] T023 [US3] [TDD-GREEN] Rediseñar `easyeat/frontend/src/pages/ChefDishesPage.tsx` reemplazando `<select>` por chips táctiles (`>= 48 px`), `<table>` por grilla de `BrutalistCard` con rótulo único `Estado Operativo` a nivel de vista (preservando `ChefDishesPage.test.tsx`), `window.confirm` por `BrutalistModal` (`>= 56 px`), `window.alert` por `FeedbackState`, e integrando `LoadingSkeleton`, `EmptyState` y paginación Brutalism (depende de T022)
- [x] T024 [US3] [TDD-GREEN] Ejecutar `npx vitest run tests/chef/chefDishesBrutalist.test.tsx tests/pages/ChefDishesPage.test.tsx tests/components/ChefDishPauseToggle.test.tsx` confirmando verde total (depende de T023)

**Checkpoint**: US3 completa y verificable de forma independiente (catálogo de platos y pausa rápida listos para commit atómico).

---

## Phase 6: User Story 4 — Editor guiado de fichas técnicas con flujo "Siguiente" y guardado unificado (Priority: P1)
> **GitHub Issue**: [#502](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/502)

**Goal**: Transformar `/cocina/platos/nuevo` y `/cocina/platos/:id` en un asistente guiado Brutalism controlado por `UnifiedDishDraftState` con stepper numerado (`01`–`04`, `>= 48 px`), navegación `"Anterior"` / `"Siguiente"` con validación por paso en pasos 1–3, tarjetas Brutalism en BOM y modal selector (`BrutalistModal`), guardado/envío unificado en Paso 4 orquestando secuencialmente `dishApi` existente con recuperación ante fallos parciales (`persistedDishId` + `replace: true`), protección de cambios sin guardar (`StatusBadge` + `beforeunload` + `BrutalistModal`) y barra fija móvil coordinada sobre `ChefMobileBottomBar`.

**Independent Test**: Montar `ChefDishEditorPage` en creación nueva (`/cocina/platos/nuevo`), edición con devolución (`FR-042`) y modo solo lectura (`PENDING_PRICE`/`CANCELLED`), recorriendo pasos 1–4 con `"Anterior"` / `"Siguiente"`, validando reglas de negocio por paso, probando fallo parcial y reintento sin duplicación, y confirmando la actualización acotada de pruebas heredadas de Spec `005`.

### Slice TDD 4 — Seam del editor guiado unificado y orquestación Paso 4 (`FR-011`, `FR-012`, `FR-015(c)`)

> **RED FIRST**: Escribir las pruebas unitarias de `chefDishPersistence` y la suite de integración de US4 y confirmar fallo inicial antes de refactorizar el editor.

- [x] T025 [US4] [TDD-RED] Escribir suite en `easyeat/frontend/tests/chef/dishEditorUnifiedBrutalist.test.tsx` cubriendo: (1) orquestación secuencial `persistUnifiedDishDraft` (`createDishDraft`/`updateDish` → foto → receta → alérgenos → opcional `submitForReview`) y retención de `persistedDishId` ante fallo intermedio para reintento idempotente sin duplicar; (2) stepper numerado Brutalism `01`–`04` (`>= 48 px`) habilitado sin `dishId` previo y navegación `"Anterior"` / `"Siguiente"` en pasos 1–3 validando nombre obligatorio, foto `<= 5 MB`/MIME, cantidades `> 0` y enteros en `'u'`, y cero etiquetas en alérgenos si está confirmado; (3) Paso 1 en 2 columnas escritorio con contadores `JetBrains Mono`, Paso 2 en `BrutalistCard` (sin modo oscuro ni `<table>`) y `IngredientSelectorModal` en `BrutalistModal` con `Filtrar por categoría`, Paso 3 en grillas de tarjetas checkbox, Paso 4 con checklist + salto rápido + `Guardar borrador` y `Guardar y enviar a revisión`; (4) badge `"Cambios sin guardar"`, `beforeunload` y `BrutalistModal` al pulsar `← Volver a Platos` con cambios pendientes; y (5) modo solo lectura en `PENDING_PRICE`/`PUBLISHED`/`CANCELLED`, precarga de propuestas de devolución, barra móvil coordinada sobre `ChefMobileBottomBar` y cero datos financieros; ejecutar `npx vitest run tests/chef/dishEditorUnifiedBrutalist.test.tsx` y confirmar fallo inicial
- [x] T026 [P] [US4] [TDD-GREEN] Implementar el módulo puro de orquestación `persistUnifiedDishDraft` sobre `dishApi` existente con retorno de `persistedDishId` ante fallo parcial conforme a `specs/027-chef-brutalism-redesign/contracts/chef-unified-dish-editor.contract.ts` en `easyeat/frontend/src/utils/chefDishPersistence.ts`
- [x] T027 [P] [US4] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/dish/DishStepper.tsx` (stepper numerado `01`–`04`, `min-h-[48px]`, indicadores de completitud, navegable sin `dishId` previo) y `easyeat/frontend/src/components/dish/BasicIdentityStep.tsx` (2 columnas escritorio, contadores `JetBrains Mono`, validaciones, navegación `"Siguiente"`, preservando prop `initialData`)
- [x] T028 [P] [US4] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/dish/IngredientSelectorModal.tsx` (`BrutalistModal`, tarjetas `BrutalistCard`, buscador, `<select>` accesible `Filtrar por categoría` y paginación) y `easyeat/frontend/src/components/dish/RecipeBOMStep.tsx` (tarjetas `BrutalistCard` claras sin modo oscuro ni tabla, `FeedbackState` de duplicado, validación enteros `'u'`, navegación `"Anterior"` / `"Siguiente"`, preservando prop `initialRecipe`)
- [x] T029 [P] [US4] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/dish/AllergenStep.tsx` (grillas táctiles de tarjetas checkbox Brutalism, bloque destacado `Confirmar sección de alérgenos`, `"Anterior"` / `"Siguiente"`, preservando prop `initialRestrictions`) y `easyeat/frontend/src/components/dish/ReviewSubmitStep.tsx` (checklist Brutalism con salto rápido a pasos incompletos, banner solo lectura, `"Anterior"`, `Guardar borrador` y `Guardar y enviar a revisión`)
- [x] T030 [US4] [TDD-GREEN] Refactorizar `easyeat/frontend/src/pages/ChefDishEditorPage.tsx` centralizando `UnifiedDishDraftState` (alimentando `initialData`, `initialRecipe` e `initialRestrictions` a los pasos para preservar `ChefDishEditorPage.test.tsx`), conectando `persistUnifiedDishDraft` con `navigate(..., { replace: true })` sin sobreescribir el borrador ante fallo parcial, agregando badge `"Cambios sin guardar"` + `beforeunload` + `BrutalistModal` de salida y coordinando la barra móvil fija (`bottom-[68px] md:static`, `pb-40 md:pb-6`) (depende de T026, T027, T028, T029)
- [x] T031 [P] [US4] [TDD-GREEN] Aplicar actualizaciones acotadas autorizadas (`FR-015(c)` / Grill-Me Q4) en las pruebas unitarias de pasos `easyeat/frontend/tests/components/dish/BasicIdentityStep.test.tsx`, `RecipeBOMStep.test.tsx`, `AllergenStep.test.tsx` y `ReviewSubmitStep.test.tsx` preservando el 100 % de las validaciones de dominio (depende de T030)
- [x] T032 [P] [US4] [TDD-GREEN] Aplicar actualizaciones acotadas autorizadas (`FR-015(c)` / Grill-Me Q4) en las pruebas de integración `easyeat/frontend/tests/integration/us1_basic_identity.test.tsx`, `us2_recipe_definition.test.tsx`, `us3_allergen_labeling.test.tsx` y `us4_review_submission.test.tsx` para recorrer el flujo `"Siguiente"` y verificar la persistencia orquestada en Paso 4 (depende de T030)
- [x] T033 [US4] [TDD-GREEN] Ejecutar `npx vitest run tests/chef/dishEditorUnifiedBrutalist.test.tsx tests/pages/ChefDishEditorPage.test.tsx tests/components/dish/ tests/integration/us1_basic_identity.test.tsx tests/integration/us2_recipe_definition.test.tsx tests/integration/us3_allergen_labeling.test.tsx tests/integration/us4_review_submission.test.tsx` confirmando verde total (depende de T031, T032)

**Checkpoint**: US4 completa y verificable de forma independiente (editor guiado unificado listo para commit atómico).

---

## Phase 7: User Story 5 — Catálogo de ingredientes de cocina en tarjetas Brutalism (Priority: P2)
> **GitHub Issue**: [#503](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/503)

**Goal**: Migrar `/cocina/ingredientes` a una grilla responsiva de tarjetas Brutalism (1 columna móvil, 2 tableta, 3 escritorio) sin tablas ni scroll horizontal, resaltando de inmediato los insumos `Out of Stock` con la tríada semántica `danger` (texto + ícono + borde) y manteniendo el aislamiento financiero absoluto (FR-023).

**Independent Test**: Montar `ChefIngredientsPage` con insumos `In Stock` y `Out of Stock` verificando grilla de tarjetas sin `<table>`, enlace de retorno al KDS, búsqueda `"Buscar materia prima..."`, filtro por categoría, énfasis `danger` en `Out of Stock`, paginación y ausencia total de costos, precios o símbolos monetarios.

### Slice TDD 5 — Seam del catálogo de ingredientes de cocina (`FR-009`, `FR-013`)

> **RED FIRST**: Escribir la suite de integración de US5 y confirmar fallo inicial antes de migrar `/cocina/ingredientes`.

- [x] T034 [US5] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/chef/chefIngredientsBrutalist.test.tsx` verificando: (1) enlace táctil `← Volver al Panel KDS de Cocina`, buscador `"Buscar materia prima..."` (`>= 48 px`), filtro por categoría (`Todas`, `Produce`, `Meat`, `Dairy`, `Dry Goods`, `Spices`) y paginación Brutalism; (2) grilla responsiva de `BrutalistCard` (`grid-cols-1 md:grid-cols-2 lg:grid-cols-3`, cero `<table>`) con stock y unidad en `JetBrains Mono` y etiquetas `In Stock` / `Out of Stock`; (3) énfasis inmediato `alert-danger` (borde + `StatusBadge` `danger` + ícono) para ítems `Out of Stock`; (4) `LoadingSkeleton`, `EmptyState` y `FeedbackState` ante error; y (5) ausencia absoluta de costos, precios, márgenes, auditoría o símbolos monetarios (FR-023); ejecutar `npx vitest run tests/chef/chefIngredientsBrutalist.test.tsx` y confirmar fallo inicial
- [x] T035 [P] [US5] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/chef/ChefIngredientTable.tsx` (conservando el símbolo exportado) como grilla responsiva de `BrutalistCard` con stock en `JetBrains Mono`, `StatusBadge` (`In Stock`/`Out of Stock`), énfasis `alert-danger` en `Out of Stock`, `LoadingSkeleton`, `EmptyState` y cero datos financieros
- [x] T036 [US5] [TDD-GREEN] Rediseñar `easyeat/frontend/src/pages/ChefIngredientsPage.tsx` con cabecera Brutalism, enlace de retorno táctil al KDS, buscador y filtro de categoría (`>= 48 px`), `FeedbackState` ante errores y paginación Brutalism (depende de T035)
- [ ] T037 [US5] [TDD-GREEN] Ejecutar `npx vitest run tests/chef/chefIngredientsBrutalist.test.tsx tests/pages/ChefIngredientsPage.test.tsx` confirmando verde total (depende de T036)

**Checkpoint**: US5 completa y verificable de forma independiente (100 % del espacio del Chef migrado a Brutalism).

---

## Phase 8: Polish & Cross-Cutting Concerns
> **GitHub Issue**: [#504](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/504)

**Purpose**: Verificación integral de regresión (`001`–`027`), auditoría estática Zero AI-Slop / Brutalism y de exclusiones constitucionales, validación en navegador real (`360/768/1024/1440 px`) y re-ejecución de `/impeccable critique` en modo **Operate** confirmando la superación del puntaje base (`12/40`).

- [ ] T038 Ejecutar regresión completa de frontend (`npm test -- --run`), chequeo de tipos (`npx tsc --noEmit`) y compilación de producción (`npm run build`) en `easyeat/frontend/` verificando 0 errores y 0 regresiones en `001`–`027`
- [ ] T039 [P] Ejecutar auditoría estática y verificación de exclusiones (`git diff --stat` confirmando 0 modificaciones bajo `easyeat/backend/`, `easyeat/frontend/src/api/`, `easyeat/frontend/src/components/diner/` y `easyeat/frontend/src/components/admin/`; y búsqueda estática confirmando 0 `border-l-4`, 0 `slate-900`/`gray-900`/`zinc-900`, 0 `<table>`, 0 `window.confirm`/`window.alert` y 0 `Inter` en vistas del Chef)
- [ ] T040 Ejecutar validación visual en navegador real a `360px`, `768px`, `1024px` y `1440px` sobre las 5 rutas de `specs/027-chef-brutalism-redesign/contracts/chef-screens-audit.contract.ts` siguiendo `specs/027-chef-brutalism-redesign/quickstart.md`
- [ ] T041 Ejecutar auditoría post-implementación `/impeccable critique` (modo **Operate**) sobre `easyeat/frontend/src/pages/KitchenWorkspacePage.tsx` y superficies del Chef confirmando 0 hallazgos `side-tab` del detector, 0 problemas P0/P1 y mejora verificada respecto de la línea base (`12/40`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; inicia de inmediato.
- **Foundational (Phase 2)**: Depende de Phase 1; **BLOQUEA** todas las User Stories (`US1`–`US5`).
- **User Story 1 (Phase 3 — P1 MVP)**: Depende de Phase 2. Habilita `fullWidth` y `ChefMobileBottomBar` utilizados por `US2` y `US4`.
- **User Story 2 (Phase 4 — P1)**: Depende de Phase 2 y de `US1` (`fullWidth` en `WorkspaceLayout` / `RoleLayoutShell`).
- **User Story 3 (Phase 5 — P1)**: Depende de Phase 2; puede ejecutarse en paralelo con `US2` y `US5`.
- **User Story 4 (Phase 6 — P1)**: Depende de Phase 2 (y coordina altura móvil con `ChefMobileBottomBar` de `US1`).
- **User Story 5 (Phase 7 — P2)**: Depende de Phase 2; independiente de `US2`, `US3` y `US4`.
- **Polish (Phase 8)**: Depende de la finalización de todas las User Stories (`US1`–`US5`).

### Within Each User Story

- Siempre **TDD-RED primero** (escribir pruebas en `tests/chef/` y confirmar fallo) → **TDD-GREEN después** (implementación mínima + ejecución en verde).
- Bloques/componentes independientes marcados con `[P]` se implementan en paralelo antes de la integración en la página correspondiente.
- Cada User Story culmina en un checkpoint independiente apto para commit atómico dentro de `feat/027-chef-brutalism-redesign`.

---

## Parallel Examples

### Foundational (Phase 2)
```text
Tras confirmar RED en T003:
- T004 [P] BrutalistCard.tsx
- T005 [P] BrutalistModal.tsx
- T006 [P] DishStatusBadge.tsx
- T007 [P] DishRejectionFeedbackBanner.tsx
→ Luego T008 verifica todo en verde.
```

### User Story 2 — KDS (Phase 4)
```text
Tras confirmar RED en T014:
- T015 [P] [US2] KitchenTicketDelayControl.tsx
- T016 [P] [US2] KdsQueuedCard.tsx + KdsInPreparationCard.tsx
→ Luego T017 (KitchenKanbanBoard) → T018 (KitchenWorkspacePage) → T019 (tests acotados) → T020 (verde).
```

### User Story 4 — Editor unificado (Phase 6)
```text
Tras confirmar RED en T025:
- T026 [P] [US4] chefDishPersistence.ts
- T027 [P] [US4] DishStepper.tsx + BasicIdentityStep.tsx
- T028 [P] [US4] IngredientSelectorModal.tsx + RecipeBOMStep.tsx
- T029 [P] [US4] AllergenStep.tsx + ReviewSubmitStep.tsx
→ Luego T030 (ChefDishEditorPage) → T031 [P] y T032 [P] (tests Spec 005 acotados) → T033 (verde).
```

---

## Implementation Strategy

### MVP First (Phase 1 → Phase 2 → US1)
1. Completar Phase 1 (Setup) y Phase 2 (Foundational Brutalism).
2. Completar Phase 3 (US1 — Navegación táctil de un toque y extensión del shell del Chef).
3. Validar el checkpoint independiente de US1 y registrar commit atómico.

### Incremental Delivery (US2 → US3 → US4 → US5 → Polish)
1. Entregar **US2** (KDS a pantalla completa — mayor impacto operativo en cocina caótica) con commit atómico.
2. Entregar **US3** (`/cocina/platos` con chips, tarjetas y pausa `>= 56 px`) con commit atómico.
3. Entregar **US4** (Editor guiado unificado de fichas técnicas) con commit atómico.
4. Entregar **US5** (`/cocina/ingredientes` en tarjetas Brutalism) con commit atómico.
5. Ejecutar **Phase 8** (regresión completa, auditoría estática/diff, navegador real `360/768/1024/1440 px` y re-critique `/impeccable critique`).

---

## Notes

- **Cero cambios en backend**: prohibido modificar cualquier archivo bajo `easyeat/backend/` o `easyeat/frontend/src/api/`.
- **Cero cambios fuera de rol**: prohibido modificar `src/components/diner/`, `src/components/admin/` o pantallas de Comensal, Encargado del Mostrador y Administrador.
- **TDD estricto**: ninguna tarea `[TDD-GREEN]` comienza sin haber ejecutado y visto fallar su tarea `[TDD-RED]` correspondiente.
- **Trazabilidad de pruebas heredadas**: solo se modifican las aserciones acotadas explícitamente autorizadas en `specs/027-chef-brutalism-redesign/contracts/chef-screens-audit.contract.ts` (`FR-015`).
