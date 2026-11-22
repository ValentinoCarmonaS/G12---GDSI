# Tasks: Rediseño ejecutivo Glassmorphism del espacio del Administrador

**Input**: Documentos de diseño en `specs/026-admin-glassmorphism-redesign/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`) y auditoría base Impeccable `/critique` sobre `easyeat/frontend/src/pages/AdminWorkspacePage.tsx` (`14/40`, P0 errores silenciosos/sin carga, P1 incumplimiento Glassmorphism + `ai-color-palette` en L74/L96, P1 jerarquía plana/jerga técnica, P2 brechas de foco y `aria-live`).

**Prerequisites**: `plan.md` (aprobado), `spec.md` (`US1`–`US5`), `research.md`, `data-model.md`, `contracts/glassmorphism-ui-blocks.contract.ts`, `contracts/admin-navigation-bimodal.contract.ts`, `contracts/admin-screens-audit.contract.ts`, `quickstart.md`.

**Tests (TDD obligatorio)**: Todas las fases funcionales siguen ciclos verticales **TDD-RED (escribir prueba y verificar que falla primero) → TDD-GREEN (implementación mínima que hace pasar la prueba)** en `easyeat/frontend/tests/admin/`, seguidos de la verificación de regresión sobre la suite heredada (`001`–`025`). **El backend (`easyeat/backend/`), los servicios Axios (`easyeat/frontend/src/api/`) y los archivos de Spec `025` (`easyeat/frontend/src/components/diner/`) NO se modifican** (excepción `single-tier frontend-only` autorizada bajo Principio X).

