---
description: "Lista de tareas atómicas ordenadas por dependencia con enfoque TDD (Red -> Green) para la Spec 028"
---

# Tareas: optimización transversal de latencia, consultas en lote y carga instantánea en entornos de capa gratuita (Spec 028)

**Input**: Documentos de diseño en `specs/028-platform-load-time-optimization/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/*`, `quickstart.md`) y `.specify/memory/constitution.md` (v2.1.1).

**Prerequisites**: `plan.md` (completo), `spec.md` (US1–US4, FR-001–FR-010, SC-001–SC-006), `research.md` (D-01–D-11), `data-model.md`, `contracts/01`–`05`, `quickstart.md`.

**Tests (TDD obligatorio)**: Todas las fases con código productivo siguen disciplina estricta **RED primero → GREEN después** tanto en backend (pruebas unitarias Mockito con `verify(..., never())` sobre métodos `N+1` + pruebas de integración Testcontainers PostgreSQL con `SessionFactory.getStatistics()` de Hibernate) como en frontend (Vitest + React Testing Library). Las pruebas existentes de integración, contrato HTTP y suites Vitest de specs `001`–`027` permanecen 100 % intactas (FR-010), excepto los mocks `N+1` autorizados y la línea 56 de `NeonDataSourceConfigurationTest` (decisiones Q1–Q4 del 24/11/2026).

**Organization**: Tareas agrupadas por User Story (US1–US4 en orden de prioridad P1 → P2) para permitir entrega vertical full-stack (Principio X) e incremento verificable e independiente por historia.

## Formato: `- [ ] [ID] [P?] [Story?] Descripción con ruta exacta`

- **[P]**: Ejecutable en paralelo (archivos distintos, sin dependencia sobre tareas incompletas).
- **[Story]**: Etiqueta de historia de usuario (`[US1]`, `[US2]`, `[US3]`, `[US4]`) únicamente en las fases de historias; omitida en Setup, Foundational y Polish.
- Cada tarea referencia sus requisitos origen (`FR-xxx`, `SC-xxx`, `D-xx`) y rutas exactas relativas a la raíz del repositorio (`easyeat/backend/...`, `easyeat/frontend/...`, `.specify/memory/...`).

---

## Phase 1: Setup (preparación y arnés de verificación SQL)

