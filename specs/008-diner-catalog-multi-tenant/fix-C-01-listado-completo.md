# Fix Draft: C-01 — Listado completo de restaurantes (comensal)

**Feature**: [008-diner-catalog-multi-tenant](./spec.md)  
**Branch**: `develop/sprint-3`  
**Date**: 31/10/2026  
**Status**: Draft for implementation (pre-implementation artifact)  
**Origin**: Fix C-01 raised against the discovery module of spec 008 (User Story 1 / COM-1.5.1).

---

## 1. Resumen del fix

El comensal autenticado (`ROLE_COMENSAL`) solo podía descubrir establecimientos mediante el autocompletado por nombre (COM-1.5.1; FR-001, FR-002, FR-003; umbral de ≥ 3 caracteres). Si el comensal no conocía el nombre comercial del local, no existía camino de descubrimiento.

**Fix C-01**: en la pantalla de descubrimiento (`/descubrimiento`), el comensal autenticado debe ver el listado completo de establecimientos activos al cargar; al seleccionar uno debe cargarse su catálogo en tiempo real (mismo comportamiento que COM-1.5.1 CA2 / FR-004, FR-006); los establecimientos pausados o inhabilitados no deben aparecer; y si no hay activos, debe mostrarse un estado vacío informativo.

## 2. User story y criterios de aceptación (snapshot del fix)

**C-01 — Listado completo de restaurantes (comensal)**  
Como comensal,  
quiero ver el listado completo de establecimientos activos en la pantalla de descubrimiento,  
para poder elegir un restaurante aunque no sepa su nombre.

Contexto: actualmente solo existe búsqueda por nombre (COM-1.5.1.1). Si el comensal no conoce el nombre del local, no puede descubrirlo.

Criterios de aceptación:

1. **Dado** un comensal autenticado en la pantalla de descubrimiento, **cuando** la carga, **entonces** ve el listado de todos los establecimientos activos.
2. **Dado** el listado, **cuando** el comensal selecciona uno, **entonces** se carga su catálogo en tiempo real (mismo comportamiento que COM-1.5.1 CA2).
3. **Dado** el listado, **cuando** un establecimiento está pausado o inhabilitado, **entonces** no aparece.
4. **Dado** el listado vacío (sin establecimientos activos), **entonces** se muestra un estado vacío informativo.

## 3. Análisis de brechas vs. implementación actual (evidencia)

| Área | Estado actual | Brecha |
|---|---|---|
| Backend endpoint | `EstablishmentDiscoveryController` expone solo `GET /api/v1/establishments/search?q=`; service exige ≥ 3 caracteres; repository filtra `status = 'ACTIVE'` + unaccent/ILIKE. | No existe endpoint de listado completo de activos. |
| Repository | `EstablishmentRepository.findAllByStatus(EstablishmentStatus)` ya existe (sin uso en código main) y es reusable para el listado. | — |
| Frontend | `EstablishmentDiscoveryPage` renderiza hero + `EstablishmentSearchBar` (autocomplete; query < 3 → sin resultados). No existe render de listado completo. | Falta listado completo, empty state y wiring de selección desde listado. |
| Selección / catálogo | `EstablishmentSearchBar.applySelection` → `setTenant` + `navigate('/catalogo'); CatalogPage` carga vía `dinerCatalogApi.getEstablishmentCatalog(tenant.id)` + polling de availability. | Comportamiento CA2 ya existente y reusable; el fix debe reutilizarlo sin duplicar. |

## 4. Requisitos funcionales derivados (borrador del fix)

- **FR-030**: El sistema MUST ofrecer el listado completo de establecimientos activos (`status = ACTIVE`) al comensal autenticado en la pantalla de descubrimiento. El backend correspondiente MUST devolver únicamente establecimientos activos.
- **FR-031**: Cuando el comensal selecciona un establecimiento del listado, el sistema MUST cargar el catálogo en tiempo real del nuevo tenant, limpiando el contexto previo (FR-004, FR-006) con el modal de confirmación de carrito si corresponde (FR-029).
- **FR-032**: El listado MUST presentar un estado vacío informativo cuando no existan establecimientos activos.

