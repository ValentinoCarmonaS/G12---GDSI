# Tasks: Umbral de Stock Crítico en Inventario y Pausa Manual de Platos por Chef

**Input**: Design documents from `specs/013-critical-stock-chef-pause/`

**Branch base**: `feat/012/phase-6-polish` → branch de trabajo: `013-critical-stock-chef-pause`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**TDD Order (por US)**: Tests unitarios + edge cases → Implementación backend → Tests frontend → Implementación frontend

**Organization**: Tareas agrupadas por User Story para implementación y verificación independiente.

## Formato: `[ID] [P?] [Story] Descripción con ruta exacta`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: User Story a la que pertenece ([US1], [US2], [US3])
- Rutas relativas a la raíz del repositorio

---

## Phase 1: Setup (Infraestructura compartida)

**Purpose**: Inicialización del branch de trabajo y estructura de proyecto para la feature 013.

- [ ] T001 Crear el branch de trabajo `013-critical-stock-chef-pause` a partir de `feat/012/phase-6-polish` con `git checkout -b 013-critical-stock-chef-pause`
- [ ] T002 Verificar que la migración Flyway predecesora `V10__support_admin_dish_cancellation.sql` existe en `easyeat/backend/src/main/resources/db/migration/` y que la secuencia no tiene huecos
- [ ] T003 Crear el archivo de migración Flyway `easyeat/backend/src/main/resources/db/migration/V11__critical_stock_and_chef_pause.sql` con el DDL completo definido en `data-model.md §3.1`: columna `ingredients.critical_stock_threshold INTEGER NULL CHECK (IS NULL OR >= 0)`, columna `dishes.paused_by_chef BOOLEAN NOT NULL DEFAULT FALSE`, e índices `idx_ingredients_critical_alert` e `idx_dishes_catalog_visibility`

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)

**Purpose**: Extensiones de entidades JPA y tipos TypeScript que son base para todas las User Stories. Debe completarse antes de iniciar cualquier fase US.

**⚠️ CRÍTICO**: Ninguna fase US puede comenzar hasta que esta fase esté completa.

- [ ] T004 Extender la entidad JPA `easyeat/backend/src/main/java/com/easyeat/inventory/entity/Ingredient.java` añadiendo el campo `private Integer criticalStockThreshold` con `@Column(name = "critical_stock_threshold")` y el método `@Transient public boolean isCritical()` según la fórmula `isCritical = status == ACTIVE && threshold != null && currentStock < threshold` (data-model.md §4.1)
- [ ] T005 Extender la entidad JPA `easyeat/backend/src/main/java/com/easyeat/catalog/domain/Dish.java` añadiendo el campo `private Boolean pausedByChef = false` con `@Column(name = "paused_by_chef", nullable = false)` y los métodos `getPausedByChef()` / `setPausedByChef(Boolean)` (data-model.md §4.2)
- [ ] T006 [P] Extender el tipo TypeScript `easyeat/frontend/src/types/inventory.ts` añadiendo los campos `criticalStockThreshold?: number | null` e `isCritical?: boolean` en la interfaz de ingrediente
- [ ] T007 [P] Extender el tipo TypeScript `easyeat/frontend/src/types/dish.ts` añadiendo el campo `pausedByChef: boolean` en la interfaz de plato y definir la interfaz `ChefDishOperationalStatus` con los campos `dishId`, `pausedByChef`, `sinStock`, `isActive` y `updatedAt`

**Checkpoint**: Migración V11 creada, entidades JPA extendidas, tipos TypeScript actualizados — las fases US pueden comenzar.

---

## Phase 3: User Story 1 — Definir umbral de stock crítico individual (Priority: P1) 🎯 MVP

**Goal**: El administrador puede asignar y modificar un umbral numérico entero `>= 0` individual por ingrediente en el módulo de inventario. Chef queda bloqueado. (ADM-4.1.3, FR-001..FR-003, FR-017, INV-1, INV-2)

**Independent Test**: Autenticarse con rol Admin → abrir formulario de creación/edición de ingrediente → ingresar valor entero `>= 0` en "Stock mínimo/crítico" → guardar → verificar que el umbral queda persistido como atributo del insumo. Intentar lo mismo con rol Chef → debe recibir HTTP 403.

### Tests — User Story 1 (escribir PRIMERO, deben FALLAR antes de implementar)