**GitHub Issue**: [#515](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/515)

**Purpose**: Verificar la línea base limpia sin dependencias nuevas y disponer el arnés de conteo de consultas SQL basado en `SessionFactory.getStatistics()` de Hibernate (decisión Tasks Q2).

- [X] T001 Verificar línea base de compilación, versión Flyway más alta en `easyeat/backend/src/main/resources/db/migration/` y ausencia de nuevas dependencias en `easyeat/backend/pom.xml` y `easyeat/frontend/package.json` (`FR-010`, `D-05`)
- [X] T002 [P] Crear helper de aserción de cardinalidad SQL basado en `SessionFactory.getStatistics()` (`clear()` y lectura de `getPrepareStatementCount()`) en `easyeat/backend/src/test/java/com/easyeat/common/SqlQueryCountAssertionHelper.java` (`SC-001`, `SC-002`, Tasks Q2)

---

## Phase 2: Foundational (prerrequisitos bloqueantes compartidos)

**GitHub Issue**: [#516](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/516)

**Purpose**: Infraestructura transversal compartida por múltiples historias (índices compuestos Flyway, GZIP, HikariCP `minimum-idle = 5`, CORS `ETag`, cachés base `DietaryTaxonomyCache` y `OperatingScheduleCache` según decisión Tasks Q1, y almacén cliente `ClientCacheContext` con purga de sesión).

**CRITICO**: Ninguna fase de historia de usuario comienza hasta completar el checkpoint de esta fase.

### Tests Foundational (TDD — RED primero)

> **NOTA: Escribir y ejecutar estas pruebas PRIMERO, confirmando que FALLAN (RED) antes de implementar el código productivo.**

- [X] T003 [P] Escribir prueba de integración RED que localiza la migración por descripción (regla 5 de `.specify/memory/migrations.md`) y verifica en `pg_indexes` la existencia de `idx_dishes_public_catalog`, `idx_order_items_dish_order` e `idx_temp_reservations_active_expires_order` en `easyeat/backend/src/test/java/com/easyeat/config/PerformanceCompositeIndexesMigrationTest.java` (`FR-009`, `D-10`)
- [X] T004 [P] Actualizar `easyeat/backend/src/test/java/com/easyeat/config/NeonDataSourceConfigurationTest.java`: aserción de `spring.datasource.hikari.minimum-idle` de `1` a `5` (línea 56, decisión Q1) y aserción obsoleta de `flywayLocations_shouldIncludeMigrationAndDev` (líneas 114–141, decisión F3) para reflejar `application.yml` vigente (`classpath:db/migration`) (`FR-009`, `D-10`, Q1, F3)
- [X] T005 [P] Escribir prueba de integración RED que valida `Content-Encoding: gzip` para respuestas `application/json` y `application/problem+json` ≥ 1024 bytes y la cabecera CORS `Access-Control-Expose-Headers: ETag` en `easyeat/backend/src/test/java/com/easyeat/config/HttpTransportOptimizationTest.java` (`FR-009`, `FR-004`, `D-04`, `D-10`, Q2)
- [X] T006 [P] Escribir pruebas unitarias RED de `DietaryTaxonomyCache` (acotada a `maximumSize = 1`, `expireAfterWrite = 60 min`, reutilización en memoria sin segunda llamada al repositorio y `evictAll`) en `easyeat/backend/src/test/java/com/easyeat/profile/cache/DietaryTaxonomyCacheTest.java` (`FR-005`, `D-05`, `D-06`, Tasks Q1)
- [X] T007 [P] Escribir pruebas unitarias RED de `OperatingScheduleCache` (`maximumSize = 200`, `expireAfterWrite = 15 min`, carga semanal única por `establishmentId`, invalidación sincrónica y evicción vía listener JPA) en `easyeat/backend/src/test/java/com/easyeat/operations/cache/OperatingScheduleCacheTest.java` (`FR-005`, `D-05`, `D-06`, Tasks Q1)
- [X] T008 [P] Escribir pruebas unitarias RED en Vitest de `ClientSWRStore` y `ClientCacheProvider` (deduplicación de promesas `inFlight`, lectura sincrónica, suscripciones reactivas, `setMediaVersion`/`getMediaVersion`, invalidación por clave/prefijo, `clearAll` sincrónico desde `AuthContext` con `<ClientCacheProvider>` envolviendo a `<AuthProvider>` en logout/cambio de usuario/`easyeat:session-expired`, y almacén efímero aislado fuera del provider) en `easyeat/frontend/tests/context/ClientCacheContext.test.tsx` (`FR-007`, `FR-008`, `FR-010`, `D-08`, `D-09`, F2, E1)

### Implementación Foundational (TDD — GREEN)

- [X] T009 Crear la migración versionada Flyway de índices compuestos (`idx_dishes_public_catalog`, `idx_order_items_dish_order`, `idx_temp_reservations_active_expires_order`, confirmando versión más baja > `V29` al autorar — candidata `V30`) en `easyeat/backend/src/main/resources/db/migration/V30__add_performance_composite_indexes.sql` y registrar la fila en `.specify/memory/migrations.md` (`FR-009`, `D-10`, `data-model.md §6`)
- [X] T010 [P] Configurar compresión HTTP GZIP (`server.compression.enabled=true`, `min-response-size=1024`, `mime-types=application/json,application/problem+json`) y `spring.datasource.hikari.minimum-idle: 5` en `easyeat/backend/src/main/resources/application.yml` (`FR-009`, `D-10`, Q1)
- [X] T011 [P] Añadir `configuration.setExposedHeaders(List.of("ETag"))` en `corsConfigurationSource` dentro de `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java` (`FR-004`, `FR-006`, `D-04`, Q2)
- [X] T012 [P] Implementar `DietaryTaxonomyCache` con Caffeine (`maximumSize = 1`, `expireAfterWrite = 60 min`) en `easyeat/backend/src/main/java/com/easyeat/profile/cache/DietaryTaxonomyCache.java` e integrarla en `easyeat/backend/src/main/java/com/easyeat/profile/service/DietaryRestrictionService.java` y `easyeat/backend/src/main/java/com/easyeat/catalog/service/CatalogComplianceService.java` (`FR-005`, `D-05`, `D-06`, Tasks Q1)
- [X] T013 [P] Implementar `OperatingScheduleCache` con Caffeine (`maximumSize = 200`, `expireAfterWrite = 15 min`) y listener JPA en `easyeat/backend/src/main/java/com/easyeat/operations/cache/OperatingScheduleCache.java`, anotar `@EntityListeners` en `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentOperatingSchedule.java` e integrar lectura/invalidación sincrónica en `easyeat/backend/src/main/java/com/easyeat/operations/service/OperatingScheduleService.java` (`FR-005`, `D-05`, `D-06`, Tasks Q1)
- [X] T014 [P] Implementar `ClientSWRStore` (incluyendo `setMediaVersion`/`getMediaVersion`) y `ClientCacheProvider` (con aislamiento automático para pruebas Vitest standalone) en `easyeat/frontend/src/context/ClientCacheContext.tsx` (`FR-007`, `FR-008`, `FR-010`, `D-08`, E1)
- [ ] T015 Montar `<ClientCacheProvider>` **envolviendo a `<AuthProvider>`** en `easyeat/frontend/src/App.tsx` y consumir `useContext(ClientCacheContext)` de forma opcional en `easyeat/frontend/src/context/AuthContext.tsx` para invocar `clearAll()` sincrónico en `logout`, `clearAuth`, cambio de `user.id` en `setToken` y evento `window` `'easyeat:session-expired'` sin afectar tests standalone (`FR-008`, `SC-005`, `D-09`, F2)
- [ ] T016 Ejecutar verificación GREEN de Fase 2 (`mvn test -Dtest=PerformanceCompositeIndexesMigrationTest,NeonDataSourceConfigurationTest,HttpTransportOptimizationTest,DietaryTaxonomyCacheTest,OperatingScheduleCacheTest` en `easyeat/backend/` y `npx vitest run tests/context/ClientCacheContext.test.tsx` en `easyeat/frontend/`) confirmando paso de RED a GREEN (`SC-006`)

**Checkpoint**: Infraestructura transversal, índices compuestos, transporte GZIP/CORS, cachés de metadatos base y `ClientCacheProvider` verificados en verde; las historias de usuario pueden comenzar.

---

## Phase 3: User Story 1 — Carga y actualización casi instantánea del Catálogo del Comensal (Priority: P1) [MVP]

**GitHub Issue**: [#517](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/517)

**Goal**: Resolver el catálogo categorizado del comensal en ≤ 6 consultas SQL constantes ($O(1)$) y la disponibilidad con `ETag` SHA-256 bit a bit en ≤ 4 consultas, eliminar el 100 % de las peticiones HTTP duplicadas al montar `/catalogo` (perfil, listado plano `/dishes`, taxonomía innecesaria y sondeo en `t = 0 ms`), habilitar render instantáneo (< 100 ms) con SWR y carga diferida de imágenes en tarjetas.

**Independent Test**: Cargar `/catalogo` de un establecimiento con ≥ 20 platos publicados (invitado y autenticado con restricciones dietarias), recargar y navegar hacia `/descubrimiento` o `/perfil` y de regreso, verificando ≤ 6 SQL en catálogo, ≤ 4 SQL en disponibilidad (`304` sin cambios), exactamente 1 `/profile/me`, 1 `/branding`, 1 `/catalog`, 0 `/dishes`, 0 `/catalog/availability` en `t = 0 ms`, `< 100 ms` al volver y `loading="lazy"` + `decoding="async"` en `DishCard`.

### Tests para User Story 1 (TDD — RED primero)

> **NOTA: Escribir y ejecutar estas pruebas PRIMERO, confirmando que FALLAN (RED) antes de implementar el código productivo.**

- [ ] T017 [P] [US1] Actualizar mocks N+1 a consulta consolidada en lote (`findRecipeLinesWithIngredientsByDishIdsAndTenantId`, añadiendo `verify(..., never())` sobre `existsByDishId`, `findByIdDishId` y `findByIdAndTenantId`) y agregar casos de colección vacía (`IN ()`) y determinismo canónico de categorías/alérgenos/estilos de vida en `easyeat/backend/src/test/java/com/easyeat/catalog/service/DinerCatalogServiceTest.java` (`FR-001`, `FR-010`, `D-01`, `D-11`, F7)
- [ ] T018 [P] [US1] Actualizar mocks N+1 a pasada única en lote consolidado (`findRecipeLinesWithIngredientsByDishIdsAndTenantId`, añadiendo `verify(..., never())` sobre `findByIdDishId`, `findByIdAndTenantId` y `findById` en bucle) y validar determinismo bit a bit del `ETag` SHA-256 (incluyendo `pausedByChef` sobre plato agotado y mapa vacío `"empty"`) en `easyeat/backend/src/test/java/com/easyeat/catalog/service/DishAvailabilityEngineTest.java` (`FR-002`, `FR-010`, `D-02`, `D-11`, F7)
- [ ] T019 [P] [US1] Escribir prueba de integración RED con Testcontainers y `SqlQueryCountAssertionHelper` (`hibernate.generate_statistics=true`) verificando ≤ 6 SQL en `GET /api/v1/establishments/{tenantId}/catalog` autenticado como `COMENSAL` (con ≥ 20 platos × 4 ingredientes y restricciones dietarias), ≤ 6 SQL en flujo invitado vía `GET /api/v1/establishments/{tenantId}/dishes` (o `DinerCatalogService.getCatalog(tenantId, null)` sin alterar `hasRole('COMENSAL')` en `SecurityConfig`), ≤ 4 SQL en `GET /api/v1/establishments/{tenantId}/catalog/availability` + `304 Not Modified`, y coherencia inmediata tras mutar stock/pausa/reserva en `easyeat/backend/src/test/java/com/easyeat/catalog/integration/CatalogBatchQueryCountIntegrationTest.java` (`FR-001`, `FR-002`, `SC-001`, `SC-005`, Tasks Q2, F4)
- [ ] T020 [P] [US1] Escribir pruebas Vitest RED en `easyeat/frontend/tests/pages/CatalogPagePerformanceAndSwr.test.tsx` verificando: exactamente 1 `/profile/me`, 1 `/branding`, 1 `/catalog`, 0 `/dishes`, 0 `/dietary-restrictions` y 0 `/catalog/availability` en `t = 0 ms` (primer sondeo a `2500 ms`); render inmediato SWR sin esqueleto en revisita preservando scroll, categoría activa, filtros y modales; conservación de datos ante error transitorio en background; y preservación de esqueleto/error `"Reintentar"` en primera visita (`FR-007`, `FR-008`, `SC-003`, `SC-004`, `D-08`, `D-09`)
- [ ] T021 [P] [US1] Escribir prueba Vitest RED verificando atributos `loading="lazy"` y `decoding="async"` en la imagen renderizada por `DishCard` en `easyeat/frontend/tests/components/DishCardLazyLoading.test.tsx` (`FR-009`, `D-10`)

### Implementación Backend para User Story 1 (TDD — GREEN)

- [ ] T022 [P] [US1] Añadir consultas JPQL con `LEFT JOIN FETCH d.restrictionCodes` (`findPublicCatalogDishesWithRestrictions`, `findPublishedActiveDishesWithRestrictions`) reutilizando/generalizando el método existente `findByIdIn` en `easyeat/backend/src/main/java/com/easyeat/catalog/repository/DishRepository.java` y añadir `@BatchSize(size = 100)` en `Dish.restrictionCodes` dentro de `easyeat/backend/src/main/java/com/easyeat/catalog/domain/Dish.java` como red de seguridad ante consultas paginadas (`FR-001`, `FR-002`, `FR-004`, `D-01`, A1, F1, E2)
- [ ] T023 [P] [US1] Añadir consulta en lote consolidada `findRecipeLinesWithIngredientsByDishIdsAndTenantId` (`LEFT JOIN Ingredient` por `dishIds` y `tenantId`) y `findByIdDishIdIn(Collection<UUID> dishIds)` en `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishRecipeIngredientRepository.java` (`FR-001`, `FR-002`, `D-01`, `data-model.md §1`)
- [ ] T024 [P] [US1] Añadir consulta en lote `findDishIdsWithPhoto(Collection<UUID> dishIds)` sin lectura `BYTEA` en `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishPhotoRepository.java` y `findByIdInAndTenantId(Collection<UUID> ids, UUID tenantId)` en `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java` (`FR-001`, `FR-004`, `D-01`)
- [ ] T025 [P] [US1] Añadir consulta agregada en lote `sumActiveReservedPortionsByDishIds(Collection<UUID> dishIds, Instant now)` (`GROUP BY oi.dishId`) en `easyeat/backend/src/main/java/com/easyeat/order/repository/TemporaryReservationRepository.java` (`FR-001`, `FR-002`, `D-01`)
- [ ] T026 [US1] Refactorizar `DishAvailabilityEngine` para cálculo en pasada única en memoria (`isAvailable` + `availableStock`) usando la consulta consolidada `findRecipeLinesWithIngredientsByDishIdsAndTenantId`, `computeAvailabilityMap` en lote y `computeETag` sin `findById` en bucle (preservando SHA-256 bit a bit y guarda `IN ()` vacío) en `easyeat/backend/src/main/java/com/easyeat/catalog/service/DishAvailabilityEngine.java` (`FR-002`, `D-02`, `D-11`, F7)
- [ ] T027 [US1] Refactorizar `DinerCatalogService.getCatalog` para ensamblado en lote $O(1)$ (≤ 6 consultas SQL usando `findRecipeLinesWithIngredientsByDishIdsAndTenantId`, `DietaryTaxonomyCache`, `OperatingScheduleCache`, `BatchedCatalogSnapshot` y guarda `IN ()` vacío, preservando orden canónico y constructores existentes) en `easyeat/backend/src/main/java/com/easyeat/catalog/service/DinerCatalogService.java` y verificar compatibilidad de stubs en `easyeat/backend/src/main/java/com/easyeat/catalog/controller/DinerCatalogController.java` (`FR-001`, `FR-002`, `FR-010`, `D-01`, `D-02`, F7)

### Implementación Frontend para User Story 1 (TDD — GREEN)

- [ ] T028 [P] [US1] Añadir atributos `loading="lazy"` y `decoding="async"` al elemento `<img>` de `DishCard` sin modificar estilos ni contrato `comensal.DESIGN.md` en `easyeat/frontend/src/components/catalog/DishCard.tsx` (`FR-009`, `D-10`)
- [ ] T029 [P] [US1] Extender `useProfile` con `options?: { enabled?: boolean; loadTaxonomy?: boolean }` (default `true`) integrado con `ClientSWRStore` (`profile:me`, `taxonomy:dietary`) e invalidación sincrónica de `profile:me` y prefijo `catalog:` tras mutaciones en `easyeat/frontend/src/hooks/useProfile.ts` (`FR-007`, `FR-008`, `FR-010`, `D-08`, `D-09`)
- [ ] T030 [P] [US1] Restringir la carga de perfil en `WorkspaceLayout` exclusivamente a `user?.role === 'COMENSAL'` compartiendo `profile:me` a través de `ClientSWRStore` en `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` (`FR-007`, `SC-004`, `D-08`)
- [ ] T031 [P] [US1] Extender `useCatalog` con `options?: { enabled?: boolean }` (default `true`) en `easyeat/frontend/src/hooks/useCatalog.ts` y `useRealtimeAvailability` con `deferInitialPoll?: boolean` (default `false`) en `easyeat/frontend/src/hooks/useRealtimeAvailability.ts` (`FR-007`, `FR-010`, `D-08`)
- [ ] T032 [P] [US1] Integrar `useRestaurantBranding` con `ClientSWRStore` (`branding:diner:{establishmentId}`) para deduplicación en vuelo y SWR entre vistas en `easyeat/frontend/src/hooks/useRestaurantBranding.ts` (`FR-007`, `FR-008`, `D-08`)
- [ ] T033 [US1] Actualizar `CatalogPage` para usar `ClientSWRStore` (`catalog:{tenantId}:{userScope}` con render < 100 ms, esqueletos/error intactos en primera visita y estabilidad de scroll/categoría/filtros/modales en revalidación), `useProfile({ enabled: isAuthenticated, loadTaxonomy: false })`, `useCatalog(undefined, { enabled: !tenant?.id })`, inicialización de `initialAvailability` + `deferInitialPoll: true`, y `?v=<updatedAt>` en el logotipo del local en `easyeat/frontend/src/pages/CatalogPage.tsx` (`FR-006`, `FR-007`, `FR-008`, `SC-003`, `SC-004`, `D-07`, `D-08`, `D-09`, Q3)
- [ ] T034 [US1] Ejecutar verificación GREEN del MVP (US1): `mvn test -Dtest=DinerCatalogServiceTest,DishAvailabilityEngineTest,DinerAvailabilityControllerTest,CatalogBatchQueryCountIntegrationTest,DinerCatalogQuickstartIntegrationTest,DinerCatalogAvailabilityPollingTest,CatalogStockExhaustionSyncIntegrationTest,MultiTenantCatalogIntegrationTest,CatalogComplianceIntegrationTest` en `easyeat/backend/` y `npx vitest run tests/pages/CatalogPagePerformanceAndSwr.test.tsx tests/components/DishCardLazyLoading.test.tsx tests/pages/CatalogPage.test.tsx src/test/catalog/CatalogPage.test.tsx tests/hooks/useCatalog.test.tsx tests/hooks/useRealtimeAvailability.test.ts src/test/catalog/useRealtimeAvailability.test.ts tests/hooks/useProfile.test.ts src/test/catalog/DinerCatalogBranding.test.tsx` en `easyeat/frontend/` (`FR-010`, `SC-001`, `SC-003`, `SC-004`)

**Checkpoint**: User Story 1 (MVP) completamente funcional y verificable de forma independiente, cumpliendo SC-001 (≤ 6 SQL catálogo / ≤ 4 SQL disponibilidad) y SC-004 (cero peticiones duplicadas).

---

## Phase 4: User Story 2 — Consulta instantánea de franjas horarias de retiro en Checkout (Priority: P1)

**GitHub Issue**: [#518](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/518)

**Goal**: Calcular la ocupación de todas las franjas horarias de retiro de una jornada en Checkout (32–48 franjas de 15 minutos) mediante una única consulta agrupada (`GROUP BY timeSlotStart`) reutilizando `idx_orders_slot_capacity` y `OperatingScheduleCache`, reduciendo la carga de 5–8 s a < 1 s en caliente sin alterar reglas de capacidad, lead time ni saturación.

**Independent Test**: Consultar `GET /api/v1/establishments/{id}/pickup-slots?date=YYYY-MM-DD` para múltiples fechas con órdenes activas distribuidas en distintas franjas, verificando exactamente 1 consulta SQL agrupada sobre `orders`, clasificación exacta de `AVAILABLE`, `HIGH_DEMAND` (`>= 80 %`), ocultamiento de `SOLD_OUT` / `EXPIRED_OR_LEAD_TIME`, y render fluido en `CheckoutPage`.

### Tests para User Story 2 (TDD — RED primero)

> **NOTA: Escribir y ejecutar estas pruebas PRIMERO, confirmando que FALLAN (RED) antes de implementar el código productivo.**

- [ ] T035 [P] [US2] Actualizar mocks del bucle de conteo a consulta agrupada única (agregando `verify(orderRepository, never()).countActiveOrdersBySlot(...)` y validación de umbrales `AVAILABLE`/`HIGH_DEMAND`/`SOLD_OUT`/`EXPIRED_OR_LEAD_TIME`) en `easyeat/backend/src/test/java/com/easyeat/order/TimeSlotOccupancyTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/PickupSlotServiceTest.java` (`FR-003`, `FR-010`, `D-03`, `D-11`)
- [ ] T036 [P] [US2] Escribir prueba de integración RED con Testcontainers y `SqlQueryCountAssertionHelper` verificando exactamente 1 consulta SQL agrupada sobre `orders` para una jornada de 32–48 franjas con órdenes en estados computables (`PENDING_PAYMENT` vigentes vs vencidas, `PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`) y reflejo inmediato al crear una nueva orden en `easyeat/backend/src/test/java/com/easyeat/order/integration/PickupSlotGroupedQueryIntegrationTest.java` (`FR-003`, `SC-002`, `SC-005`, Tasks Q2)
- [ ] T037 [P] [US2] Escribir prueba Vitest RED en `easyeat/frontend/tests/pages/CheckoutPickupSlotsRendering.test.tsx` verificando que `CheckoutPage` consume el catálogo de franjas (`AVAILABLE` y `HIGH_DEMAND`) y reutiliza el branding desde `ClientSWRStore` sin re-fetch bloqueante al navegar desde `/catalogo` (`FR-003`, `FR-007`, `FR-010`)

### Implementación para User Story 2 (TDD — GREEN)

- [ ] T038 [US2] Añadir proyección `TimeSlotOccupancyProjection` y consulta JPQL agrupada `countActiveOrdersGroupedBySlot(UUID establishmentId, LocalDate pickupDate, Instant now)` (`GROUP BY o.timeSlotStart` con los mismos predicados de estado) en `easyeat/backend/src/main/java/com/easyeat/order/repository/OrderRepository.java` (`FR-003`, `D-03`, `data-model.md §2`)
- [ ] T039 [US2] Refactorizar `PickupSlotService.getPickupSlots` para ejecutar `countActiveOrdersGroupedBySlot` una única vez fuera del bucle de intervalos de 15 minutos y resolver los horarios del día solicitado y actual mediante `OperatingScheduleCache` en `easyeat/backend/src/main/java/com/easyeat/order/service/PickupSlotService.java` (`FR-003`, `FR-005`, `D-03`)
- [ ] T040 [US2] Ejecutar verificación GREEN de US2 (`mvn test -Dtest=TimeSlotOccupancyTest,PickupSlotServiceTest,PickupSlotGroupedQueryIntegrationTest` en `easyeat/backend/` y `npx vitest run tests/pages/CheckoutPickupSlotsRendering.test.tsx` en `easyeat/frontend/`) confirmando cero regresiones (`FR-010`, `SC-002`)

**Checkpoint**: User Stories 1 y 2 completas, independientes y verificadas en verde.

---

## Phase 5: User Story 3 — Agilidad operativa en tiempo real para Cocina (KDS), Mostrador y Gestión de Platos (Priority: P2)

**GitHub Issue**: [#519](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/519)

**Goal**: Eliminar las consultas `N+1` en los tres paneles operativos de personal (`Mostrador`, `Tablero KDS` y `Gestión de Platos del Chef`) resolviendo cada ciclo en ≤ 3–4 consultas constantes ($O(1)$), preservando el `ETag` contractual en `GET /api/v1/dishes/operational-status` (decisión Q2) y procesando `304 Not Modified` limpiamente en `dishApi.ts`.

**Independent Test**: Con múltiples comandas `READY_FOR_PICKUP`, comandas KDS en distintas etapas y decenas de platos con recetas, verificar ≤ 4 SQL en `GET /api/v1/counter/orders`, 1 SQL agregada en `GET /api/v1/kitchen/dashboard/summary`, ≤ 3–4 SQL en `GET /api/v1/dishes` y `GET /api/v1/dishes/operational-status`, y manejo limpio de `ETag` / `304 Not Modified` en backend y `dishApi.ts`.

### Tests para User Story 3 (TDD — RED primero)

> **NOTA: Escribir y ejecutar estas pruebas PRIMERO, confirmando que FALLAN (RED) antes de implementar el código productivo.**

- [ ] T041 [P] [US3] Actualizar mocks Mockito N+1 a consultas en lote (`findAllByIdWithItems`, `findAllById`, `findUserIdsWithPhoto` con `verify(..., never())` en `findById` y `existsByUserId`, preservando verificación por reflexión de la firma 3-arg y constructor 5-arg) en `easyeat/backend/src/test/java/com/easyeat/order/CounterOrderControllerIntegrationTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/controller/CounterOrderControllerSecurityCodeTest.java` (`FR-004`, `FR-010`, `D-04`, Q4)
- [ ] T042 [P] [US3] Actualizar mocks N+1 a proyecciones livianas + lote consolidado en `listDishes` y `getOperationalStatus` (añadiendo `verify(..., never())` sobre `existsByDishId`, `countByIdDishId`, `findByIdDishId` y `findByIdAndTenantId` en bucle, más guarda `IN ()` vacío) en `easyeat/backend/src/test/java/com/easyeat/dish/service/DishBasicServiceTest.java` (`FR-004`, `FR-010`, `D-04`, `D-11`, F1)
- [ ] T043 [P] [US3] Escribir prueba de integración RED con Testcontainers y `SqlQueryCountAssertionHelper` verificando ≤ 4 SQL en `GET /api/v1/counter/orders` (≥ 10 comandas), 1 SQL agregada en `GET /api/v1/kitchen/dashboard/summary`, ≤ 4 SQL en `GET /api/v1/dishes`, ≤ 3 SQL en `GET /api/v1/dishes/operational-status` + `304 Not Modified` (confirmando 0 consultas `N+1` ocultas a `dish_dietary_restrictions`), y cardinalidad constante en la Bandeja de Revisión Admin (`DishReviewInboxService` gracias a `@BatchSize`) en `easyeat/backend/src/test/java/com/easyeat/order/integration/StaffOperationsBatchQueryIntegrationTest.java` (`FR-004`, `SC-002`, Tasks Q2, F1, F8, E2)
- [ ] T044 [P] [US3] Escribir prueba Vitest RED en `easyeat/frontend/tests/api/dishApiOperationalStatus304.test.ts` verificando que `dishApi.getOperationalStatus(etag)` envía `If-None-Match`, acepta `304 Not Modified` vía `validateStatus` sin rechazar la promesa (incluso si la cabecera `ETag` viene ausente o normalizada) y que `useChefDishesSync` conserva `items` intactos sin vaciarlos (`FR-004`, `FR-010`, `D-04`, Q2, C1)

### Implementación Backend para User Story 3 (TDD — GREEN)

- [ ] T045 [P] [US3] Añadir consulta en lote `findUserIdsWithPhoto(Collection<UUID> userIds)` sin lectura `BYTEA` en `easyeat/backend/src/main/java/com/easyeat/profile/repository/UserPhotoRepository.java` (`FR-004`, `D-04`)
- [ ] T046 [P] [US3] Añadir consulta JPQL agregada única `findDashboardSummaryCounts(UUID establishmentId, LocalDate today)` (`SUM(CASE WHEN ...)`) preservando exactamente los predicados por estado y fecha en `easyeat/backend/src/main/java/com/easyeat/order/repository/KdsTicketRepository.java` (`FR-004`, `D-04`, `data-model.md §5.2`)
- [ ] T047 [US3] Refactorizar `CounterOrderController.getCounterReadyOrders` para cargar órdenes con ítems (`findAllByIdWithItems`), clientes (`findAllById`) y presencia de avatares (`findUserIdsWithPhoto`) en 4 consultas constantes con guarda `IN ()` vacío, preservando firma 3-arg y constructor 5-arg en `easyeat/backend/src/main/java/com/easyeat/order/controller/CounterOrderController.java` (`FR-004`, `FR-010`, `D-04`, Q4)
- [ ] T048 [US3] Refactorizar `KitchenKdsController.getDashboardSummary` para invocar `findDashboardSummaryCounts` en una única consulta agregada preservando `KdsDashboardSummaryResponse` en `easyeat/backend/src/main/java/com/easyeat/order/controller/KitchenKdsController.java` (`FR-004`, `D-04`)
- [ ] T049 [US3] Añadir proyecciones JPQL livianas sin `restrictionCodes` (`DishOperationalProjection` y `DishSummaryProjection`) en `easyeat/backend/src/main/java/com/easyeat/catalog/repository/DishRepository.java` y refactorizar `DishService.listDishes`, `DishService.getOperationalStatus` (y `updateAllergens` leyendo desde `DietaryTaxonomyCache`) para usar dichas proyecciones junto a `findRecipeLinesWithIngredientsByDishIdsAndTenantId` y `findDishIdsWithPhoto` con guarda `IN ()` vacío y semántica intacta de `hasStock` en `easyeat/backend/src/main/java/com/easyeat/dish/service/DishService.java` (`FR-004`, `FR-005`, `D-04`, `D-11`, F1, F8)
- [ ] T050 [US3] Ajustar comparación `If-None-Match` tolerante a comillas en `DishController.getOperationalStatus` preservando bit a bit la fórmula `Integer.toHexString(sb.toString().hashCode())` en `easyeat/backend/src/main/java/com/easyeat/dish/controller/DishController.java` (`FR-004`, `FR-010`, `D-04`, Q2)

### Implementación Frontend para User Story 3 (TDD — GREEN)

- [ ] T051 [US3] Añadir `validateStatus: (status) => (status >= 200 && status < 300) || status === 304` y retorno explícito `{ etag: resolvedEtag ?? ifNoneMatch, items: [], notModified: true }` en `304` dentro de `getOperationalStatus` en `easyeat/frontend/src/api/dishApi.ts`, y añadir guarda `if (response?.notModified) return;` en `easyeat/frontend/src/hooks/useChefDishesSync.ts` preservando su firma y pruebas existentes (`FR-004`, `FR-010`, `D-04`, Q2, C1)
- [ ] T052 [US3] Ejecutar verificación GREEN de US3 (`mvn test -Dtest=CounterOrderControllerIntegrationTest,CounterOrderControllerSecurityCodeTest,CounterOrderControllerTest,KitchenKdsControllerIntegrationTest,KitchenKdsExclusionTest,DishBasicServiceTest,DishChefPauseOpenApiContractTest,ChefDishPauseIntegrationTest,StaffOperationsBatchQueryIntegrationTest` en `easyeat/backend/` y `npx vitest run tests/api/dishApiOperationalStatus304.test.ts tests/hooks/useChefDishesSync.test.ts tests/pages/ChefDishesPage.test.tsx tests/pages/CounterWorkspacePage.test.tsx tests/pages/KitchenWorkspacePage.test.tsx` en `easyeat/frontend/`) (`FR-010`, `SC-002`)

**Checkpoint**: User Stories 1, 2 y 3 verificadas en verde con cardinalidad SQL $O(1)$ en todos los roles operativos.

---

## Phase 6: User Story 4 — Caché en memoria con coherencia estricta, transporte liviano y carga eficiente de imágenes (Priority: P2)

**GitHub Issue**: [#520](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/520)

**Goal**: Eliminar consultas repetitivas de validación de sesión y branding mediante `UserSessionCache` y `BrandingCache` acotadas con invalidación sincrónica determinista (+ `@EntityListeners`), habilitar `ETag` + `304 Not Modified` sin leer `BYTEA` en fotos de platos, logotipos y avatares preservando `Cache-Control` exactos (decisión Q3), y completar SWR y refresco visual de medios en Descubrimiento, Perfil y Branding Admin.

**Independent Test**: Ejecutar peticiones autenticadas consecutivas confirmando 0 lecturas repetidas a `users` seguidas de logout/login concurrente/suspensión con `401`/`403` inmediato; editar branding/logo/horarios y verificar reflejo inmediato; enviar `If-None-Match` a `/dishes/{id}/photo`, `/establishments/{id}/logo` y `/profile/users/{id}/avatar` confirmando `304 Not Modified` sin leer `BYTEA` y cabeceras `Cache-Control` exactas; y verificar SWR en Descubrimiento/Perfil/AdminBranding y purga en logout/`401`.

### Tests para User Story 4 (TDD — RED primero)

> **NOTA: Escribir y ejecutar estas pruebas PRIMERO, confirmando que FALLAN (RED) antes de implementar el código productivo.**

- [ ] T053 [P] [US4] Escribir pruebas unitarias RED de `UserSessionCache` (`maximumSize = 2000`, `expireAfterWrite = 5 min`, no cacheo de `Optional.empty()`, invalidación sincrónica) y de `JwtAuthenticationFilter` con cache hit/miss y rechazo inmediato `401`/`403` en `easyeat/backend/src/test/java/com/easyeat/auth/cache/UserSessionCacheTest.java` (`FR-005`, `SC-005`, `SC-006`, `D-05`, `D-06`)
- [ ] T054 [P] [US4] Escribir pruebas unitarias RED de `BrandingCache` (`maximumSize = 200`, `expireAfterWrite = 15 min`, vistas `DINER` y `ADMIN`, evicción sincrónica de ambas vistas ante actualización de branding o logotipo) en `easyeat/backend/src/test/java/com/easyeat/catalog/cache/BrandingCacheTest.java` (`FR-005`, `SC-005`, `D-05`, `D-06`)
- [ ] T055 [P] [US4] Escribir prueba de integración RED con Testcontainers y `SqlQueryCountAssertionHelper` verificando 0 consultas repetidas a `users` en peticiones autenticadas consecutivas, `401`/`403` inmediato ante logout/login concurrente/suspensión/cambio de contraseña/`userRepository.save` directo, e invalidación inmediata de `BrandingCache` y `OperatingScheduleCache` tras mutaciones en `easyeat/backend/src/test/java/com/easyeat/auth/integration/SessionAndMetadataCacheCoherenceIntegrationTest.java` (`FR-005`, `SC-005`, `D-06`, Tasks Q2)
- [ ] T056 [P] [US4] Escribir pruebas RED (unitarias y MockMvc) de `BinaryMediaETagSupport` y de validación condicional `ETag` + `If-None-Match` (`304 Not Modified` sin invocar lectura `BYTEA`, cabeceras `Cache-Control` exactas `private, max-age=86400`, `public, max-age=86400`, `private, max-age=3600`, firma 1-arg de `ProfileAvatarController.getUserAvatarById` y nuevo `ETag` al resubir) en `easyeat/backend/src/test/java/com/easyeat/common/BinaryMediaConditionalValidationTest.java` (`FR-006`, `FR-010`, `SC-006`, `D-07`, Q3)
- [ ] T057 [P] [US4] Escribir pruebas Vitest RED en `easyeat/frontend/tests/context/SwrCoherenceAndMediaRefresh.test.tsx` verificando: SWR `< 100 ms` en `EstablishmentSearchBar` (`establishments:all`), 1 sola llamada `GET /profile/me` al montar `ProfilePage` con `WorkspaceLayout`, deduplicación e invalidación de `branding:admin:{id}`/`branding:diner:{id}` en `AdminBrandingPage` + `WorkspaceLayout`, `?v=<updatedAt>` en logos, refresco de foto de plato vía `mediaVersions['dish:{id}']` al subir/eliminar foto en el flujo del Chef (sin alterar `photoUrl` cuando no hay versión en memoria), estabilización de `_t` en avatar por evento `easyeat:avatar-changed`, y purga total en logout y `401` (`FR-006`, `FR-007`, `FR-008`, `SC-003`, `SC-005`, `D-07`, `D-08`, `D-09`, Q3, E1)

### Implementación Backend para User Story 4 — Cachés e invalidación (TDD — GREEN)

- [ ] T058 [P] [US4] Implementar `UserSessionCache` (`maximumSize = 2000`, `expireAfterWrite = 5 min`) y su listener JPA `@PostPersist`/`@PostUpdate`/`@PostRemove` en `easyeat/backend/src/main/java/com/easyeat/auth/cache/UserSessionCache.java`, y anotar `@EntityListeners` en `easyeat/backend/src/main/java/com/easyeat/auth/domain/User.java` (`FR-005`, `SC-005`, `D-05`, `D-06`)
- [ ] T059 [US4] Integrar `UserSessionCache` vía `@Autowired(required = false)` en `easyeat/backend/src/main/java/com/easyeat/auth/security/JwtAuthenticationFilter.java` (preservando constructores de 2 y 3 argumentos) e invalidación sincrónica explícita en los 14 puntos de mutación de usuario en `easyeat/backend/src/main/java/com/easyeat/auth/service/AuthService.java`, `easyeat/backend/src/main/java/com/easyeat/auth/service/PasswordResetService.java`, `easyeat/backend/src/main/java/com/easyeat/auth/controller/FirstLoginPasswordController.java`, `easyeat/backend/src/main/java/com/easyeat/profile/service/ProfileService.java`, `easyeat/backend/src/main/java/com/easyeat/profile/service/EmailChangeService.java` y `easyeat/backend/src/main/java/com/easyeat/staff/service/StaffService.java` (`FR-005`, `SC-005`, `D-06`)
- [ ] T060 [P] [US4] Implementar `BrandingCache` (`maximumSize = 200`, `expireAfterWrite = 15 min`, vistas `DINER`/`ADMIN`) y listener JPA en `easyeat/backend/src/main/java/com/easyeat/catalog/cache/BrandingCache.java`, anotar `@EntityListeners` en `easyeat/backend/src/main/java/com/easyeat/catalog/domain/RestaurantBranding.java`, e integrar lectura/evicción sincrónica en `easyeat/backend/src/main/java/com/easyeat/catalog/service/RestaurantBrandingService.java` y `easyeat/backend/src/main/java/com/easyeat/catalog/service/BrandingLogoStorageService.java` (`FR-005`, `SC-005`, `D-06`)
- [ ] T061 [P] [US4] Integrar `OperatingScheduleCache` en los lectores restantes de horarios semanales para suprimir el N+1 de descubrimiento y lecturas repetitivas en `easyeat/backend/src/main/java/com/easyeat/catalog/service/EstablishmentDiscoveryService.java`, `easyeat/backend/src/main/java/com/easyeat/operations/service/DinerTimeSlotService.java`, `easyeat/backend/src/main/java/com/easyeat/operations/service/SlotCapacityService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/OrderService.java` (`FR-005`, `D-06`)

### Implementación Backend para User Story 4 — Binarios condicionales sin `BYTEA` (TDD — GREEN)

- [ ] T062 [P] [US4] Implementar `BinaryMediaMetadataProjection` y `BinaryMediaETagSupport` (SHA-256 determinista sobre `resourceId:updatedAtMillis:fileSize:fileName` y comparación tolerante a comillas) en `easyeat/backend/src/main/java/com/easyeat/common/http/BinaryMediaETagSupport.java` (`FR-006`, `D-07`, `data-model.md §5.1`)
- [ ] T063 [P] [US4] Añadir proyección liviana sin `BYTEA` `findMetadataByDishId` en `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishPhotoRepository.java`, asignar `updatedAt = Instant.now()` al subir en `easyeat/backend/src/main/java/com/easyeat/dish/service/DishPhotoService.java`, y habilitar `ETag` + `If-None-Match` (`304` sin `BYTEA`, preservando `Cache-Control: private, max-age=86400`) en `easyeat/backend/src/main/java/com/easyeat/dish/controller/DishPhotoController.java` (`FR-006`, `FR-010`, `D-07`, Q3)
- [ ] T064 [US4] Añadir proyección liviana sin `BYTEA` `findMetadataByEstablishmentId` en `easyeat/backend/src/main/java/com/easyeat/catalog/repository/RestaurantLogoRepository.java`, asignar `updatedAt = Instant.now()` al subir en `easyeat/backend/src/main/java/com/easyeat/catalog/service/BrandingLogoStorageService.java` (secuenciado tras T060 y T062), y habilitar `ETag` + `If-None-Match` (`304` sin `BYTEA`, preservando `Cache-Control: public, max-age=86400`) en `easyeat/backend/src/main/java/com/easyeat/catalog/controller/DinerBrandingController.java` (`FR-006`, `FR-010`, `D-07`, Q3)
- [ ] T065 [P] [US4] Añadir proyección liviana sin `BYTEA` `findMetadataByUserId` en `easyeat/backend/src/main/java/com/easyeat/profile/repository/UserPhotoRepository.java`, asignar `updatedAt = Instant.now()` al subir en `easyeat/backend/src/main/java/com/easyeat/profile/service/UserPhotoService.java`, y habilitar `ETag` + `If-None-Match` vía `RequestContextHolder` (preservando la firma 1-arg `getUserAvatarById(UUID)` y `Cache-Control: private, max-age=3600` / `private, max-age=86400`) en `easyeat/backend/src/main/java/com/easyeat/profile/controller/ProfileAvatarController.java` (`FR-006`, `FR-010`, `D-07`, Q3)

### Implementación Frontend para User Story 4 — SWR restante y refresco de imágenes (TDD — GREEN)

- [ ] T066 [P] [US4] Integrar `ClientSWRStore` (`establishments:all`, render inmediato < 100 ms + revalidación silenciosa en segundo plano) en `easyeat/frontend/src/components/catalog/EstablishmentSearchBar.tsx` (`FR-008`, `SC-003`, `D-09`)
- [ ] T067 [P] [US4] Compartir `branding:admin:{id}` en `ClientSWRStore`, invalidar `branding:admin:{id}` y `branding:diner:{id}` tras guardar/subir logo y anexar `?v=<updatedAt>` en la imagen de logo dentro de `easyeat/frontend/src/pages/AdminBrandingPage.tsx` y `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` (`FR-006`, `FR-007`, `FR-008`, `D-07`, `D-08`, Q3)
- [ ] T068 [P] [US4] Estabilizar el parámetro de versión de avatar por evento `easyeat:avatar-changed` (manteniendo intacta la firma y comportamiento de `getAuthenticatedAvatarUrl` para `avatarUtils.test.ts`) en `easyeat/frontend/src/components/layout/RoleLayoutShell.tsx` y `easyeat/frontend/src/components/profile/PersonalDataForm.tsx` (`FR-006`, `FR-010`, `D-07`, Q3, F6)
- [ ] T068b [P] [US4] Cablear `mediaVersions['dish:{id}']` en `ClientSWRStore` al subir o eliminar fotografías de platos en `easyeat/frontend/src/pages/ChefDishEditorPage.tsx` (coordinado con `easyeat/frontend/src/utils/chefDishPersistence.ts`) y anexar `?v=<version>` en `easyeat/frontend/src/components/catalog/DishCard.tsx` y `easyeat/frontend/src/components/catalog/DishDetailModal.tsx` únicamente cuando exista versión registrada en memoria de sesión (preservando `photoUrl` exacto sin `?v=` en tests standalone como `DishCard.test.tsx`) (`FR-006`, `FR-010`, `D-07`, Q3, E1)
- [ ] T069 [US4] Ejecutar verificación GREEN de US4 (`mvn test -Dtest=UserSessionCacheTest,BrandingCacheTest,SessionAndMetadataCacheCoherenceIntegrationTest,BinaryMediaConditionalValidationTest,JwtAuthenticationFilterTest,LoginIntegrationTest,StaffStatusControllerIntegrationTest,DishPhotoControllerTest,DinerLogoEndpointTest,ProfileAvatarStaffControllerTest,AdminBrandingIntegrationTest,DinerBrandingFallbackIntegrationTest` en `easyeat/backend/` y `npx vitest run tests/context/SwrCoherenceAndMediaRefresh.test.tsx tests/utils/avatarUtils.test.ts tests/pages/ProfilePage.test.tsx tests/components/WorkspaceLayout.test.tsx src/test/catalog/EstablishmentSearchBar.test.tsx src/test/admin/AdminBrandingPage.test.tsx tests/components/DishCard.test.tsx` en `easyeat/frontend/`) (`FR-010`, `SC-005`, `SC-006`)

**Checkpoint**: Las 4 historias de usuario están implementadas y verificadas independientemente con coherencia estricta y cero regresiones.

---

## Phase 7: Polish y verificación transversal

**GitHub Issue**: [#521](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/521)

**Purpose**: Verificación global de regresión, cobertura JaCoCo, compilación de producción, gobernanza visual (Principio XI) y validación end-to-end contra `quickstart.md`.

- [ ] T070 Ejecutar la suite completa de backend `mvn verify` en `easyeat/backend/` confirmando 100 % pruebas existentes y nuevas en verde y cumplimiento del gate JaCoCo ≥ 80 % LINE e INSTRUCTION (`FR-010`, `SC-006`)
- [ ] T071 [P] Ejecutar la suite completa de frontend (`npm run lint`, `npm test`, `npm run build`) en `easyeat/frontend/` confirmando cero regresiones en specs `001`–`027` y empaquetado Vite limpio (`FR-010`, `SC-006`)
- [ ] T072 [P] Ejecutar auditoría visual **Impeccable `/critique`** y verificación WCAG 2.2 AA sobre todos los componentes UI tocados por la especificación (`easyeat/frontend/src/components/catalog/DishCard.tsx`, `easyeat/frontend/src/components/catalog/DishDetailModal.tsx`, `easyeat/frontend/src/components/catalog/EstablishmentSearchBar.tsx`, `easyeat/frontend/src/pages/CatalogPage.tsx`, `easyeat/frontend/src/components/profile/PersonalDataForm.tsx`, `easyeat/frontend/src/components/common/WorkspaceLayout.tsx`, `easyeat/frontend/src/components/layout/RoleLayoutShell.tsx`, `easyeat/frontend/src/pages/AdminBrandingPage.tsx` y `easyeat/frontend/src/pages/ChefDishEditorPage.tsx`) contra sus contratos `.DESIGN.md` aplicables (`comensal`, `admin`, `mostrador`, `chef`) confirmando invariabilidad visual total y Zero AI-Slop (Principio XI, `FR-010`, D1, F6)
- [ ] T073 Ejecutar el recorrido completo de validación extremo a extremo (US1–US4, SC-001–SC-006, verificación `EXPLAIN` de índices compuestos y estabilidad < 512 MB RAM) definido en `specs/028-platform-load-time-optimization/quickstart.md` (`SC-001`–`SC-006`)

---

## Dependencies & Execution Order

### Dependencias entre fases

- **Phase 1 (Setup — T001–T002)**: Sin dependencias; inicia de inmediato.
- **Phase 2 (Foundational — T003–T016)**: Depende de Phase 1; **BLOQUEA** todas las fases de User Stories (US1–US4).
- **Phase 3 (US1 — T017–T034, P1 MVP)**: Depende de Phase 2. Independiente de US2, US3 y US4.
- **Phase 4 (US2 — T035–T040, P1)**: Depende de Phase 2. Independiente de US1, US3 y US4.
- **Phase 5 (US3 — T041–T052, P2)**: Depende de Phase 2 (y reutiliza `findByIdDishIdIn`, `findDishIdsWithPhoto` y `findByIdInAndTenantId` de T023–T024; si US3 corre en paralelo con US1, coordinar esos 3 métodos de repositorio primero).
- **Phase 6 (US4 — T053–T069, P2)**: Depende de Phase 2 (y coordina `WorkspaceLayout.tsx` con T030 de US1).
- **Phase 7 (Polish — T070–T073)**: Depende de la finalización de US1–US4.

### Reglas dentro de cada historia de usuario (TDD estricto)

1. Escribir y ejecutar primero TODAS las pruebas de la historia confirmando fallo (**RED**).
2. Implementar repositorios/proyecciones antes de servicios y cachés.
3. Implementar servicios antes de controladores/endpoints.
4. Implementar hooks/contextos frontend antes de páginas consumidoras.
5. Ejecutar la tarea de checkpoint de la historia confirmando paso completo a **GREEN** y cero regresiones antes de avanzar.

### Oportunidades de paralelismo

- **Phase 2**: T003–T008 (todos los tests RED) en paralelo; luego T010–T014 (configuración, CORS, cachés y `ClientCacheContext`) en paralelo.
- **Phase 3 (US1)**: T017–T021 (tests RED backend + frontend) en paralelo; T022–T025 (repositorios en lote) en paralelo; T028–T032 (componentes/hooks frontend) en paralelo con el backend T022–T027.
- **Phase 4 (US2)**: T035–T037 (tests RED backend + frontend) en paralelo; luego T038 → T039 → T040.
- **Phase 5 (US3)**: T041–T044 (tests RED) en paralelo; T045–T046 (repositorios) en paralelo; T051 (`dishApi.ts`) en paralelo con el backend T045–T050.
- **Phase 6 (US4)**: T053–T057 (tests RED) en paralelo; T058, T060, T061 (cachés), T062–T065 (binarios `ETag`/`304`, salvo `BrandingLogoStorageService.java` compartido entre T060 y T064 que se secuencia T060 → T064) y T066–T068 (frontend SWR/avatar) altamente paralelizables.

---

## Ejemplos de ejecución en paralelo por historia

### Parallel Example: User Story 1 (MVP)

```text
# 1. Lanzar todos los tests RED de US1 en paralelo:
T017: DinerCatalogServiceTest.java (mocks lote + never() N+1)
T018: DishAvailabilityEngineTest.java (pasada única + ETag SHA-256 bit a bit)
T019: CatalogBatchQueryCountIntegrationTest.java (Testcontainers + Hibernate Statistics <= 6 / <= 4 SQL)
T020: CatalogPagePerformanceAndSwr.test.tsx (0 duplicados + SWR < 100 ms)
T021: DishCardLazyLoading.test.tsx (loading="lazy" + decoding="async")

# 2. Lanzar repositorios backend y hooks/componentes frontend de US1 en paralelo:
T022: DishRepository.java (LEFT JOIN FETCH d.restrictionCodes)
T023: DishRecipeIngredientRepository.java (join consolidado recipes + ingredients)
T024: DishPhotoRepository.java + IngredientRepository.java
T025: TemporaryReservationRepository.java (GROUP BY dishId)
T028: DishCard.tsx (loading="lazy" + decoding="async")
T029: useProfile.ts (ClientSWRStore + options)
T030: WorkspaceLayout.tsx (COMENSAL only + ClientSWRStore)
T031: useCatalog.ts + useRealtimeAvailability.ts (enabled + deferInitialPoll)
T032: useRestaurantBranding.ts (ClientSWRStore)
```

### Parallel Example: User Story 4

```text
# 1. Lanzar todos los tests RED de US4 en paralelo:
T053: UserSessionCacheTest.java
T054: BrandingCacheTest.java
T055: SessionAndMetadataCacheCoherenceIntegrationTest.java
T056: BinaryMediaConditionalValidationTest.java
T057: SwrCoherenceAndMediaRefresh.test.tsx

# 2. Lanzar implementaciones independientes en paralelo (tras T062 para binarios):
T058: UserSessionCache.java + User.java
T060: BrandingCache.java + RestaurantBranding.java + RestaurantBrandingService.java + BrandingLogoStorageService.java
T061: EstablishmentDiscoveryService.java + DinerTimeSlotService.java + SlotCapacityService.java + OrderService.java
T063: DishPhotoRepository/Service/Controller (ETag/304 sin BYTEA)
T065: UserPhotoRepository/Service/Controller (ETag/304 sin BYTEA, firma 1-arg intacta)
T066: EstablishmentSearchBar.tsx (SWR)
T068: layout/RoleLayoutShell.tsx + PersonalDataForm.tsx (estabilización avatar)
T068b: ChefDishEditorPage.tsx + DishCard.tsx + DishDetailModal.tsx (mediaVersions['dish:{id}'])
```

---

## Implementation Strategy

### MVP First (User Story 1 primero)

1. Completar Phase 1 (Setup: T001–T002).
2. Completar Phase 2 (Foundational: T003–T016) — verifica índices, GZIP, HikariCP `minimum-idle = 5`, CORS `ETag`, `DietaryTaxonomyCache`, `OperatingScheduleCache` y `ClientCacheProvider`.
3. Completar Phase 3 (US1: T017–T034) siguiendo RED → GREEN.
4. **DETENER y VALIDAR**: ejecutar T034 verificando ≤ 6 SQL en catálogo, ≤ 4 SQL en disponibilidad, 0 peticiones duplicadas y `< 100 ms` en revisitas SWR. Este incremento ya resuelve el cuello de botella principal de ~10 s denunciado en la especificación.

### Entrega incremental por prioridad

1. Setup + Foundational (T001–T016) → base transversal lista.
2. US1 (P1 MVP, T017–T034) → Catálogo del Comensal instantáneo.
3. US2 (P1, T035–T040) → Checkout y franjas de retiro en 1 consulta agrupada (< 1 s).
4. US3 (P2, T041–T052) → Mostrador, KDS y Gestión de Platos del Chef en $O(1)$ + `304` limpio.
5. US4 (P2, T053–T069) → `UserSessionCache`, `BrandingCache`, binarios `ETag`/`304` sin `BYTEA` y SWR completo.
6. Polish (T070–T073) → `mvn verify`, `npm test`/`build`, auditoría visual Principio XI y recorrido `quickstart.md`.

---

## Notes

- Toda tarea `[P]` opera sobre archivos independientes; en US4 secuenciar T060 antes de T064 sobre `BrandingLogoStorageService.java` y en US3/US4 coordinar cambios en `WorkspaceLayout.tsx` (T030 → T067).
- Mantener intactos el 100 % de los tests de integración, contrato HTTP y Vitest existentes (FR-010); solo modificar mocks `N+1` en los tests unitarios explícitamente listados (T017, T018, T035, T041, T042) y `NeonDataSourceConfigurationTest` (T004: línea 56 decisión Q1 + líneas 114–141 decisión F3).
- Nunca almacenar en caché de servidor stock de ingredientes, reservas temporales activas ni ocupación de franjas horarias (SC-005).
- Confirmar el número de versión Flyway en `easyeat/backend/src/main/resources/db/migration/` al momento de ejecutar T009 y actualizar `.specify/memory/migrations.md` en el mismo PR.
