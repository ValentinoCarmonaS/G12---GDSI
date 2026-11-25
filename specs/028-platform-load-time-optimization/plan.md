# Plan de implementación: optimización transversal de latencia, consultas en lote y carga instantánea en entornos de capa gratuita

**Branch**: `028-platform-load-time-optimization` | **Date**: 24/11/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/028-platform-load-time-optimization/spec.md`

---

## Summary

Este plan diseña la entrega full-stack transversal (Vertical Slice, Principio X) de la especificación `028-platform-load-time-optimization` para eliminar los cuellos de botella de latencia en el despliegue actual de EasyEat sobre capas gratuitas (Render Free en US-West Oregon + PostgreSQL Neon Free en São Paulo, ~170–200 ms de RTT intercontinental por consulta secuencial, 0.1 CPU, 512 MB RAM y pool de 5 conexiones), reduciendo la carga del catálogo del comensal y de las franjas de Checkout de ~10 s / 5–8 s a < 1,5 s / < 1 s en caliente y a < 100 ms en navegación repetida sin agregar dependencias ni migrar de infraestructura.

El enfoque técnico se estructura en cuatro frentes coordinados y desacoplados:
1. **Eliminación de consultas `N+1` a cardinalidad constante ($O(1)$)**: consultas agrupadas por lote (incluyendo `LEFT JOIN FETCH` sobre `Dish.restrictionCodes` en catálogo y proyección consolidada de recetas + ingredientes) y cálculo en pasada única con determinismo bit a bit del `ETag` SHA-256 en Catálogo y Motor de Disponibilidad (FR-001, FR-002); agregación única `GROUP BY timeSlotStart` en `PickupSlotService` (FR-003); cargas en lote en Mostrador (`CounterOrderController`), agregación única en resumen KDS (`/kitchen/dashboard/summary`), proyecciones JPQL livianas sin `restrictionCodes` + precarga en lote + `ETag`/`304` operativo en Gestión de Platos del Chef y `@BatchSize(size = 100)` en `Dish.restrictionCodes` como red de seguridad para consultas paginadas como `DishReviewInboxService` (FR-004).
2. **Cachés coherentes acotadas en servidor con Caffeine**: uso directo de `com.github.ben-manes.caffeine:caffeine` (ya declarado) para `UserSessionCache`, `DietaryTaxonomyCache`, `BrandingCache` y `OperatingScheduleCache` (< 2,5 MB en Heap), con invalidación sincrónica determinista en todos los puntos de escritura reforzada con `@EntityListeners` de JPA, y prohibición estricta de cachear stock o reservas en servidor (FR-005).
3. **Transporte liviano, binarios condicionales e infraestructura de base de datos**: proyección sin `BYTEA` con `ETag` + `If-None-Match` (`304 Not Modified`) preservando los `Cache-Control` contractuales exactos en fotos de platos, logotipos y avatares (FR-006); exposición CORS de `ETag`; compresión HTTP GZIP ≥ 1024 B para `application/json` y `application/problem+json`; carga diferida `loading="lazy"` + `decoding="async"` en tarjetas del catálogo; pool HikariCP precalentado (`minimum-idle = 5`); y migración Flyway con índices compuestos faltantes sobre `dishes`, `order_items` y `temporary_reservations` (FR-009).
4. **Coordinación de estado cliente (SWR y deduplicación en vuelo)**: `ClientSWRStore` provisto por `ClientCacheProvider` envolviendo a `AuthProvider` en `App.tsx` sin dependencias nuevas (con aislamiento automático para pruebas Vitest standalone y consumo opcional desde `AuthContext`), suprimiendo el doble `GET /api/v1/profile/me`, el disparo paralelo del listado plano heredado `/dishes` y el sondeo duplicado de disponibilidad en `t = 0 ms`, habilitando renderizado inmediato (< 100 ms) con revalidación silenciosa, `mediaVersions` por sesión para fotografías de platos y purga sincrónica en mutaciones, logout y `401` (FR-007, FR-008, FR-010).

---

## Technical Context

**Language/Version**: Java 21 (LTS) en backend; TypeScript 5.6.3 en frontend.

**Primary Dependencies**:
- Backend (sin dependencias nuevas): Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-mail`), PostgreSQL JDBC Driver, Flyway Core + `flyway-database-postgresql`, `com.github.ben-manes.caffeine:caffeine` (ya declarado; uso directo sin `spring-boot-starter-cache`), JJWT 0.12.6.
- Frontend (sin dependencias nuevas): React 18.3.1, React Router DOM 6.27.0, Axios 1.7.7, Tailwind CSS 3.4.13, Lucide React 0.453.0, Recharts 3.11.0, Vite 5.4.8.