- [ ] T008 [P] [US1] Escribir test unitario `easyeat/backend/src/test/java/com/easyeat/inventory/entity/IngredientCriticalStockTest.java`: casos `testIndividualThreshold` (umbral independiente por insumo, INV-1), `testStrictlyLessThanThreshold` (stock == umbral → isCritical false, INV-3), `testNullThresholdEmitsNoAlert` (threshold null → isCritical false, INV-7), `testZeroThreshold` (umbral 0 → isCritical solo si stock < 0, EC-001), `testZeroStockWithPositiveThreshold` (stock 0 con umbral > 0 → crítico, EC-004), `testDiscontinuedSuspendsAlert` (DISCONTINUED → isCritical false, INV-17), `testIngredientWithoutRecipesAlerts` (insumo sin recetas con stock < umbral → crítico, EC-009), `testArbitrarilyHighThresholdAccepted` (umbral muy alto aceptado, EC-022)
- [ ] T009 [P] [US1] Escribir test de validación `easyeat/backend/src/test/java/com/easyeat/inventory/validation/IngredientValidationTest.java`: casos `testNegativeAndDecimalRejection` (negativo, decimal, texto → 400 Bad Request, INV-2, FR-002, SC-002) y `testArbitrarilyHighThresholdAccepted` (entero alto válido, EC-022)
- [ ] T010 [P] [US1] Escribir test de seguridad `easyeat/backend/src/test/java/com/easyeat/inventory/security/InventorySecurityTest.java`: caso `testChefCannotSetThreshold` (rol Chef → HTTP 403 al configurar umbral, INV-16, FR-017, SC-009, EC-018)
- [ ] T011 [P] [US1] Escribir test de creación con umbral `easyeat/backend/src/test/java/com/easyeat/inventory/integration/IngredientCreationTest.java`: caso `testWithValidThreshold` (admin crea ingrediente con umbral válido → 201 con umbral persistido, US1 AC-1.1)
- [ ] T012 [P] [US1] Escribir test de edición con umbral `easyeat/backend/src/test/java/com/easyeat/inventory/integration/IngredientUpdateTest.java`: caso `testIndividualThresholdIsolation` (admin edita umbral de un ingrediente sin afectar otros, US1 AC-1.2)
- [ ] T013 [P] [US1] Escribir test de componente RTL `easyeat/frontend/tests/components/IngredientFormModal.test.tsx`: casos de renderizado del campo "Stock mínimo/crítico", rechazo de negativos y no enteros en cliente, aceptación de enteros `>= 0`

### Implementación — User Story 1