**Organization**: Tareas atómicas ordenadas por dependencias y agrupadas por User Story (`US1` a `US5`) para permitir implementación, verificación y commits atómicos por historia dentro de `feat/026-admin-glassmorphism-redesign`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Ejecutable en paralelo (archivos distintos, sin dependencia sobre tareas incompletas).
- **[Story]**: User Story a la que pertenece la tarea (`[US1]` a `[US5]`). Las fases de Setup, Foundational y Polish no llevan etiqueta de historia.
- Todas las tareas incluyen rutas exactas de archivo dentro del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)
> **GitHub Issue**: [#480](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/480)

**Purpose**: Verificar la línea base de la rama `feat/026-admin-glassmorphism-redesign` y preparar el directorio de pruebas trazables de Spec `026` sin modificar dependencias ni backend.

- [x] T001 Verificar estado limpio de compilación y suite base (`npx tsc --noEmit` y `npx vitest run`) en `easyeat/frontend/` antes de iniciar cambios de Spec `026`
- [x] T002 [P] Crear directorio de pruebas trazables `easyeat/frontend/tests/admin/` para las suites TDD de Spec `026`

---

## Phase 2: Foundational (Blocking Prerequisites)
> **GitHub Issue**: [#481](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/481)

**Purpose**: Crear únicamente los bloques nuevos necesarios para Glassmorphism (`GlassCard`) y el estado ultraligero de platos pendientes sin polling (`AdminPendingDishesContext`), reutilizando `AccessibleModal`, `useAccessibleOverlay`, `AuthCardShell` (desde `src/components/diner/`) y los cinco primitivos base de `024`.

**CRITICAL**: Ninguna User Story puede comenzar hasta completar Phase 2 en verde.

### Slice TDD 0 — Fundaciones Glassmorphism y contexto ultraligero de pendientes (`FR-001`–`FR-004`, `FR-006`)

> **RED FIRST**: Escribir las pruebas unitarias/de contrato y confirmar fallo inicial antes de implementar `GlassCard` y `AdminPendingDishesContext`.

- [x] T003 [TDD-RED] Escribir pruebas unitarias en `easyeat/frontend/tests/admin/glassmorphismBlocksAndNav.test.tsx` (bloque 1: fundaciones) cubriendo: (1) `GlassCard` variantes `elevated`/`subtle`/`interactive`/`accent`, `backdrop-filter: blur(12px)`, borde `1px`, padding/radio `--ee-*`, micro-transiciones `<= 200ms` anuladas bajo `prefers-reduced-motion` y degradación sólida bajo `prefers-reduced-transparency`; y (2) `AdminPendingDishesProvider` realizando exactamente una consulta mínima inicial (`size=1`), manteniendo `pendingCount === null` ante carga o error (sin cero falso), sin polling periódico, y sincronizando `setPendingCount` / `decrementPendingCount` en memoria sin requests extra; ejecutar `npx vitest run tests/admin/glassmorphismBlocksAndNav.test.tsx` y confirmar fallo inicial
- [x] T004 [P] [TDD-GREEN] Implementar `GlassCard` conforme a `specs/026-admin-glassmorphism-redesign/contracts/glassmorphism-ui-blocks.contract.ts` en `easyeat/frontend/src/components/admin/GlassCard.tsx`
- [x] T005 [P] [TDD-GREEN] Implementar `AdminPendingDishesProvider` y hook `useAdminPendingDishes` conforme a `specs/026-admin-glassmorphism-redesign/contracts/admin-navigation-bimodal.contract.ts` (1 consulta mínima al montar, cero polling, sincronización en memoria) en `easyeat/frontend/src/context/AdminPendingDishesContext.tsx`
- [x] T006 [TDD-GREEN] Ejecutar `npx vitest run tests/admin/glassmorphismBlocksAndNav.test.tsx` y confirmar verde total de fundaciones (depende de T004, T005)

**Checkpoint**: Fundaciones Glassmorphism y `AdminPendingDishesContext` listos; las 5 User Stories ya pueden avanzar.

---

## Phase 3: User Story 1 — Navegación ejecutiva Glassmorphism y arquitectura visual del Administrador (Priority: P1) 🎯 MVP
> **GitHub Issue**: [#482](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/482)

**Goal**: Extender el contenedor de navegación exclusivamente para `ADMINISTRADOR` incorporando la barra lateral ejecutiva Glassmorphism fija en escritorio (ocultando el botón hamburguesa en `lg:`), las 6 secciones agrupadas (`Operación`, `Catálogo y equipo`, `Negocio`) y el indicador accesible `pendingCount` junto a `Auditoría` en PC y móvil, sin alterar `COMENSAL`, `ENCARGADO` ni `CHEF`.

**Independent Test**: Montar `RoleLayoutShell` con usuario `ADMINISTRADOR` en escritorio y móvil verificando las 6 secciones agrupadas, marcado `aria-current="page"`, insignia `pendingCount` (visible solo si `> 0`), ausencia de `Métricas`, ocultamiento del botón hamburguesa en `lg:`, cierre de sesión por teclado y cero cambios en los shells de `COMENSAL`, `ENCARGADO` y `CHEF`.

### Slice TDD 1 — Seam de navegación ejecutiva bimodal (`FR-005`, `FR-006`, `FR-017`)

> **RED FIRST**: Ampliar `glassmorphismBlocksAndNav.test.tsx` con los escenarios de US1 y confirmar fallo inicial antes de modificar el shell.

- [ ] T007 [US1] [TDD-RED] Ampliar `easyeat/frontend/tests/admin/glassmorphismBlocksAndNav.test.tsx` verificando: (1) `ROLE_NAVIGATION_CONFIG.ADMINISTRADOR` expone exactamente 6 secciones en 3 grupos (`Operación`, `Catálogo y equipo`, `Negocio`) con `useDesktopSidebar: true` y sin `admin-metricas`; (2) `AdminSidebar` renderiza la barra fija de escritorio con foco visible `--ee-focus-ring`, respeto a `prefers-reduced-motion`, estado activo e insignia `pendingCount` junto a `Auditoría` solo cuando `pendingCount > 0`; (3) `RoleLayoutShell` oculta el botón hamburguesa en `lg:` solo para `ADMINISTRADOR`, endurece la campana de notificaciones administrativas para WCAG 2.2 AA (foco, contraste, teclado), mantiene el indicador de cuenta no navegable (sin enlace a `/perfil`) y deja intactos `COMENSAL`, `ENCARGADO` y `CHEF`; y (4) `RoleHamburgerMenu` muestra `pendingCount` junto a `Auditoría` en móvil con objetivos `>= 44×44 px` y respeto a `prefers-reduced-motion`; ejecutar `npx vitest run tests/admin/glassmorphismBlocksAndNav.test.tsx` y confirmar fallo inicial
- [ ] T008 [P] [US1] [TDD-GREEN] Actualizar `easyeat/frontend/src/components/layout/roleNavigationConfig.ts` agregando `useDesktopSidebar?: boolean` (activo solo en `ADMINISTRADOR`) y retirando `admin-metricas` para consolidar las 6 secciones agrupadas
- [ ] T009 [P] [US1] [TDD-GREEN] Implementar `AdminSidebar` (barra lateral ejecutiva Glassmorphism fija en `lg:flex`, 3 grupos, estado activo, insignia `pendingCount` junto a `Auditoría`, metadatos de cuenta y cierre de sesión) en `easyeat/frontend/src/components/admin/AdminSidebar.tsx`
- [ ] T010 [US1] [TDD-GREEN] Extender `easyeat/frontend/src/components/layout/RoleLayoutShell.tsx` y `easyeat/frontend/src/components/layout/RoleHamburgerMenu.tsx` (y endurecer la campana de notificaciones administrativas para WCAG 2.2 AA e indicador no navegable de cuenta) para integrar `AdminSidebar` en escritorio, ocultar hamburguesa en `lg:` para `ADMINISTRADOR` y exponer la insignia `pendingCount` en ambos modos (depende de T008, T009)
- [ ] T011 [US1] [TDD-GREEN] Actualizar de forma acotada las aserciones de navegación obsoletas (7 → 6 ítems) en `easyeat/frontend/tests/components/WorkspaceLayout.test.tsx` conforme a `FR-017`, y ejecutar `npx vitest run tests/admin/glassmorphismBlocksAndNav.test.tsx tests/components/WorkspaceLayout.test.tsx` confirmando verde total (depende de T010)

**Checkpoint**: US1 completa y verificable de forma independiente (MVP de navegación ejecutiva listo para commit atómico).

---

## Phase 4: User Story 2 — Panel ejecutivo de Salud Financiera y Ranking de Platos en `/admin` (Priority: P1)
> **GitHub Issue**: [#483](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/483)

**Goal**: Convertir `/admin` en el Panel Ejecutivo consolidado (banda superior de KPIs + grilla asimétrica 2 columnas con `FinancialHealthChart` estilizado en `dataViz` / `"Ver tabla"` a la izquierda y `DishRankingTable` en tarjetas `GlassCard` a la derecha), eliminar el hub placeholder y el modo oscuro heredado, redirigir `/admin/analytics` → `/admin` y resolver todos los hallazgos P0/P1/P2 del `/impeccable critique` base (`14/40`).

**Independent Test**: Montar `/admin` y `/admin/analytics` verificando redirección transparente, validación de sesión al montar, cambio de períodos independientes (`Día`/`Semana`/`Mes` y `Histórico global`), alternancia `"Ver tabla"` / `"Ver gráfico"`, paleta `dataViz`, estados de carga (`LoadingSkeleton`), vacío (`EmptyState`) y error (`FeedbackState` con reintento) sin clases `purple-*` ni contenedor `legacy-dark`.

### Slice TDD 2 — Seam del Panel Ejecutivo consolidado y `dataViz` (`FR-007`, `FR-008`, `FR-009`, hallazgos critique P0–P2)

> **RED FIRST**: Escribir la suite de integración de US2 y confirmar fallo inicial antes de migrar `/admin` y sus componentes de analítica.

- [ ] T012 [US2] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/admin/executiveDashboardGlass.test.tsx` verificando: (1) `/admin/analytics` redirige limpiamente hacia `/admin`; (2) `AdminWorkspacePage` valida sesión al montar, envuelve el espacio `/admin/*` en `AdminPendingDishesProvider`, elimina el banner Multi-Tenant/tarjetas placeholder/`text-purple-600`/`legacy-dark` y renderiza la banda KPI superior (`font-ee-mono tabular-nums`) + grilla de 2 columnas en escritorio; (3) `FinancialHealthChart` consume `useRoleTheme().dataViz` y alterna limpiamente entre gráfico y tabla semántica `"Ver tabla"`; (4) `DishRankingTable` renderiza tarjetas `GlassCard` con cifras en `JetBrains Mono` y selector de período independiente; y (5) estados `LoadingSkeleton`, `EmptyState` y `FeedbackState` con reintento ante error; ejecutar `npx vitest run tests/admin/executiveDashboardGlass.test.tsx` y confirmar fallo inicial
- [ ] T013 [P] [US2] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/analytics/FinancialHealthSummary.tsx` y `easyeat/frontend/src/components/analytics/FinancialHealthChart.tsx` usando `GlassCard`, `useRoleTheme().dataViz`, `JetBrains Mono` tabular, primitivos base y modo accesible `"Ver tabla"` / `"Ver gráfico"`
- [ ] T014 [P] [US2] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/analytics/DishRankingTable.tsx` como lista de tarjetas `GlassCard` responsivas con posición, nombre, unidades vendidas en `JetBrains Mono`, `LoadingSkeleton`, `EmptyState` y `FeedbackState`
- [ ] T015 [US2] [TDD-GREEN] Reemplazar el hub placeholder en `easyeat/frontend/src/pages/AdminWorkspacePage.tsx` por el Panel Ejecutivo consolidado (banda KPI superior + grilla `lg:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]` + verificación de sesión al montar) y actualizar `easyeat/frontend/src/routes/AppRoutes.tsx` para montar `AdminPendingDishesProvider` en `/admin/*` y redirigir `/admin/analytics` → `/admin` (depende de T013, T014)
- [ ] T016 [US2] [TDD-GREEN] Actualizar de forma acotada las aserciones obsoletas del antiguo hub placeholder en pruebas heredadas afectadas (conforme a `FR-017`) y ejecutar `npx vitest run tests/admin/executiveDashboardGlass.test.tsx tests/components/AdminLogin.test.tsx` y la suite de analítica de `023` confirmando verde total (depende de T015)

**Checkpoint**: US2 completa y verificable de forma independiente (lista para commit atómico).

---

## Phase 5: User Story 3 — Bandeja de Revisión de Platos y Auditoría Financiera de Costos (Priority: P1)
> **GitHub Issue**: [#484](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/484)

**Goal**: Rediseñar `/admin/dishes` como split-pane Glassmorphism en escritorio y flujo maestro → detalle con `Volver a la bandeja` en móvil, sincronizar `pendingCount` en memoria al aprobar/rechazar/cancelar platos sin requests extra, y migrar `/admin/dishes/:id/cost-audit` a tarjetas `GlassCard`.

**Independent Test**: Montar `/admin/dishes` y `/admin/dishes/:id/cost-audit` verificando split-pane en PC, navegación maestro → detalle preservando pestaña y página en móvil, desglose de Food Cost en tarjetas con `JetBrains Mono`, alertas `warning`/`danger` para insumos sin precio o margen negativo, modales vía `AccessibleModal` y actualización inmediata de `pendingCount`.

### Slice TDD 3 — Seam de Bandeja de Revisión y Auditoría de Costos (`FR-006`, `FR-009`, `FR-010`)

> **RED FIRST**: Escribir la suite de integración de US3 y confirmar fallo inicial antes de migrar los componentes de revisión y costos.

- [ ] T017 [US3] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/admin/dishReviewAndCostAuditGlass.test.tsx` verificando: (1) split-pane Glassmorphism en escritorio y flujo maestro → detalle con botón `Volver a la bandeja` (`>= 44×44 px`) en móvil preservando pestaña, paginación y `selectedDishId` consistente ante cambio de viewport móvil/escritorio; (2) `ReviewInboxList`/`ReviewInboxRow` y `FoodCostBreakdown` en tarjetas `GlassCard` con cifras en `JetBrains Mono` y alertas semánticas WCAG 2.2 AA para insumos sin costo y margen negativo; (3) `PublicationConfirmModal` y `RejectionDialog` migrados a `AccessibleModal` (`Escape`, foco); (4) sincronización inmediata de `pendingCount` en `AdminPendingDishesContext` tras aprobar, rechazar o cancelar; y (5) `DishCostAuditPage` en tarjetas Glassmorphism; ejecutar `npx vitest run tests/admin/dishReviewAndCostAuditGlass.test.tsx` y confirmar fallo inicial
- [ ] T018 [P] [US3] [TDD-GREEN] Rediseñar la bandeja de entrada (`ReviewInboxTabs.tsx`, `ReviewInboxList.tsx`, `ReviewInboxRow.tsx`, `InboxPagination.tsx`) en `easyeat/frontend/src/components/dishreview/` usando `GlassCard`, `StatusBadge`, `LoadingSkeleton`, `EmptyState` y `Button`
- [ ] T019 [P] [US3] [TDD-GREEN] Rediseñar el panel de auditoría y modales (`DishAuditPanel.tsx`, `FoodCostBreakdown.tsx`, `UnpricedIngredientsWarning.tsx`, `MarginPriceCalculator.tsx`, `DishDecisionActions.tsx`, `PublicationConfirmModal.tsx` y `RejectionDialog.tsx` reutilizando `AccessibleModal`) en `easyeat/frontend/src/components/dishreview/`
- [ ] T020 [US3] [TDD-GREEN] Reestructurar `easyeat/frontend/src/pages/AdminDishReviewPage.tsx` (split-pane `lg:grid-cols-[380px_minmax(0,1fr)]` + flujo maestro-detalle móvil con `Volver a la bandeja` + sincronización en memoria de `pendingCount` vía `useAdminPendingDishes`) y rediseñar `easyeat/frontend/src/pages/DishCostAuditPage.tsx` en tarjetas `GlassCard` (depende de T018, T019)
- [ ] T021 [US3] [TDD-GREEN] Ejecutar `npx vitest run tests/admin/dishReviewAndCostAuditGlass.test.tsx` junto a las pruebas heredadas de revisión de platos y auditoría de costos confirmando verde total (depende de T020)

**Checkpoint**: US3 completa y verificable de forma independiente (lista para commit atómico).

---

## Phase 6: User Story 4 — Gestión de Personal e Inventario de Materias Primas en tarjetas Glassmorphism (Priority: P2)
> **GitHub Issue**: [#485](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/485)

**Goal**: Migrar `/admin/staff` y `/admin/inventory` desde tablas tradicionales hacia grillas responsivas de tarjetas `GlassCard` con objetivos táctiles `>= 44×44 px`, señalización redundante de stock crítico y sus 6 modales operativos reutilizando `AccessibleModal`.

**Independent Test**: Montar `StaffManagementPage` e `InventoryManagementPage` verificando búsqueda, filtros (incluido `Solo insumos en stock crítico`), tarjetas sin scroll horizontal en `360px`, cifras en `JetBrains Mono` y operación completa por teclado (`Escape`, foco) de los 6 modales.

### Slice TDD 4 — Seam de Personal e Inventario en tarjetas y modales (`FR-009`, `FR-011`, `FR-012`)

> **RED FIRST**: Escribir la suite de integración de US4 y confirmar fallo inicial antes de migrar Personal e Inventario.

- [ ] T022 [US4] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/admin/staffAndInventoryGlass.test.tsx` verificando: (1) `StaffManagementPage` y `StaffRosterTable` en tarjetas `GlassCard` con `StatusBadge` (`Activo`, `Pendiente de primer login`, `Suspendido`), acciones `>= 44×44 px` y los 3 modales (`StaffFormModal`, `StaffStatusModal` con error de único admin, `TemporaryCredentialsModal` con bloque monoespaciado) vía `AccessibleModal`; y (2) `InventoryManagementPage` e `IngredientCatalogTable` en tarjetas `GlassCard` con filtro `Solo insumos en stock crítico`, señalización redundante (ícono + texto + tríada semántica), cifras en `JetBrains Mono` y los 3 modales (`IngredientFormModal`, `StockAdjustmentModal`, `StockAuditModal` con historial en tarjetas) vía `AccessibleModal`; ejecutar `npx vitest run tests/admin/staffAndInventoryGlass.test.tsx` y confirmar fallo inicial
- [ ] T023 [P] [US4] [TDD-GREEN] Rediseñar `StaffRosterTable.tsx`, `StaffFormModal.tsx`, `StaffStatusModal.tsx`, `TemporaryCredentialsModal.tsx` (en `easyeat/frontend/src/components/staff/`) y `easyeat/frontend/src/pages/StaffManagementPage.tsx` usando `GlassCard`, `AccessibleModal` y primitivos base
- [ ] T024 [P] [US4] [TDD-GREEN] Rediseñar `IngredientCatalogTable.tsx`, `IngredientFormModal.tsx`, `StockAdjustmentModal.tsx`, `StockAuditModal.tsx` (en `easyeat/frontend/src/components/inventory/`) y `easyeat/frontend/src/pages/InventoryManagementPage.tsx` usando `GlassCard`, `AccessibleModal`, `JetBrains Mono` y señalización redundante de stock crítico
- [ ] T025 [US4] [TDD-GREEN] Ejecutar `npx vitest run tests/admin/staffAndInventoryGlass.test.tsx` junto a las pruebas heredadas de personal e inventario confirmando verde total (depende de T023, T024)

**Checkpoint**: US4 completa y verificable de forma independiente (lista para commit atómico).

---

## Phase 7: User Story 5 — Operación del Restaurante, Mi Local y Cambio Obligatorio de Contraseña (Priority: P2)
> **GitHub Issue**: [#486](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/486)

**Goal**: Completar el rediseño Glassmorphism en `/admin/operations` (horarios en tarjetas, cupos preservando `role="row"`, pagos con manejo de 409) y `/admin/mi-local` (2 columnas con aislamiento estricto de marca), y migrar `/first-login-password-change` a `AuthCardShell` polimórfico (`--ee-*`).

**Independent Test**: Montar `AdminOperationsPage`, `AdminBrandingPage` y `FirstLoginPasswordPage` verificando tarjetas de horarios, `role="row"` en cupos, manejo de conflicto 409 en pagos, aislamiento total de colores editados en `/admin/mi-local` y polimorfismo por rol en `/first-login-password-change`.

### Slice TDD 5 — Seam de Operación, Mi Local y Primer Ingreso (`FR-009`, `FR-013`, `FR-014`, `FR-015`)

> **RED FIRST**: Escribir la suite de integración de US5 y confirmar fallo inicial antes de migrar las tres vistas.

- [ ] T026 [US5] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/admin/operationsBrandingFirstLoginGlass.test.tsx` verificando: (1) `AdminOperationsPage` con `WeeklyScheduleMatrix` en tarjetas `GlassCard`, `SlotCapacityGrid` preservando `role="row"` y `PaymentMethodToggle` con manejo accesible de conflicto 409; (2) `AdminBrandingPage` en 2 columnas en escritorio con códigos hex en `JetBrains Mono` y aislamiento estricto de los colores editados dentro de la previsualización; y (3) `FirstLoginPasswordPage` renderizando `AuthCardShell` 100 % `--ee-*` (polimórfica con respaldo Administrador Glassmorphism) y checklist de contraseña en tiempo real WCAG 2.2 AA; ejecutar `npx vitest run tests/admin/operationsBrandingFirstLoginGlass.test.tsx` y confirmar fallo inicial
- [ ] T027 [P] [US5] [TDD-GREEN] Rediseñar `WeeklyScheduleMatrix.tsx` (tarjetas por día), `SlotCapacityGrid.tsx` (superficie Glassmorphism preservando `role="row"`), `PaymentMethodToggle.tsx` (en `easyeat/frontend/src/components/operations/`) y `easyeat/frontend/src/pages/AdminOperationsPage.tsx`
- [ ] T028 [P] [US5] [TDD-GREEN] Rediseñar `ColorPickerInput.tsx` (hex en `JetBrains Mono`), `LogoUploader.tsx` (en `easyeat/frontend/src/components/admin/`) y `easyeat/frontend/src/pages/AdminBrandingPage.tsx` en 2 columnas con aislamiento estricto de marca (Spec `016`/`024`)
- [ ] T029 [P] [US5] [TDD-GREEN] Migrar `easyeat/frontend/src/pages/FirstLoginPasswordPage.tsx` a `AuthCardShell` (reutilizado de `src/components/diner/`) y primitivos base (`--ee-*`) con checklist de requisitos de complejidad WCAG 2.2 AA
- [ ] T030 [US5] [TDD-GREEN] Ejecutar `npx vitest run tests/admin/operationsBrandingFirstLoginGlass.test.tsx` junto a las pruebas heredadas de operaciones, branding y primer login confirmando verde total (depende de T027, T028, T029)

**Checkpoint**: US5 completa y verificable de forma independiente (100 % del ecosistema Administrador migrado a Glassmorphism).

---

## Phase 8: Polish & Cross-Cutting Concerns
> **GitHub Issue**: [#487](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/487)

**Purpose**: Verificación integral de regresión (`001`–`026`), auditoría estática Zero AI-Slop y de exclusiones constitucionales, validación en navegador real (`360/768/1440 px`) y auditoría post-implementación `/impeccable critique` en modo **Operate**.

- [ ] T031 Ejecutar regresión completa de frontend (`npx vitest run`), chequeo de tipos (`npx tsc --noEmit`) y compilación de producción (`npm run build`) en `easyeat/frontend/` verificando 0 errores y 0 regresiones en `001`–`026`
- [ ] T032 [P] Verificar cero uso de `Inter`, cero `text-purple-*`/`bg-purple-*` en rutas de Administrador, cero `legacy-dark` activo en `/admin/*`, cero emojis literales en alertas administrativas y cero modificaciones bajo `easyeat/backend/`, `easyeat/frontend/src/api/` y `easyeat/frontend/src/components/diner/` mediante auditoría estática y `git diff --stat`
- [ ] T033 Ejecutar validación visual en navegador real a `360px`, `768px` y `1440px` sobre las pantallas definidas en `specs/026-admin-glassmorphism-redesign/contracts/admin-screens-audit.contract.ts` siguiendo `specs/026-admin-glassmorphism-redesign/quickstart.md`
- [ ] T034 Ejecutar auditoría crítica post-implementación `/impeccable critique` sobre `AdminWorkspacePage.tsx` (línea base `14/40`) y las vistas clave de `US3`–`US5` contra `.specify/memory/designs/admin.DESIGN.md` en modo **Operate**, verificando mejora sustancial y cero hallazgos bloqueantes P0/P1 (aplicando `/impeccable polish` solo si surgiera algún ajuste menor y re-verificando tests)

---

## Dependencies & Execution Order

- **Phase 1 (Setup)** → **Phase 2 (Foundational)** → desbloquea **US1** (Phase 3).
- **US1 (Phase 3)** → desbloquea **US2** (Phase 4), **US3** (Phase 5), **US4** (Phase 6) y **US5** (Phase 7), que pueden ejecutarse en paralelo o secuencialmente por prioridad (`P1` → `P2`).
- **Phase 8 (Polish)** requiere `US1`–`US5` completadas.

## Parallel Execution Examples

- **Phase 2**: `T004` (`GlassCard.tsx`) y `T005` (`AdminPendingDishesContext.tsx`) en paralelo tras `T003` (RED).
- **US1**: `T008` (`roleNavigationConfig.ts`) y `T009` (`AdminSidebar.tsx`) en paralelo tras `T007` (RED).
- **US2**: `T013` (`FinancialHealth*`) y `T014` (`DishRankingTable.tsx`) en paralelo tras `T012` (RED).
- **US3**: `T018` (bandeja) y `T019` (panel/modales) en paralelo tras `T017` (RED).
- **US4**: `T023` (Personal) y `T024` (Inventario) en paralelo tras `T022` (RED).
- **US5**: `T027` (Operaciones), `T028` (Mi Local) y `T029` (Primer Ingreso) en paralelo tras `T026` (RED).

## Implementation Strategy

- **MVP primero**: Completar Phase 1 → Phase 2 → US1 (navegación bimodal Glassmorphism) y US2 (Panel Ejecutivo consolidado en `/admin`), cerrando los hallazgos P0/P1 del `/critique` base.
- **Entrega incremental**: Un commit atómico por User Story (`US1`–`US5`) dentro de `feat/026-admin-glassmorphism-redesign`, siempre validando TDD-RED → TDD-GREEN antes de avanzar.