**Storage**: PostgreSQL 16 (Neon Free en producción; Testcontainers `postgresql:16-alpine` en pruebas). Esquema gestionado por Flyway (`V1` a `V29` ya consumidas en la rama; nueva migración versionada de índices compuestos confirmada al autorar el archivo según `.specify/memory/migrations.md`, candidata actual `V30`). Almacenamiento binario de imágenes en tablas separadas `dish_photos`, `restaurant_logos` y `user_photos` con metadatos (`file_name`, `content_type`, `file_size`, `updated_at`) junto a `data BYTEA`.

**Testing**:
- Backend: JUnit 5, Mockito, Spring Security Test, Testcontainers PostgreSQL 1.21.4 (`mvn test` / `mvn verify` con gate JaCoCo ≥ 80 % LINE e INSTRUCTION).
- Frontend: Vitest 2.1.3, React Testing Library 16.0.1, `@testing-library/user-event` 14.5.2, jsdom 25.0.1 (`npm test`, `npm run lint`, `npm run build`).

**Target Platform**: Contenedor Linux en Render Free (US-West Oregon, 0.1 CPU, 512 MB RAM) conectado a PostgreSQL Neon Free (São Paulo, Brasil) + SPA servida en navegadores modernos de escritorio y móviles.

**Project Type**: Aplicación web full-stack multi-tenant (Backend REST API Spring Boot en `easyeat/backend` + Frontend SPA React en `easyeat/frontend`).

**Performance Goals**:
- Catálogo del Comensal (≥ 20 platos): ≤ 6 consultas SQL ($O(1)$), carga en caliente `< 1,5 s`, navegación repetida `< 100 ms` vía SWR (SC-001, SC-003).
- Sondeo de disponibilidad del catálogo: ≤ 4 consultas SQL ($O(1)$), `304 Not Modified` sin cuerpo cuando el `ETag` coincide (SC-001).
- Turnos de retiro en Checkout: exactamente `1` consulta agrupada `GROUP BY timeSlotStart`, carga en caliente `< 1 s` (SC-002, SC-003).
- Mostrador, resumen KDS y estado operativo del Chef: ≤ 3–4 consultas SQL constantes ($O(1)$) por ciclo (SC-002).
- Navegador en montaje de `/catalogo`: exactamente 1 `/profile/me`, 1 `/branding`, 1 `/catalog`, 0 `/dishes` heredado y 0 sondeos de disponibilidad en `t = 0 ms` (SC-004).

**Constraints**:
- Infraestructura gratuita inmutable: 0.1 CPU, 512 MB RAM, `maximum-pool-size = 5`, `minimum-idle = 5`, sin Redis, Kafka, CDN externo ni planes pagos (lineamiento 1).
- Invariabilidad total de flujos funcionales, permisos, rutas, esquemas JSON, códigos HTTP y contratos visuales de specs `001`–`027` para los cuatro roles (lineamiento 2, FR-010).
- Cero dependencias nuevas en backend y frontend; cero deuda técnica; alta cohesión y bajo acoplamiento (lineamientos 3, 4 y 5).
- Cero caché temporal de stock, reservas o disponibilidad en servidor; invalidación sincrónica inmediata de sesiones revocadas y metadatos editados (SC-005).
- Aislamiento de pruebas y contratos: 100 % de pruebas de integración, contratos HTTP y suites Vitest intactas (solo actualización de mocks N+1 en tests unitarios Mockito y de `NeonDataSourceConfigurationTest` —línea 56 `minimum-idle` y aserción obsoleta de `db/dev` en líneas 114–141— conforme a las decisiones Q1–Q4 y del análisis del 24/11/2026).

**Scale/Scope**: Optimización transversal de los 4 roles (`Comensal`, `Encargado del Mostrador`, `Chef`, `Administrador`) abarcando Catálogo, Disponibilidad, Checkout/Turnos, Mostrador, KDS, Gestión de Platos, Descubrimiento, Perfil, Branding, Horarios, Seguridad de Sesión, Binarios de Imagen, Transporte HTTP y Base de Datos (FR-001 a FR-010).

---

## Constitution Check

*GATE: Evaluación obligatoria contra `.specify/memory/constitution.md` (v2.1.1) — Pre-diseño y re-evaluación Post-diseño.*