- [ ] T014 [US1] Extender el DTO `easyeat/backend/src/main/java/com/easyeat/inventory/dto/CreateIngredientRequest.java` añadiendo el campo `@Min(0) Integer criticalStockThreshold` con validación Jakarta `@Min(0)` y mensaje de error explícito (FR-001, FR-002, INV-2)
- [ ] T015 [US1] Extender el DTO `easyeat/backend/src/main/java/com/easyeat/inventory/dto/UpdateIngredientRequest.java` añadiendo el campo `@Min(0) Integer criticalStockThreshold` con la misma validación (FR-001, FR-002)
- [ ] T016 [US1] Extender el DTO `easyeat/backend/src/main/java/com/easyeat/inventory/dto/AdminIngredientDetailResponse.java` añadiendo los campos `Integer criticalStockThreshold` y `boolean isCritical` para exponer el umbral y el estado calculado al frontend (FR-004, D-002)
- [ ] T017 [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/inventory/service/IngredientService.java` para leer y persistir `criticalStockThreshold` al crear y actualizar ingredientes, y para mapear `isCritical()` desde la entidad al DTO de respuesta (FR-001, FR-003, FR-010, INV-14)
- [ ] T018 [US1] Verificar que `easyeat/backend/src/main/java/com/easyeat/inventory/controller/AdminIngredientController.java` tiene `@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")` en los endpoints de creación y edición de ingredientes; ajustar si falta (FR-017, INV-16)
- [ ] T019 [US1] Extender el campo "Stock mínimo/crítico" en el componente `easyeat/frontend/src/components/inventory/IngredientFormModal.tsx`: añadir input numérico entero `>= 0`, validación en cliente con mensaje de error, y vincular al campo `criticalStockThreshold` del formulario (FR-001, FR-002, INV-2)
- [ ] T020 [US1] Actualizar `easyeat/frontend/src/api/inventoryApi.ts` para incluir `criticalStockThreshold` en los payloads de creación y edición de ingredientes y en el tipo de respuesta (FR-001, FR-003)

**Checkpoint**: US1 completamente funcional — el administrador puede definir y editar umbrales individuales; el Chef recibe 403; validaciones rechazan valores inválidos.

---

## Phase 4: User Story 2 — Visualizar alertas de reabastecimiento en inventario (Priority: P1)

**Goal**: Al consultar el módulo de inventario, el administrador ve resaltados visualmente (con insignia "⚠️ Stock crítico") todos los ingredientes cuya cantidad disponible sea estrictamente menor a su umbral crítico. Incluye filtro rápido `criticalOnly`. La alerta es exclusivamente visual (cero correos/SMS/push). (ADM-4.1.4, FR-004..FR-010, FR-020, FR-021, INV-3..INV-7, INV-14, INV-17)

**Independent Test**: Ajustar la cantidad de un insumo por debajo de su umbral → acceder al módulo de inventario con rol Admin → verificar insignia de alerta visual; activar filtro "Solo insumos críticos" → verificar que solo aparecen los ingredientes críticos; reabastecer el insumo por encima del umbral → verificar que la insignia desaparece.

### Tests — User Story 2 (escribir PRIMERO, deben FALLAR antes de implementar)

- [ ] T021 [P] [US2] Escribir test de integración con Testcontainers `easyeat/backend/src/test/java/com/easyeat/inventory/integration/IngredientRepositoryCriticalFilterTest.java`: casos `testCriticalOnly` (parámetro `criticalOnly=true` retorna solo ingredientes con `current_stock < threshold`, US2 AC-2.6, FR-020, SC-015), `testDiscontinuedExcludedFromCriticalFilter` (insumos DISCONTINUED excluidos, INV-17, FR-021), `testNullThresholdExcludedFromFilter` (insumos sin umbral excluidos, INV-7, EC-002)
- [ ] T022 [P] [US2] Escribir test de integración `easyeat/backend/src/test/java/com/easyeat/inventory/integration/IngredientAlertIntegrationTest.java`: casos `testSaleTriggersAlert` (venta reduce stock → `isCritical=true`, EC-005), `testAdjustmentTriggersAlert` (ajuste manual → `isCritical=true`, EC-006), `testReplenishmentRemovesAlert` (reposición `>= umbral` → `isCritical=false`, EC-007), `testHigherThresholdTriggersAlert` (umbral > stock actual al guardar → crítico inmediato, EC-003), `testThresholdUpdateRecalculatesImmediately` (modificar umbral recalcula en siguiente consulta, INV-14)
- [ ] T023 [P] [US2] Escribir test de lógica de negocio `easyeat/backend/src/test/java/com/easyeat/inventory/service/IngredientServiceTest.java`: caso `testThresholdUpdateRecalculatesImmediately` (INV-14) y `testCriticalStockDoesNotBlockSale` (INV-6, SC-011, EC-010)
- [ ] T024 [P] [US2] Escribir test de notificaciones `easyeat/backend/src/test/java/com/easyeat/inventory/integration/InventoryNotificationTest.java`: caso `testNoExternalNotificationsEmitted` (estado crítico no dispara correos, SMS, push ni órdenes, INV-5, FR-008, SC-005)
- [ ] T025 [P] [US2] Escribir test de disponibilidad del catálogo `easyeat/backend/src/test/java/com/easyeat/catalog/service/DishAvailabilityEngineTest.java`: caso `testCriticalStockDoesNotBlockSale` (insumo crítico con stock > 0 no bloquea venta del plato, INV-6, FR-009, SC-011, EC-008)
- [ ] T026 [P] [US2] Escribir test de catálogo del comensal `easyeat/backend/src/test/java/com/easyeat/catalog/service/DinerCatalogServiceTest.java`: caso `testThresholdChangeDoesNotAffectDiner` (modificar umbral no altera visibilidad del catálogo, EC-010, FR-009)
- [ ] T027 [P] [US2] Escribir test de componente RTL `easyeat/frontend/tests/components/IngredientCatalogTable.test.tsx`: casos `testHighlightCriticalIngredients` (insignia "⚠️ Stock crítico" visible cuando `isCritical=true`, INV-4), `testMultipleAlertsRendered` (múltiples críticos resaltan simultáneamente, EC-020, US2 AC-2.5), `testNoAlertWhenAboveThreshold` (stock >= umbral → sin insignia, FR-006), `testDiscontinuedNoAlert` (DISCONTINUED sin alerta, INV-17), `testCriticalOnlyFilterToggles` (filtro rápido muestra solo críticos, FR-020, US2 AC-2.6)

### Implementación — User Story 2

- [ ] T028 [US2] Extender `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java` añadiendo la consulta JPQL o query nativa con soporte de parámetro `criticalOnly`: filtra `status = 'ACTIVE' AND critical_stock_threshold IS NOT NULL AND current_stock < critical_stock_threshold` apoyada en el índice `idx_ingredients_critical_alert` de la migración V11 (FR-020, D-003)
- [ ] T029 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/inventory/service/IngredientService.java` para soportar el parámetro `criticalOnly` en el método de listado, delegando al repositorio extendido en T028, y para calcular el campo `isCritical` en el DTO de respuesta considerando la suspensión de DISCONTINUED (FR-004..FR-007, FR-010, FR-021, INV-3, INV-7, INV-14, INV-17)
- [ ] T030 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/inventory/controller/AdminIngredientController.java` para aceptar el query param `criticalOnly=true` en el endpoint `GET /api/v1/admin/ingredients` y pasarlo al servicio (FR-020, D-003)
- [ ] T031 [US2] Extender la insignia visual de alerta en `easyeat/frontend/src/components/inventory/IngredientCatalogTable.tsx`: renderizar el badge "⚠️ Stock crítico" (color ámbar/rojo) condicionalmente cuando `isCritical === true`, e incorporar el toggle de filtro rápido "Solo insumos en stock crítico" en la barra de herramientas (FR-005, FR-020, INV-4, D-003)
- [ ] T032 [US2] Actualizar `easyeat/frontend/src/api/inventoryApi.ts` para aceptar y enviar el parámetro `criticalOnly?: boolean` en la función de listado de ingredientes (FR-020)
- [ ] T033 [US2] Integrar el filtro rápido en `easyeat/frontend/src/pages/InventoryManagementPage.tsx`: gestionar el estado `criticalOnly` local y pasarlo al componente `IngredientCatalogTable` y a la llamada de API (FR-020, SC-015)

**Checkpoint**: US2 completamente funcional — alertas visuales correctas, filtro rápido operativo, cero efectos externos.

---

## Phase 5: User Story 3 — Pausa manual de platos por el chef durante el servicio (Priority: P1)

**Goal**: El chef puede pausar/despausar individualmente cada plato mediante un toggle en su panel de cocina. Los platos pausados se ocultan del catálogo del comensal en `<= 3 s`. El carrito preserva platos pausados con advertencia y bloqueo de checkout. El modal de detalle abierto se deshabilita reactivamente sin cerrarse. El toggle es siempre operable por el chef (incluso con plato sin stock o desactivado). Las tres causas de indisponibilidad son independientes. (CHF-2.2.5, FR-011..FR-016, FR-018..FR-019, FR-022..FR-023, INV-8..INV-13, INV-15..INV-16, INV-18..INV-19)

**Independent Test**: Autenticarse como Chef → seleccionar plato activo → accionar toggle a "pausado" → verificar que el plato desaparece del catálogo del comensal; accionar toggle a "activo" → plato reaparece (si no hay otras causas de indisponibilidad); pausar plato ya en carrito → verificar advertencia "Pausado por cocina" y bloqueo de checkout.

### Tests — User Story 3 (escribir PRIMERO, deben FALLAR antes de implementar)

- [ ] T034 [P] [US3] Escribir test unitario de dominio `easyeat/backend/src/test/java/com/easyeat/dish/service/DishChefPauseTest.java`: casos `testToggleAlwaysOperable` (toggle funciona con plato sin stock, EC-011, INV-19), `testToggleOperableWhenAdminDeactivated` (toggle funciona con plato desactivado por admin, EC-012, INV-19), `testToggleDoesNotMutateStockOrPrice` (pausa no altera stock, receta ni precio, INV-11, US3 AC-3.3), `testUnpauseRestoresVisibility` (despausar restaura visibilidad salvo otras causas, INV-9)
- [ ] T035 [P] [US3] Escribir test de las tres causas de indisponibilidad `easyeat/backend/src/test/java/com/easyeat/dish/service/DishThreeTierAvailabilityTest.java`: casos `testDistinctCauses` (tres causas no colapsan en flag único, INV-10, SC-014), `testCoexistenceSinStockAndPause` (plato pausado + sin stock → despausar exitoso pero no comprable, EC-011, INV-12, US3 AC-3.4), `testCoexistenceAdminDeactivatedAndPause` (pausa + desactivación admin coexisten, EC-012), `testAdminReactivationKeepsChefPause` (reactivar admin no despausa chef, EC-013), `testUnpauseOutOfStockRendersAgotado` (despausar plato sin stock → muestra "Agotado", EC-014), `testAdminDeactivationDoesNotBlockChefToggle` (desactivación admin no bloquea toggle, INV-13)
- [ ] T036 [P] [US3] Escribir test de seguridad en `easyeat/backend/src/test/java/com/easyeat/inventory/security/InventorySecurityTest.java` (o clase dedicada): casos `testAdminCannotOperateChefToggle` (rol Admin → HTTP 403 al PATCH chef-pause, INV-16, FR-018, SC-010, EC-019) y `testChefCannotSetThreshold` si aún no existe (EC-018)
- [ ] T037 [P] [US3] Escribir test de integración con Testcontainers `easyeat/backend/src/test/java/com/easyeat/dish/integration/ChefDishPauseIntegrationTest.java`: casos `testPauseHidesFromDiner` (toggle ON → plato ausente en catálogo comensal, US3 AC-3.1), `testUnpauseRestoresToDiner` (toggle OFF → plato reaparece si no hay otras causas, US3 AC-3.2), `testMultipleDishesPaused` (múltiples platos pausados se ocultan individualmente, EC-021)
- [ ] T038 [P] [US3] Escribir test de concurrencia `easyeat/backend/src/test/java/com/easyeat/dish/integration/ChefPauseConcurrenceIntegrationTest.java`: casos `testRapidToggle` (alternancia rápida → estado final = último valor confirmado, EC-015), `testMultiChefConcurrence` (dos chefs alternan toggle casi simultáneamente → último prevalece, EC-025, INV-18)
- [ ] T039 [P] [US3] Escribir test de polling ETag `easyeat/backend/src/test/java/com/easyeat/catalog/integration/DinerCatalogAvailabilityPollingTest.java`: caso `testPropagationLatency` (mutación de pausa cambia ETag → 200 en siguiente polling, 304 cuando no hay cambio, INV-15)
- [ ] T040 [P] [US3] Escribir test de componente RTL `easyeat/frontend/tests/components/ChefDishPauseToggle.test.tsx` (nuevo componente): renderizado del toggle, estado optimista al conmutar, ausencia en vistas de administrador (INV-16, SC-010)
- [ ] T041 [P] [US3] Escribir test de página RTL `easyeat/frontend/tests/pages/ChefDishesPage.test.tsx`: casos `testToggleRenderedPerDish` (toggle por plato en tabla), `testIndividualTogglesOnly` (sin acciones masivas por categoría, EC-016), `testOperationalStatusShown` (estado operativo visible en columna)
- [ ] T042 [P] [US3] Escribir test de hook `easyeat/frontend/tests/hooks/useChefDishesSync.test.ts`: polling cada 2500 ms con ETag, actualización de estado en cocina al cambiar ETag (D-006, INV-18)
- [ ] T043 [P] [US3] Escribir test de modal de detalle RTL `easyeat/frontend/tests/components/DishDetailModal.test.tsx`: caso `testReactivePauseWithoutClosing` (pausa llega al modal abierto → deshabilita compra sin cerrar panel, EC-017, FR-016, INV-15)
- [ ] T044 [P] [US3] Escribir test de carrito RTL `easyeat/frontend/tests/components/CartDrawer.test.tsx`: caso `testHoldAndLockoutOnChefPause` (plato pausado en carrito → aviso "Pausado por cocina", botón `+` inhabilitado, checkout bloqueado, FR-019, EC-023)
- [ ] T045 [P] [US3] Escribir test de hook de disponibilidad RTL `easyeat/frontend/tests/hooks/useRealtimeAvailability.test.ts`: propagación de pausa del chef al catálogo del comensal (INV-15, D-006)

### Implementación — User Story 3

#### Backend

- [ ] T046 [US3] Crear el DTO `easyeat/backend/src/main/java/com/easyeat/dish/dto/ToggleChefPauseRequest.java` con el campo `boolean paused` (payload del endpoint PATCH, D-005)
- [ ] T047 [US3] Crear el DTO `easyeat/backend/src/main/java/com/easyeat/dish/dto/DishChefPauseResponse.java` con los campos `UUID dishId`, `boolean pausedByChef`, `Instant updatedAt` (respuesta del endpoint PATCH, plan.md §5.2)
- [ ] T048 [US3] Crear el DTO `easyeat/backend/src/main/java/com/easyeat/dish/dto/ChefDishesOperationalStatusResponse.java` con la lista de items que incluye `dishId`, `pausedByChef`, `sinStock`, `isActive` (respuesta de GET operational-status con ETag, D-004, INV-10)
- [ ] T049 [US3] Crear el DTO `easyeat/backend/src/main/java/com/easyeat/catalog/dto/DishAvailabilityDetailsResponse.java` con los campos `dishId`, `sinStock`, `pausedByChef`, `desactivacionAdmin` para exponer las tres causas de forma separada (INV-10, FR-015, D-004)
- [ ] T050 [US3] Actualizar `easyeat/backend/src/main/java/com/easyeat/catalog/repository/DishRepository.java` para incluir `paused_by_chef` en las consultas de catálogo del comensal: platos visibles = `status=PUBLISHED AND is_active=true AND paused_by_chef=false` apoyado en el índice `idx_dishes_catalog_visibility` (INV-8, FR-012)
- [ ] T051 [US3] Actualizar `easyeat/backend/src/main/java/com/easyeat/catalog/service/DinerCatalogService.java` para excluir platos con `paused_by_chef=true` del catálogo del comensal (INV-8, FR-012)
- [ ] T052 [US3] Actualizar `easyeat/backend/src/main/java/com/easyeat/catalog/service/DishAvailabilityEngine.java` para incluir `pausedByChef` en el cálculo del ETag SHA-256 y en la evaluación de disponibilidad del catálogo (INV-15, D-006)
- [ ] T053 [US3] Implementar el método `toggleChefPause(UUID dishId, boolean paused, UUID tenantId)` en `easyeat/backend/src/main/java/com/easyeat/dish/service/DishService.java`: persistir `paused_by_chef`, verificar tenant, sin validar stock ni estado admin (INV-19, FR-023, D-005)
- [ ] T054 [US3] Implementar el método `getOperationalStatus(UUID tenantId)` en `easyeat/backend/src/main/java/com/easyeat/dish/service/DishService.java`: retornar lista de platos con sus tres causas de indisponibilidad desacopladas y el ETag calculado (D-004, D-006, INV-10)
- [ ] T055 [US3] Extender `easyeat/backend/src/main/java/com/easyeat/dish/controller/DishController.java` añadiendo el endpoint `PATCH /api/v1/dishes/{id}/chef-pause` con `@PreAuthorize("hasRole('CHEF')")` y el endpoint `GET /api/v1/dishes/operational-status` con soporte de ETag (`If-None-Match` → 304 si no hay cambios) (FR-011, FR-022, INV-16, FR-018, D-005, D-006)

#### Frontend

- [ ] T056 [US3] Crear el componente `easyeat/frontend/src/components/chef/ChefDishPauseToggle.tsx`: control de alternancia (toggle) interactivo con actualización optimista del estado visual, llamada a `dishApi.toggleChefPause`, manejo de error con rollback (FR-011, D-010)
- [ ] T057 [US3] Crear el hook `easyeat/frontend/src/hooks/useChefDishesSync.ts`: polling condicional cada 2500 ms con ETag hacia `GET /api/v1/dishes/operational-status`, actualización de estado de los platos en cocina cuando el ETag cambia (FR-022, INV-18, D-006)
- [ ] T058 [US3] Extender `easyeat/frontend/src/api/dishApi.ts` añadiendo las funciones `toggleChefPause(dishId, paused)` (PATCH) y `getOperationalStatus()` (GET con `If-None-Match`) (plan.md §5.2)
- [ ] T059 [US3] Extender `easyeat/frontend/src/pages/ChefDishesPage.tsx` para incorporar la columna de estado operativo (pausado/activo/sin stock/desactivado) y el componente `ChefDishPauseToggle` por fila, integrado con `useChefDishesSync` (FR-011, FR-022, INV-18, D-010)
- [ ] T060 [US3] Actualizar `easyeat/frontend/src/hooks/useRealtimeAvailability.ts` para que el catálogo del comensal refresque al detectar cambios de ETag que involucren `pausedByChef` (FR-012, FR-013, INV-15, D-006)
- [ ] T061 [US3] Extender `easyeat/frontend/src/components/catalog/DishCard.tsx` para ocultar platos con `pausedByChef=true` de la grilla del catálogo del comensal (INV-8, FR-012)
- [ ] T062 [US3] Extender `easyeat/frontend/src/components/catalog/DishDetailModal.tsx` para detectar reactivamente (vía `useRealtimeAvailability`) cuando el plato abierto pasa a `pausedByChef=true`: mostrar distintivo "Pausado por cocina" y deshabilitar controles de compra sin cerrar el modal; al cerrar, el plato queda excluido del catálogo (FR-016, EC-017, INV-15, D-008)
- [ ] T063 [US3] Extender `easyeat/frontend/src/context/CartContext.tsx` para detectar platos con `pausedByChef=true` en el carrito de sesión (`sessionStorage 'easyeat_cart'`) y exponer el flag de bloqueo de checkout cuando existan platos pausados (FR-019, EC-023, D-007)
- [ ] T064 [US3] Extender `easyeat/frontend/src/components/cart/CartDrawer.tsx` para mostrar la advertencia visible "Pausado por cocina" en ítems pausados, deshabilitar el botón `+` de incremento y bloquear el botón de confirmación del pedido mientras haya platos pausados en el carrito (FR-019, EC-023, D-007)
- [ ] T065 [US3] Extender `easyeat/frontend/src/components/cart/CartItemWithBadge.tsx` para inhabilitar visualmente los controles de cantidad cuando el ítem tiene `pausedByChef=true` (FR-019, D-007)

**Checkpoint**: US3 completamente funcional — toggle operativo, platos pausados ocultos en `<= 3 s`, carrito con bloqueo, modal reactivo, sincronización entre chefs.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Refinamientos de calidad que afectan a múltiples User Stories: validación de contratos OpenAPI, E2E quickstart y auditoría de invariantes.

- [ ] T066 [P] Escribir test de contrato OpenAPI `easyeat/backend/src/test/java/com/easyeat/inventory/contract/IngredientOpenApiContractTest.java`: validar que los endpoints de ingredientes del contrato `specs/013-critical-stock-chef-pause/contracts/openapi.yaml` responden con los schemas y códigos HTTP especificados (plan.md §9, D-003)
- [ ] T067 [P] Escribir test de contrato OpenAPI `easyeat/backend/src/test/java/com/easyeat/dish/contract/DishChefPauseOpenApiContractTest.java`: validar endpoints de toggle de pausa y operational-status contra `specs/013-critical-stock-chef-pause/contracts/openapi.yaml` (plan.md §9, D-005)
- [ ] T068 [P] Verificar que el mapeo de errores RFC 7807 (`ProblemDetail`) está configurado en `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` para los nuevos casos: umbral negativo (400), formato inválido (400), Chef sin autorización para umbral (403), Admin sin autorización para toggle (403), insumo/plato no encontrado (404) (plan.md §10)
- [ ] T069 Ejecutar el escenario E2E completo del `specs/013-critical-stock-chef-pause/quickstart.md` sobre entorno local con la migración V11 aplicada y validar los 7 escenarios prácticos descritos
- [ ] T070 [P] Revisar la matriz de trazabilidad `plan.md §12` para confirmar que el 100% de los 23 FRs, 19 INVs, 25 ECs y 15 SCs tienen al menos una tarea asignada en este `tasks.md`
- [ ] T071 Ejecutar la suite de tests del backend (`./mvnw test` desde `easyeat/backend/`) y confirmar que todos los tests de la feature 013 pasan (cero regresiones en tests preexistentes)
- [ ] T072 [P] Ejecutar la suite de tests del frontend (`npm run test` desde `easyeat/frontend/`) y confirmar que todos los tests RTL de la feature 013 pasan (cero regresiones)

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Phase 1 (Setup)**: Sin dependencias — puede comenzar de inmediato.
- **Phase 2 (Foundational)**: Depende de Phase 1 — **bloquea todas las fases US**.
- **Phase 3 (US1)**: Depende de Phase 2.
- **Phase 4 (US2)**: Depende de Phase 3 (necesita `criticalStockThreshold` persistido por US1).
- **Phase 5 (US3)**: Depende de Phase 2 (independiente de US1 y US2 — bounded context distinto).
- **Phase 6 (Polish)**: Depende de Phase 3 + Phase 4 + Phase 5.

### Dependencias entre User Stories

- **US1 (P1)**: Puede comenzar tras Phase 2. Sin dependencias de otras US.
- **US2 (P1)**: Depende de US1 (evalúa `criticalStockThreshold` en `Ingredient`).
- **US3 (P1)**: Puede comenzar tras Phase 2 en paralelo con US1/US2 (bounded context `dish`/`catalog` separado de `inventory`).

### Dentro de cada User Story (orden TDD)

1. Tests unitarios y de edge cases → deben **FALLAR** antes de implementar.
2. Implementación de entidades y repositorios.
3. Implementación de servicios.
4. Implementación de controladores / endpoints.
5. Tests y componentes frontend.
6. Implementación de páginas y contexto frontend.

### Oportunidades de paralelismo

- **Phase 2**: T006 y T007 (tipos TypeScript) pueden ejecutarse en paralelo con T004 y T005 (entidades JPA).
- **Phase 3 — Tests US1**: T008, T009, T010, T011, T012, T013 todos en paralelo.
- **Phase 4 — Tests US2**: T021, T022, T023, T024, T025, T026, T027 todos en paralelo.
- **Phase 5 — Tests US3**: T034..T045 todos en paralelo.
- **US3 completa** puede ejecutarse en paralelo con US1+US2 si hay dos desarrolladores.
- **Phase 6**: T066, T067, T068, T070, T072 en paralelo; T069 y T071 requieren que la suite esté completa.

---

## Ejemplo de ejecución paralela — User Story 3 (Tests)

```bash
# Lanzar todos los tests de US3 en paralelo (deben fallar antes de implementar):
Task: T034 DishChefPauseTest.java             → easyeat/backend/src/test/.../dish/service/
Task: T035 DishThreeTierAvailabilityTest.java → easyeat/backend/src/test/.../dish/service/
Task: T036 InventorySecurityTest.java         → easyeat/backend/src/test/.../inventory/security/
Task: T037 ChefDishPauseIntegrationTest.java  → easyeat/backend/src/test/.../dish/integration/
Task: T038 ChefPauseConcurrenceTest.java      → easyeat/backend/src/test/.../dish/integration/
Task: T039 DinerCatalogAvailabilityTest.java  → easyeat/backend/src/test/.../catalog/integration/
Task: T040 ChefDishPauseToggle.test.tsx       → easyeat/frontend/tests/components/
Task: T041 ChefDishesPage.test.tsx            → easyeat/frontend/tests/pages/
Task: T042 useChefDishesSync.test.ts          → easyeat/frontend/tests/hooks/
Task: T043 DishDetailModal.test.tsx           → easyeat/frontend/tests/components/
Task: T044 CartDrawer.test.tsx                → easyeat/frontend/tests/components/
Task: T045 useRealtimeAvailability.test.ts    → easyeat/frontend/tests/hooks/
```

---

## Estrategia de implementación

### MVP (Solo User Story 1)

1. Completar **Phase 1**: Setup del branch y migración V11.
2. Completar **Phase 2**: Entidades JPA y tipos TypeScript.
3. Completar **Phase 3**: US1 — umbral de stock crítico.
4. **PARAR Y VALIDAR**: el administrador puede definir umbrales; el Chef recibe 403; validaciones rechazan valores inválidos.
5. Demo / deploy parcial si se requiere.

### Entrega incremental

1. Phase 1 + Phase 2 → Base lista.
2. Phase 3 (US1) → Validar de forma independiente → Demo.
3. Phase 4 (US2) → Validar alertas visuales → Demo.
4. Phase 5 (US3) → Validar pausa de chef → Demo.
5. Phase 6 (Polish) → Suite completa → Release.

### Estrategia de equipo paralelo

Con dos desarrolladores disponibles tras completar Phase 2:

- **Dev A**: Phase 3 (US1) → Phase 4 (US2).
- **Dev B**: Phase 5 (US3).
- Ambos convergen en Phase 6 (Polish).

---

## Notas

- Las tareas marcadas `[P]` pueden ejecutarse en paralelo dentro de su fase.
- El label `[US?]` mapea la tarea a su User Story de trazabilidad.
- Cada fase de User Story es completamente verificable de forma independiente.
- **TDD estricto**: verificar que los tests FALLAN antes de escribir la implementación.
- Rutas relativas a la raíz del repositorio `easyeat/` conforme al layout del `plan.md §5.2`.
- El campo `criticalStockThreshold` en frontend es `number | null` (`null` = sin umbral configurado, INV-7).
- El polling de `useChefDishesSync` y `useRealtimeAvailability` usa `intervalMs = 2500` conforme a las specs 008 y 011.