## 5. Diseño técnico propuesto (borrador)

### 5.1 Backend

- Endpoint: `GET /api/v1/establishments` → `200 OK` con `List<EstablishmentSummaryResponse>`.
- Servicio: `EstablishmentDiscoveryService.getAllActiveEstablishments()` delegando en `establishmentRepository.findAllByStatus(ACTIVE)` y mapeando a `EstablishmentSummaryResponse` (misma proyección del search; orden por nombre ASC).
- Seguridad: `GET /api/v1/establishments` → `hasRole('COMENSAL')`.
- Migraciones: sin cambios (reusa schema y seeds de V8).

### 5.2 Frontend

- API client: `dinerCatalogApi.getAllEstablishments()` → `GET /establishments`.
- Pantalla: render del listado completo en `/descubrimiento` (query vacía o < 3 caracteres, o disparo explícito del listado); items clickeables que reusan `handleSelectEstablishment` / `applySelection` (setTenant + navigate `/catalogo` + modal de switch).
- Estado vacío informativo cuando la lista de activos viene vacía.

### 5.3 Contratos

- `contracts/openapi.yaml`: agregar `GET /establishments` (operationId `getAllEstablishments`) con respuestas 200 (array de `EstablishmentSummaryResponse`), 401 y 403.

## 6. Decisiones pendientes (NEEDS CLARIFICATION)

1. **Path del endpoint**: `GET /api/v1/establishments` (recomendado) vs. sufijo explícito (`/all`). → Recomendado `GET /api/v1/establishments`.
2. **Disparador del listado**: query vacía o < 3 caracteres en la pantalla de descubrimiento. → Recomendado: query < 3 (o vacía) muestra listado completo; query ≥ 3 mantiene autocomplete.
3. **Orden**: nombre ASC (consistente con el search).
4. **Amplitud del cambio**: introducir FRs nuevas (FR-030..FR-032) vs. extender las existentes (FR-003) — decisión de producto por confirmar (grill-me).

## 7. Criterios de aceptación → tareas (borrador de trazabilidad)

| CA del fix | Comportamiento | Tareas derivadas |
|---|---|---|
| CA1 | Listado completo de activos al cargar | T052, T054, T055, T056 |
| CA2 | Selección → catálogo en tiempo real (CA2 de COM-1.5.1) | T056 (reusa flujo existente) |
| CA3 | Exclusión de pausados/inactivos | T052, T054, T056 |
| CA4 | Estado vacío informativo | T056 |

## 8. Tareas draft para implementación (continuación de tasks.md de la spec 008 — próximos T052+)

- [ ] **T052** [P] [US1/C-01] Unit test `EstablishmentDiscoveryServiceTest` — `getAllActiveEstablishments` returns only ACTIVE establishments and empty list when none are active.
- [ ] **T053** [US1/C-01] Web contract test `EstablishmentDiscoveryControllerTest` — `GET /api/v1/establishments` returns 200 with active list, 401 unauthenticated, 403 non-COMENSAL.
- [ ] **T054** [P] [US1/C-01] Implement `GET /api/v1/establishments` in `EstablishmentDiscoveryController` + `EstablishmentDiscoveryService` (GREEN).
- [ ] **T055** [P] [US1/C-01] Frontend API client `getAllEstablishments` in `dinerCatalogApi.ts` + mock handler in `tests/mocks/dinerCatalogHandlers.ts`.
- [ ] **T056** [US1/C-01] Frontend discovery page/listing: full-list rendering, selection wiring to tenant context + catalog, informative empty state.
- [ ] **T057** [US1/C-01] OpenAPI contract update, quickstart scenario, and spec delta (FR-030..FR-032) plus checklist alignment.