| Principio Constitucional | Pre-diseño | Post-diseño | Evidencia y cumplimiento |
|---|---|---|---|
| **I. La especificación es la fuente de verdad** | PASS | PASS | Todo el diseño deriva estrictamente de US1–US4, FR-001 a FR-010, SC-001 a SC-006 y las aclaraciones del 24/11/2026 en `spec.md`, sin requisitos inventados. |
| **II. Especificación antes de implementación** | PASS | PASS | Se completa el flujo completo de `speckit-plan` (`research.md`, `data-model.md`, `contracts/*`, `quickstart.md`, `plan.md`) antes de generar `tasks.md` o escribir código productivo. |
| **III. Verificacion estratificada proporcional al riesgo** | PASS | PASS | `quickstart.md` y el plan cubren pruebas unitarias (con aserciones `never()` contra N+1), pruebas de contrato HTTP, pruebas de integración Testcontainers (incluyendo cardinalidad SQL e invalidación sincrónica) y pruebas Vitest/RTL, incluyendo todos los edge cases (`IN ()` vacío, revocación 401/403, fallo de red en background SWR, límite 512 MB). |
| **IV. Diseno simple, cohesivo y justificado** | PASS | PASS | Cada mecanismo tiene frontera cohesiva y justificación documentada en `research.md`: 4 cachés Caffeine directas (sin AOP/Spring Cache), consultas JPQL por lote en repositorios existentes, proyección liviana sin `BYTEA` y `ClientCacheProvider` nativo en React Context sin librerías externas. |
| **V. Alcance acotado** | PASS | PASS | Cero funcionalidades nuevas para los 4 roles, cero refactors cosméticos fuera de FR-001–FR-010; la actualización de la aserción obsoleta `db/dev` en `NeonDataSourceConfigurationTest` fue autorizada explícitamente el 24/11/2026 (decisión F3) para mantener `mvn verify` 100 % en verde. |
| **VI. Deuda tecnica controlada** | PASS | PASS | Ataca causas raíz (N+1 visibles y oculto de `restrictionCodes`, peticiones duplicadas, sesión sin caché, pool frío, ausencia de `ETag` en binarios, CORS sin `ETag`, falta de índices compuestos) y rechaza explícitamente código dual en `CounterOrderController` (decisión Q4). |
| **VII. Trazabilidad, consistencia y documentacion** | PASS | PASS | Trazabilidad 1:1 verificada entre `spec.md` (FR-001..FR-010, SC-001..SC-006, Edge Cases) → `research.md` (D-01..D-11) → `data-model.md` → `contracts/01..05` → `quickstart.md`. Cumple `.specify/memory/migrations.md` (reglas 2, 3 y 5). |
| **VIII. Evidencia sobre suposicion y escalacion** | PASS | PASS | Repositorio auditado íntegramente antes de diseñar; los 4 conflictos reales detectados en tests/contratos existentes (`NeonDataSourceConfigurationTest`, `DishChefPauseOpenApiContractTest` + CORS, `Cache-Control` vs refresco de imágenes y tests Mockito de `CounterOrderController`) fueron escalados y resueltos vía `grill-me` (Q1–Q4). |
| **IX. Ingenieria sobre conveniencia** | PASS | PASS | Se prioriza coherencia estricta (doble capa de invalidación servicio + JPA `@EntityListeners`, cero caché de stock, `validateStatus` para `304`, aislamiento de `ClientCacheProvider` en tests) sobre atajos frágiles. |
| **X. Integridad vertical full-stack (Vertical Slices)** | PASS | PASS | Cada historia coordina extremo a extremo BD/índices, backend REST/caché/transporte y cliente web React/Axios/DOM para los cuatro roles operativos. |
| **XI. Gobernanza visual por rol y refactorizacion UI no regresiva** | PASS | PASS | No se modifican estilos, tipografía, paletas ni contratos `.DESIGN.md` (`comensal`, `mostrador`, `chef`, `admin`), ni se alteran esqueletos, flujos visuales ni aserciones de suites Vitest `001`–`027`. Solo se añaden atributos HTML estándar `loading="lazy"` y `decoding="async"` en `DishCard.tsx` (FR-009), coordinación de estado transparente en hooks/contexto y refresco `?v=` controlado por sesión; T072 audita todos los componentes UI tocados vía Impeccable `/critique` y WCAG 2.2 AA contra sus `.DESIGN.md` aplicables (decisión D1). |

**Resultado de compuertas**: PASS en Pre-diseño y PASS en Post-diseño. Ninguna violación constitucional sin justificar.

---

## Phase 0 — Outline & Research (Completada)

Todas las incógnitas técnicas del contexto fueron auditadas en el repositorio y resueltas en [research.md](./research.md) bajo el formato estricto `Decision / Rationale / Alternatives considered`:
- **D-01**: Eliminación de `N+1` visible y oculto (`Dish.restrictionCodes` vía `LEFT JOIN FETCH`) y consolidación `recipes LEFT JOIN ingredients` en el catálogo (FR-001).
- **D-02**: Pasada única en `DishAvailabilityEngine` y determinismo bit a bit del `ETag` SHA-256 compatible con `DinerAvailabilityControllerTest` (FR-002).
- **D-03**: Agregación única `GROUP BY timeSlotStart` en `PickupSlotService` sobre `idx_orders_slot_capacity` (FR-003).
- **D-04**: Lotes en `CounterOrderController` (Q4), agregación única en KDS, lote en `DishService`, preservación del `ETag` `hashCode()` + `Access-Control-Expose-Headers: ETag` + `validateStatus` 304 en `dishApi.ts` (FR-004, Q2).
- **D-05 / D-06**: Uso directo de Caffeine (< 2,5 MB en Heap), política de claves/TTL e invalidación sincrónica en dos capas (servicio + `@EntityListeners`) para `UserSessionCache`, `DietaryTaxonomyCache`, `BrandingCache` y `OperatingScheduleCache` (FR-005).
- **D-07**: Proyección liviana sin `BYTEA`, `ETag` + `304 Not Modified`, preservación de `Cache-Control` y firma 1-arg en `ProfileAvatarController`, y refresco visual en cliente (FR-006, Q3).
- **D-08 / D-09**: `ClientSWRStore` + `ClientCacheProvider` en `App.tsx`, eliminación de peticiones duplicadas (`/profile/me`, `/dishes`, sondeo `t = 0 ms`), estabilidad visual SWR y purga sincrónica en mutaciones/logout/`401` (FR-007, FR-008).
- **D-10**: Compresión GZIP ≥ 1024 B, `loading="lazy"` + `decoding="async"`, HikariCP `minimum-idle: 5` (Q1) y migración Flyway de índices compuestos según `.specify/memory/migrations.md` (FR-009).
- **D-11**: Guardas contra `IN ()` vacío y gobernanza estricta de pruebas (FR-010).

---

## Phase 1 — Design, Data Model, Contracts & Validation (Completada)

Artefactos de diseño generados:
- **Modelo de datos**: [data-model.md](./data-model.md) — modela las 4 entidades clave de `spec.md`, las proyecciones de lectura en lote y medios sin `BYTEA`, y la migración de índices compuestos.
- **Contratos de interfaz**:
  - [contracts/01-catalogo-y-disponibilidad.md](./contracts/01-catalogo-y-disponibilidad.md)
  - [contracts/02-turnos-retiro-checkout.md](./contracts/02-turnos-retiro-checkout.md)
  - [contracts/03-operacion-mostrador-kds-chef.md](./contracts/03-operacion-mostrador-kds-chef.md)
  - [contracts/04-medios-binarios-y-transporte.md](./contracts/04-medios-binarios-y-transporte.md)
  - [contracts/05-estado-cliente-swr-y-deduplicacion.md](./contracts/05-estado-cliente-swr-y-deduplicacion.md)
- **Guía de validación extremo a extremo**: [quickstart.md](./quickstart.md)

---

## Matrices de trazabilidad obligatoria

### 1. Mapa de requisitos funcionales (FR-001 a FR-010 → artefactos del plan)

| Requisito | Historia | Decisión (`research.md`) | Modelo (`data-model.md`) | Contrato (`contracts/*`) | Validación (`quickstart.md`) |
|---|---|---|---|---|---|
| **FR-001** (Catálogo O(1) ≤ 6 consultas y determinismo) | US1 | D-01, D-11 | §1 `BatchedCatalogSnapshot` | `01-catalogo-y-disponibilidad.md §1–§2` | §3.1, §4 US1 |
| **FR-002** (Disponibilidad pasada única y `ETag` SHA-256) | US1 | D-02, D-11 | §1 `BatchedCatalogSnapshot` | `01-catalogo-y-disponibilidad.md §3` | §3.1, §4 US1 |
| **FR-003** (Turnos Checkout consulta única `GROUP BY`) | US2 | D-03 | §2 `TimeSlotOccupancyAggregate` | `02-turnos-retiro-checkout.md` | §3.1, §4 US2 |
| **FR-004** (Lotes Mostrador, KDS agregado, Chef lote + `ETag`) | US3 | D-04, D-11 | §5.2, §5.3 | `03-operacion-mostrador-kds-chef.md` | §3.1, §4 US3 |
| **FR-005** (Cachés acotadas Caffeine + invalidación sincrónica) | US4 | D-05, D-06 | §3 `Coherent Low-Volatility Cache Entry` | `05-estado-cliente-swr-y-deduplicacion.md §1` | §3.1, §4 US4 |
| **FR-006** (`ETag`/`304` sin `BYTEA` y `Cache-Control` exactos) | US4 | D-07 | §5.1 `BinaryMediaMetadataProjection` | `04-medios-binarios-y-transporte.md §1–§3` | §3.1, §4 US4 |
| **FR-007** (Dedupe en vuelo, fin duplicados y diferimiento `t=0`) | US1 | D-08 | §4 `ClientSWRStore` | `01-catalogo-y-disponibilidad.md`, `05-estado-cliente-swr-y-deduplicacion.md` | §3.2, §4 US1 |
| **FR-008** (SWR < 100 ms, esqueletos/errores y purga en cliente) | US1, US4 | D-08, D-09 | §4 `ClientSWRStore` | `05-estado-cliente-swr-y-deduplicacion.md §2` | §3.2, §4 US1, §4 US4 |
| **FR-009** (`loading="lazy"`, GZIP ≥ 1024 B, pool `5`, índices) | US1, US4 | D-10 | §6 Migración Flyway índices | `04-medios-binarios-y-transporte.md §4` | §3.1, §3.2, §4 US4 |
| **FR-010** (Invariante de contratos, roles y gobernanza de tests) | US1–US5 | D-02, D-04, D-07, D-08, D-10, D-11, D-12 | Todas las secciones | Notas FR-010 en `01`–`05` | §3.1, §3.2, §4 |
| **FR-011** (`paidAt` determinista, backfill `V31`, coherencia en vivo Admin/KDS/Comensal) | US5 | D-12 | §4, §5.2, §7 | `03-operacion-mostrador-kds-chef.md §2`, `05-estado-cliente-swr-y-deduplicacion.md §1E–§1F` | §3.1, §3.2, §4 US5 |