## 9. Verificación propuesta (borrador)

```bash
# Backend (TDD: service + controller)
cd easyeat/backend
./mvnw test -Dtest="EstablishmentDiscoveryServiceTest,EstablishmentDiscoveryControllerTest"

# Frontend
cd ../frontend
npm test -- src/test/catalog
```

Escenario de validación sugerido para `quickstart.md`: cargar `/descubrimiento` como comensal autenticado con varios establecimientos activos → ver listado completo; seleccionar uno → catálogo carga; con un establecimiento `PAUSED` no aparece; con el catálogo vacío de activos → estado vacío informativo.

---

## 10. Acuerdos de diseño finales (grill cerrado — 31/10/2026)

Tras el grill técnico exhaustivo, los acuerdos alcanzados:

1. **D1 — Endpoint del listado**: se eligió el endpoint dedicado `GET /api/v1/establishments` (opción 1 del grill). Respuesta: array plano JSON de `EstablishmentSummaryResponse`.
2. **D2 — Contrato de respuesta**: 200 con array plano; filtrado por `status = ACTIVE`; DTO existente reutilizado sin cambios.
3. **D3 — Seguridad**: el endpoint hereda el gate del módulo discovery: `hasRole('COMENSAL')` (401 sin sesión / 403 con rol no comensal).
4. **D4 — Rendimiento**: array plano sin paginación ni límite en el MVP.
5. **D5 — UX**: el listado completo es la vista principal de `/descubrimiento`, conviviendo con el banner de "Restaurante Activo".
6. **D6 — Criterios de aceptación del fix**: CA1 (listado al cargar), CA2 (selección → catálogo en tiempo real), CA3 (exclusión de pausados/inactivos), CA4 (estado vacío informativo).

**Decisiones cerradas durante el grill**: endpoint dedicado, shape de respuesta array plano, coexistencia con banner, granularidad sin paginar, gate de seguridad compartido.

**Decisiones abiertas posteriores**: orden determinista del array (recomendación: nombre ASC), copy/UX del estado vacío, manejo de transiciones de status en vivo.

## 11. Tareas definitivas del fix (borrador — continuación de tasks.md de la spec 008)

Basadas en los acuerdos D1–D6 y los criterios de aceptación CA1–CA4 del fix:

- [ ] **T052** [P] [US1/C-01] Unit test `EstablishmentDiscoveryServiceTest` — `getAllActiveEstablishments` devuelve solo establecimientos `ACTIVE` y lista vacía cuando no hay activos.
- [ ] **T053** [US1/C-01] Web contract test `EstablishmentDiscoveryControllerTest` — `GET /api/v1/establishments` 200 con lista activa, 401 sin autenticación, 403 con rol no `COMENSAL`.
- [ ] **T054** [P] [US1/C-01] Implementar `GET /api/v1/establishments` en `EstablishmentDiscoveryController` + `EstablishmentDiscoveryService` (GREEN).
- [ ] **T055** [P] [US1/C-01] Frontend API client `getAllEstablishments` en `dinerCatalogApi.ts` + mock handler en `tests/mocks/dinerCatalogHandlers.ts`.
- [ ] **T056** [US1/C-01] Frontend discovery page: render de listado completo, wiring de selección → tenant context + catálogo, estado vacío informativo.
- [ ] **T057** [US1/C-01] Actualizar contrato OpenAPI, escenario en `quickstart.md` y delta de spec (FR-030..FR-032) + checklist.

**Secuencia sugerida**: T052 → T053 (RED) → T054 (GREEN backend) → T055 (RED/GREEN frontend) → T056 (frontend) → T057 (docs/contrato).

**Notas del borrador**: documento de diseño previo a implementación. Ninguna tarea de implementación debe ejecutarse hasta aprobar este borrador y resolver las decisiones pendientes de la sección 6.