### 2. Mapa de criterios de éxito (SC-001 a SC-007 → metas de diseño)

| Criterio | Meta medible | Garantía estructural del diseño |
|---|---|---|
| **SC-001** | Catálogo ≤ 6 SQL; Disponibilidad ≤ 4 SQL (`304` con sesión cacheada) | `LEFT JOIN FETCH d.restrictionCodes` + join consolidado `recipes LEFT JOIN ingredients` + `findDishIdsWithPhoto` + `sumActiveReservedPortionsByDishIds` + `UserSessionCache` |
| **SC-002** | Checkout = 1 consulta agrupada; Mostrador/KDS/Chef ≤ 3–4 SQL constantes | `GROUP BY timeSlotStart`, `findAllByIdWithItems`/`findAllById`/`findUserIdsWithPhoto`, `SUM(CASE WHEN ...)` único en KDS y carga en lote en `DishService` |
| **SC-003** | Carga caliente `< 1,5 s` (Catálogo) / `< 1 s` (Checkout); visita repetida `< 100 ms` | Reducción de ~188 viajes (~35 s teóricos / ~10 s con paralelismo) a 4–5 viajes (~0,8 s RTT) + `minimum-idle = 5` (0 TLS nuevos) + GZIP + `ClientSWRStore` sincrónico (< 100 ms) |
| **SC-004** | En montaje de `/catalogo`: 1 `/profile/me`, 1 `/branding`, 1 `/catalog`, 0 `/dishes`, 0 sondeo en `t = 0 ms` | Deduplicación `ClientSWRStore`, `WorkspaceLayout` limitado a `COMENSAL`, `useProfile({ enabled: isAuthenticated, loadTaxonomy: false })`, `useCatalog` con `enabled: !tenant?.id` y `useRealtimeAvailability` con `initialAvailability` + `deferInitialPoll: true` |
| **SC-005** | 100 % coherencia inmediata en stock, pausas, turnos, sesiones, branding y horarios | Cero caché de stock/reservas/turnos en servidor; invalidación sincrónica + `@EntityListeners` en `User`, `RestaurantBranding` y `EstablishmentOperatingSchedule`; purga sincrónica cliente en mutaciones, logout y `401` |
| **SC-006** | Contenedor ≤ 512 MB RAM y 100 % suites backend/frontend en verde | Presupuesto total Caffeine `< 2,5 MB` + 5 conexiones JDBC `< 5 MB` + cero lectura `BYTEA` en `304` + aislamiento de `ClientCacheProvider` en Vitest + preservación de firmas y contratos |
| **SC-007** | 100 % ventas acreditadas con `paid_at` no nulo visibles al instante en Admin; KDS y Comensal sincronizados con BD | Asignación atómica de `paidAt` en `CheckoutConfirmationServiceImpl` y `OrderConfirmationTrackingService` + migración `V31` de backfill + `Clock` `America/Argentina/Buenos_Aires` en `KitchenKdsController` + sincronización de 4 KPIs KDS en cada acción/ciclo + revalidación silenciosa en `FinancialHealthPanel`, `DishRankingTable` y `MyOrdersSection` |

### 3. Mapa de Edge Cases → decisión de diseño

| Edge Case (`spec.md`) | Decisión y control de diseño |
|---|---|
| Invalidación inmediata ante cambio de stock, pausa del Chef o nueva reserva en Checkout | Stock, reservas temporales, `pausedByChef` y ocupación de turnos **nunca** entran en cachés Caffeine del servidor; se calculan en vivo con consultas en lote $O(1)$ (D-01, D-02, D-03, `data-model.md §1–§3`). |
| Invalidación inmediata de sesión única y suspensión de personal (`401` / `403`) | Invalidación sincrónica en los 14 puntos de mutación de `User` + red de seguridad `@EntityListeners` sobre `User` que desaloja `UserSessionCache` ante cualquier `save`/`delete` directo (D-06, `data-model.md §3`). |
| Establecimientos sin platos publicados, platos sin receta o sin fotografías (`IN ()` vacío) | Guarda explícita de colección vacía antes de cada llamada `IN` en repositorios por lote; preservación exacta de lista vacía, stock `0` / `isAvailable = false` y `photoUrl = null` (D-11, `data-model.md §1`). |
| Concurrencia de múltiples componentes solicitando el mismo recurso en cliente | Mapa `inFlight` por clave en `ClientSWRStore` que comparte la misma `Promise` en curso y notifica a todos los suscriptores al resolverse (D-08, `contracts/05 §2`). |
| Limpieza de estado en memoria del cliente al cerrar sesión o cambiar de usuario | `ClientCacheProvider.clearAll()` invocado sincrónicamente en `AuthContext` (`logout`, `clearAuth`, cambio de `user.id` en `setToken`) y en el listener global `'easyeat:session-expired'` ante `401` (D-09, `data-model.md §4`). |
| Preservación estricta del límite de 512 MB en contenedor gratuito | `maximumSize` estricto (`2000`/`1`/`200`/`200`, `< 2,5 MB` Heap), proyección sin `BYTEA` en `304`, `maximum-pool-size = minimum-idle = 5` y exclusión de imágenes en GZIP (D-05, D-07, D-10). |

---

## Project Structure

### Documentation (this feature)

```text
specs/028-platform-load-time-optimization/
├── spec.md                                           # Especificación funcional fuente de verdad
├── plan.md                                           # Este archivo (/speckit-plan)
├── research.md                                       # Fase 0: decisiones D-01 a D-11
├── data-model.md                                     # Fase 1: entidades, cachés, proyecciones e índices
├── quickstart.md                                     # Fase 1: guía de validación ejecutable E2E
├── contracts/                                        # Fase 1: contratos de interfaz
│   ├── 01-catalogo-y-disponibilidad.md
│   ├── 02-turnos-retiro-checkout.md
│   ├── 03-operacion-mostrador-kds-chef.md
│   ├── 04-medios-binarios-y-transporte.md
│   └── 05-estado-cliente-swr-y-deduplicacion.md
└── tasks.md                                          # Fase 2 (/speckit-tasks — NO generado en este comando)
```

### Source Code (repository root)

```text
easyeat/
├── backend/
│   ├── src/main/java/com/easyeat/
│   │   ├── auth/
│   │   │   ├── cache/UserSessionCache.java                         # Nuevo: caché Caffeine acotada (max 2000, 5m) + listener JPA
│   │   │   ├── domain/User.java                                    # @EntityListeners para invalidación determinista
│   │   │   ├── security/JwtAuthenticationFilter.java               # Lectura vía UserSessionCache (@Autowired required=false)
│   │   │   ├── service/AuthService.java                            # Evict sincrónico en register/login/logout
│   │   │   ├── service/PasswordResetService.java                   # Evict sincrónico en confirmPasswordReset
│   │   │   └── controller/FirstLoginPasswordController.java        # Evict sincrónico en changeFirstLoginPassword
│   │   ├── catalog/
│   │   │   ├── controller/DinerCatalogController.java              # Compatibilidad disponibilidad + ETag sin romper mocks
│   │   │   ├── controller/DinerBrandingController.java             # Logo ETag + If-None-Match (304 sin BYTEA, Cache-Control intacto)
│   │   │   ├── cache/BrandingCache.java                            # Nuevo: caché Caffeine acotada (max 200, 15m) + listener JPA
│   │   │   ├── domain/Dish.java                                    # @BatchSize(size = 100) en restrictionCodes (red seguridad N+1)
│   │   │   ├── domain/RestaurantBranding.java                      # @EntityListeners para invalidación determinista
│   │   │   ├── repository/DishRepository.java                      # JPQL LEFT JOIN FETCH d.restrictionCodes + proyecciones livianas Chef + findByIdIn existente
│   │   │   ├── repository/RestaurantLogoRepository.java            # Proyección liviana de metadatos sin BYTEA
│   │   │   ├── service/CatalogComplianceService.java               # Lectura de taxonomía vía DietaryTaxonomyCache
│   │   │   ├── service/DinerCatalogService.java                    # Catálogo O(1) ≤ 6 SQL + ensamblado canónico determinista
│   │   │   ├── service/DishAvailabilityEngine.java                 # Pasada única en lote + ETag SHA-256 bit a bit idéntico
│   │   │   ├── service/RestaurantBrandingService.java              # Lectura/evict BrandingCache
│   │   │   ├── service/BrandingLogoStorageService.java             # updatedAt explícito + proyección metadatos + evict BrandingCache
│   │   │   └── service/EstablishmentDiscoveryService.java          # Lectura de horarios desde OperatingScheduleCache
│   │   ├── common/
│   │   │   └── http/BinaryMediaETagSupport.java                    # Nuevo: proyección liviana y cálculo determinista ETag sin BYTEA
│   │   ├── config/
│   │   │   └── SecurityConfig.java                                 # Exponer cabecera ETag en CORS (Access-Control-Expose-Headers)
│   │   ├── dish/
│   │   │   ├── controller/DishController.java                      # ETag tolerante a comillas en operational-status (fórmula intacta)
│   │   │   ├── controller/DishPhotoController.java                 # Foto ETag + If-None-Match (304 sin BYTEA, Cache-Control intacto)
│   │   │   ├── repository/DishRecipeIngredientRepository.java      # Consulta lote por dishIds + join consolidado con Ingredient
│   │   │   ├── repository/DishPhotoRepository.java                 # findDishIdsWithPhoto + proyección metadatos sin BYTEA
│   │   │   ├── service/DishService.java                            # Lotes O(1) en listDishes y getOperationalStatus
│   │   │   └── service/DishPhotoService.java                       # updatedAt explícito + proyección metadatos
│   │   ├── inventory/
│   │   │   └── repository/IngredientRepository.java                # findByIdInAndTenantId (lote multi-tenant)
│   │   ├── operations/
│   │   │   ├── cache/OperatingScheduleCache.java                   # Nuevo: caché semanal por establishment (max 200, 15m) + listener JPA
│   │   │   ├── domain/EstablishmentOperatingSchedule.java          # @EntityListeners para invalidación determinista
│   │   │   ├── service/OperatingScheduleService.java               # Lectura/evict OperatingScheduleCache
│   │   │   ├── service/DinerTimeSlotService.java                   # Lectura de horarios vía OperatingScheduleCache
│   │   │   └── service/SlotCapacityService.java                    # Lectura de horarios vía OperatingScheduleCache
│   │   ├── order/
│   │   │   ├── controller/CounterOrderController.java              # Lotes O(1) (findAllByIdWithItems, findAllById, findUserIdsWithPhoto)
│   │   │   ├── controller/KitchenKdsController.java                # Resumen KDS en 1 consulta agregada
│   │   │   ├── repository/OrderRepository.java                     # Consulta agrupada GROUP BY timeSlotStart
│   │   │   ├── repository/TemporaryReservationRepository.java      # Consulta agregada sumActiveReservedPortionsByDishIds
│   │   │   ├── repository/KdsTicketRepository.java                 # Consulta única SUM(CASE WHEN ...) para dashboard summary
│   │   │   ├── service/OrderService.java                           # Lectura de horarios vía OperatingScheduleCache
│   │   │   └── service/PickupSlotService.java                      # Uso de agregación única + OperatingScheduleCache
│   │   ├── profile/
│   │   │   ├── cache/DietaryTaxonomyCache.java                     # Nuevo: caché Caffeine de taxonomía dietaria (max 1, 60m)
│   │   │   ├── controller/ProfileAvatarController.java             # Avatar ETag + If-None-Match vía RequestContextHolder (firma intacta)
│   │   │   ├── repository/UserPhotoRepository.java                 # findUserIdsWithPhoto + proyección metadatos sin BYTEA
│   │   │   ├── service/DietaryRestrictionService.java              # Lectura vía DietaryTaxonomyCache
│   │   │   ├── service/ProfileService.java                         # Evict UserSessionCache en mutaciones de perfil
│   │   │   ├── service/EmailChangeService.java                     # Evict UserSessionCache en flujo de cambio de email
│   │   │   └── service/UserPhotoService.java                       # updatedAt explícito + proyección metadatos
│   │   └── staff/
│   │       └── service/StaffService.java                           # Evict UserSessionCache en register/update/updateStatus
│   ├── src/main/resources/
│   │   ├── application.yml                                         # server.compression (GZIP ≥ 1024 B) + hikari.minimum-idle: 5
│   │   └── db/migration/
│   │       └── V<next>__add_performance_composite_indexes.sql      # Candidata V30 confirmada al autorar (regla 2 migrations.md)
│   └── src/test/java/com/easyeat/
│       └── ...                                                     # Mocks lote en tests unitarios N+1 + línea 56 NeonDataSourceConfigurationTest + nuevos tests Spec 028
│
└── frontend/
    └── src/
        ├── App.tsx                                                 # Montaje único de <ClientCacheProvider> envolviendo a <AuthProvider>
        ├── api/
        │   └── dishApi.ts                                          # validateStatus aceptando 304 + marca notModified en getOperationalStatus
        ├── context/
        │   ├── AuthContext.tsx                                     # Purga sincrónica opcional vía useContext(ClientCacheContext) en logout/clearAuth/cambio usuario
        │   └── ClientCacheContext.tsx                              # Nuevo: ClientSWRStore + ClientCacheProvider (dedupe + SWR + mediaVersions + aislamiento tests)
        ├── hooks/
        │   ├── useProfile.ts                                       # Opciones { enabled, loadTaxonomy } + ClientSWRStore + invalidación mutaciones
        │   ├── useCatalog.ts                                       # Opción { enabled } para respaldo heredado explícito
        │   ├── useRealtimeAvailability.ts                          # Opción deferInitialPoll para diferir primer sondeo tras /catalog
        │   ├── useChefDishesSync.ts                                # Guardia segura ante 304 notModified para no vaciar items
        │   └── useRestaurantBranding.ts                            # Dedupe + SWR compartido vía ClientSWRStore
        ├── components/
        │   ├── catalog/
        │   │   ├── DishCard.tsx                                    # loading="lazy" + decoding="async" + versión opcional mediaVersions['dish:{id}']
        │   │   ├── DishDetailModal.tsx                             # Versión opcional mediaVersions['dish:{id}'] en imagen de detalle
        │   │   └── EstablishmentSearchBar.tsx                      # SWR en listado de establecimientos (establishments:all)
        │   ├── common/
        │   │   └── WorkspaceLayout.tsx                             # Perfil solo para COMENSAL vía ClientSWRStore + branding admin compartido
        │   ├── layout/
        │   │   └── RoleLayoutShell.tsx                             # Estabilización de versión de avatar por evento easyeat:avatar-changed
        │   └── profile/
        │       └── PersonalDataForm.tsx                            # Estabilización de versión de avatar + invalidación en mutación
        └── pages/
            ├── CatalogPage.tsx                                     # SWR catálogo + sin /dishes paralelo + seed initialAvailability + deferInitialPoll + logo ?v=
            ├── ChefDishEditorPage.tsx                              # Registro de mediaVersions['dish:{id}'] al subir/eliminar foto (junto a chefDishPersistence.ts)
            └── AdminBrandingPage.tsx                               # Compartir/invalidar branding en ClientSWRStore + logo ?v=
```

**Structure Decision**: Se adopta la estructura monorepo full-stack existente (`easyeat/backend` + `easyeat/frontend`) sin crear módulos raíz nuevos ni introducir dependencias externas. Las cuatro cachés de servidor se ubican cohesivamente en subpaquetes `cache/` dentro de sus dominios propietarios (`auth`, `catalog`, `operations`, `profile`), y el almacén cliente se encapsula en `easyeat/frontend/src/context/ClientCacheContext.tsx`.

---

## Complexity Tracking

> Registro de las 4 decisiones de compatibilidad y excepciones explícitamente consensuadas y aprobadas mediante `grill-me` el 24/11/2026 para preservar coherencia total entre FR-001–FR-010 y la base de código existente. No existen violaciones constitucionales sin justificar.

| Decisión consensuada (24/11/2026) | Motivo técnico | Alternativa más simple descartada y razón |
|---|---|---|
| **Q1 / F3 — Actualización de `NeonDataSourceConfigurationTest`** (`minimum-idle` de `1` a `5` en L56 y alineación de `flywayLocations_shouldIncludeMigrationAndDev` en L114–141) | FR-009 exige explícitamente `minimum-idle: 5` en el pool de conexiones para eliminar negociaciones TLS intercontinentales de ~450 ms; además, el commit `73b4bf98` (24/11/2026) retiró intencionalmente `classpath:db/dev` de `application.yml` dejando obsoleta la aserción de L114–141 que bloquearía T016 y `mvn verify` (SC-006). | Mantener default `1` en `application.yml` incumpliría FR-009, y dejar rota la aserción obsoleta de `db/dev` impediría verificar `mvn verify` 100 % en verde sin filtros artificiales. |
| **Q2 — Preservación del `ETag` `hashCode()` en `DishController.getOperationalStatus` + CORS `exposedHeaders("ETag")` + `validateStatus` 304** | `DishChefPauseOpenApiContractTest:136-143` fija contractualmente la fórmula `hashCode()`; además, en producción cross-origin Render el navegador no podía leer `ETag` sin `Access-Control-Expose-Headers: ETag`. | Migrar el `ETag` de `operational-status` a SHA-256 rompería un test de contrato HTTP protegido por FR-010 sin ganancia funcional adicional. |
| **Q3 — Refresco inmediato de imágenes en frontend manteniendo `Cache-Control` y URLs JSON exactas en backend** | Tres tests de contrato asertan `Cache-Control` exactos (`max-age=86400`/`3600`), ocho tests asertan `photoUrl`/`logoUrl` sin query params y `avatarUtils.test.ts` aserta `token=` + `_t=\d+`. | Añadir `no-cache` a `Cache-Control` rompería los tres tests de contrato y forzaría revalidación intercontinental de 185 ms por cada imagen en cada carga. |
| **Q4 — Inclusión de `CounterOrderControllerIntegrationTest` y `CounterOrderControllerSecurityCodeTest` en la excepción Mockito de FR-010** | Pese al nombre del archivo, ambos son tests unitarios `@ExtendWith(MockitoExtension.class)` con `MockMvcBuilders.standaloneSetup` que mockean repositorios `N+1` (`findById`, `existsByUserId`). | Mantener un fallback dual en `CounterOrderController` hacia métodos individuales introduciría código muerto y deuda técnica en producción contra el lineamiento 4. |
