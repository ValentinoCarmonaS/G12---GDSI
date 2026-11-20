# Tareas de implementacion: control de acceso de personal, salud financiera y ranking de platos

**Entrada**: Documentos de diseno desde `/specs/023-staff-access-bi-dish-ranking/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)  
**Prerrequisitos**: Plan tecnico aprobado, modelo de datos definido, contratos establecidos.  
**Estrategia de pruebas**: TDD estricto (Red-Green-Refactor). Para cada funcionalidad, se escriben primero las pruebas automatizadas (RED) que deben fallar antes de escribir el codigo de produccion (GREEN).

---

## Formato: `[ID] [P?] [Story] Descripcion con ruta de archivo`

- **[P]**: Ejecutable en paralelo (archivos independientes, sin dependencias con tareas pendientes).
- **[Story]**: Historia de usuario a la que pertenece (`[US1]`, `[US2]`, `[US3]`). Omitido en fases de Setup, Foundational y Polish.
- Todas las rutas son relativas al repositorio.

---

## Phase 1: Setup (Infraestructura compartida y base)
> **GitHub Issue**: [#434](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/434)

**Proposito**: Inicializacion de scripts de base de datos, tipos compartidos y clientes HTTP base.

- [x] T001 [P] Escribir RED test de migracion Flyway en `easyeat/backend/src/test/java/com/easyeat/order/OrderPaidAtMigrationTest.java` (localiza el script por descripcion `add_paid_at_to_orders`, sin version en el nombre de la clase, regla 5 de `.specify/memory/migrations.md`), crear el script `easyeat/backend/src/main/resources/db/migration/V29__add_paid_at_to_orders.sql` agregando `orders.paid_at TIMESTAMPTZ NULL`, backfill desde `payments.approved_at`, e indice parcial `idx_orders_analytics`, y registrar `V29` en `.specify/memory/migrations.md` (regla 3).
- [x] T002 [P] Escribir RED test de tipos frontend en `easyeat/frontend/tests/types/analyticsTypes.test.ts`, instalar dependencias de graficos Recharts en `easyeat/frontend/package.json`, extender `easyeat/frontend/src/types/staff.ts` con `StaffStatusUpdateRequest` y crear `easyeat/frontend/src/types/analytics.ts` con `FinancialHealthGranularity`, `FinancialHealthResponse`, `DishRankingGranularity` y `DishRankingItemResponse`.
- [x] T003 [P] Escribir RED test para clientes HTTP de frontend en `easyeat/frontend/tests/api/analyticsApi.test.ts` y crear `easyeat/frontend/src/api/analyticsApi.ts` exponiendo `getFinancialHealth(granularity)` y `getDishRanking(granularity)` con cabeceras `Cache-Control: no-cache`.

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)
> **GitHub Issue**: [#435](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/435)

**Proposito**: Modelos de dominio y capas de persistencia centrales. Ninguna historia de usuario puede completarse sin esta fase.

- [x] T004 [P] Escribir RED test unitario en `easyeat/backend/src/test/java/com/easyeat/order/domain/OrderPaidAtTest.java` verificando mapeo del atributo `paidAt` en la entidad `Order`.
- [x] T005 Implementar campo `Instant paidAt` con getter/setter en `easyeat/backend/src/main/java/com/easyeat/order/domain/Order.java` y asignar `order.setPaidAt(Instant.now())` en `easyeat/backend/src/main/java/com/easyeat/order/service/PaymentWebhookService.java` al transicionar la orden a `OrderStatus.PAID`.
- [x] T006 [P] Escribir test de integracion con Testcontainers en `easyeat/backend/src/test/java/com/easyeat/order/repository/OrderPaidAtSchemaIntegrationTest.java` verificando la aplicacion real de `V29`, existencia de columna `paid_at`, backfill de ordenes previas y creacion del indice parcial compuesto `idx_orders_analytics`.
- [x] T007 [P] Escribir RED test unitario de repositorio en `easyeat/backend/src/test/java/com/easyeat/analytics/repository/AnalyticsRepositoryTest.java` para el filtro de ordenes concretadas (`status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')`) y delimitacion de semi-intervalo temporal `[start, end)`.
- [x] T008 Crear paquete `com.easyeat.analytics.repository` e implementar `AnalyticsRepository.java` en `easyeat/backend/src/main/java/com/easyeat/analytics/repository/AnalyticsRepository.java` con consultas agregadas de ingresos totales, cantidad de pedidos y unidades vendidas por plato.

**Punto de control**: Fundamentos listos. Las historias de usuario pueden proceder de manera independiente.

---

## Phase 3: User Story 1 - Baja y suspension de acceso de exempleados (Prioridad: P1) 🎯 MVP
> **GitHub Issue**: [#436](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/436)

**Objetivo**: Permitir al administrador suspender o inactivar personal de su establecimiento, bloqueando atómicamente la suspension si es el unico administrador activo (orfandad administrativa) y rechazando cualquier login posterior con HTTP 401 generico indistinguible de credenciales erroneas, preservando la auditoria historica.

**Criterio de prueba independiente**: Suspender un cocinero activo y comprobar que el login posterior con credenciales validas falla con 401 generico; intentar suspender al unico administrador activo y comprobar rechazo atomico con 409 Conflict; verificar que los pedidos y comandas historicas del empleado suspendido siguen visibles e inalterados.

### Pruebas para User Story 1 (TDD RED)

- [x] T009 [P] [US1] Escribir RED test unitario en `easyeat/backend/src/test/java/com/easyeat/auth/service/AuthServiceSuspendedAccountTest.java` comprobando que `AuthService.login` con cuenta `SUSPENDED` o `UNVERIFIED` ejecuta verificacion de contrasena post-BCrypt, registra `ACCOUNT_SUSPENDED` en `SecurityAuditLogger` y lanza `InvalidCredentialsException` con mensaje `"The provided email or password is incorrect."` (HTTP 401).
- [x] T010 [P] [US1] Escribir RED test unitario en `easyeat/backend/src/test/java/com/easyeat/staff/service/StaffServiceStatusUpdateTest.java` para `updateStaffStatus` verificando mutacion de estado a `SUSPENDED` / `ACTIVE`, proteccion contra orfandad cuando `countByTenantIdAndRoleAndStatus <= 1` (lanza `OrphanedAdminException` 409) y exito si existen $\ge 2$ administradores activos.
- [x] T011 [P] [US1] Escribir RED test de integracion con Testcontainers en `easyeat/backend/src/test/java/com/easyeat/staff/controller/StaffStatusControllerIntegrationTest.java` para `PATCH /api/v1/admin/staff/{id}/status` (200 OK, 404 si no existe, 403 si pertenece a otro tenant, 409 si es unico admin activo, y prueba de carrera concurrente donde dos admins intentan suspenderse mutuamente).
- [x] T012 [P] [US1] Escribir RED test frontend en `easyeat/frontend/tests/components/StaffStatusModal.test.tsx` para el modal de confirmacion de suspension y captura de error 409 Conflict de orfandad administrativa.
- [x] T013 [P] [US1] Escribir RED test frontend en `easyeat/frontend/tests/components/StaffRosterTableActions.test.tsx` para el boton de accion "Suspender" / "Activar" en la tabla de nómina del establecimiento.

### Implementacion para User Story 1 (GREEN)

- [x] T014 [US1] Modificar `AuthService.login` en `easyeat/backend/src/main/java/com/easyeat/auth/service/AuthService.java` para validar el estado del usuario post-BCrypt y lanzar `InvalidCredentialsException` (HTTP 401) en lugar de `AccountSuspendedException`.
- [x] T015 [US1] Crear record DTO `StaffStatusUpdateRequest` en `easyeat/backend/src/main/java/com/easyeat/staff/dto/StaffStatusUpdateRequest.java` y agregar consulta con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `easyeat/backend/src/main/java/com/easyeat/staff/repository/StaffUserRepository.java` para los administradores activos del tenant.
- [x] T016 [US1] Implementar metodo `updateStaffStatus(UUID id, StaffStatusUpdateRequest request)` en `easyeat/backend/src/main/java/com/easyeat/staff/service/StaffService.java` con validacion de orfandad administrativa bajo transaccion `@Transactional`.
- [x] T017 [US1] Exponer endpoint `PATCH /api/v1/admin/staff/{id}/status` en `easyeat/backend/src/main/java/com/easyeat/staff/controller/StaffController.java` protegido con roles `ADMINISTRADOR` / `ADMIN`.
- [x] T018 [US1] Extender `easyeat/frontend/src/api/staffApi.ts` implementando el metodo `updateStaffStatus(id: string, status: StaffAccountStatus)`.
- [x] T019 [US1] Implementar componente modal `StaffStatusModal.tsx` en `easyeat/frontend/src/components/staff/StaffStatusModal.tsx` con mensaje de advertencia y gestion de error 409.
- [x] T020 [US1] Integrar modal y acciones de suspension/activacion en `easyeat/frontend/src/components/staff/StaffRosterTable.tsx` y conectar con `fetchStaff` en `easyeat/frontend/src/pages/StaffManagementPage.tsx`.

**Punto de control**: User Story 1 completamente funcional y verificable como MVP de seguridad.

---

## Phase 4: User Story 2 - Panel de salud financiera del local (Prioridad: P1)
> **GitHub Issue**: [#437](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/437)

**Objetivo**: Visualizar metricas consolidadas de ingresos totales acumulados y pedidos concretados (`PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`) para el dia en curso, semana calendario ISO 8601 o mes calendario, con respuesta reactiva en <2s, estado vacio `$0,00` / `0`, componentes interactivos de tablas y graficos visuales (Recharts) y exclusion estricta de ordenes no acreditadas.

**Criterio de prueba independiente**: Consultar el panel financiero con ordenes pagadas en distintas fechas y comprobar que los ingresos y cantidad de pedidos coincidan exactamente con la suma del dia, semana o mes en huso local tanto en tarjetas como en graficos; verificar que pedidos cancelados, expirados o pendientes queden excluidos.

### Pruebas para User Story 2 (TDD RED)

- [x] T021 [P] [US2] Escribir RED test unitario en `easyeat/backend/src/test/java/com/easyeat/analytics/service/FinancialHealthServiceTest.java` para calculo determinista de limites ISO 8601 (dia actual 00:00:00 a 23:59:59, semana lunes a domingo, mes calendario), resolucion de huso horario (`Establishment.timezone`) y estado vacio `$0.00` / `0`.
- [x] T022 [P] [US2] Escribir RED test de integracion con Testcontainers en `easyeat/backend/src/test/java/com/easyeat/analytics/controller/FinancialHealthControllerIntegrationTest.java` para `GET /api/v1/admin/analytics/financial-health` (200 OK para day/week/month, 400 Bad Request para parametro invalido, 403 sin admin, exclusion de `CANCELLED`/`EXPIRED`/`PENDING_PAYMENT`, y transicion de medianoche a las 23:59:59).
- [x] T023 [P] [US2] Escribir RED test frontend en `easyeat/frontend/tests/components/FinancialHealthPanel.test.tsx` para `FinancialHealthPanel` (tarjetas de ingresos formateados en moneda, pedidos concretados, selector dia/semana/mes reactivo en <2s sin acumuladores residuales, y estado vacio).
- [x] T024 [P] [US2] Escribir RED test frontend en `easyeat/frontend/tests/components/FinancialHealthChart.test.tsx` para `FinancialHealthChart` (grafico interactivo visual de ingresos y pedidos con Recharts, conmutacion con tabs dia/semana/mes, toggle entre vista tabular y grafico, y manejo de estado vacio).

### Implementacion para User Story 2 (GREEN)

- [x] T025 [US2] Crear record DTO `FinancialHealthResponse` en `easyeat/backend/src/main/java/com/easyeat/analytics/dto/FinancialHealthResponse.java`.
- [x] T026 [US2] Implementar `FinancialHealthService.java` en `easyeat/backend/src/main/java/com/easyeat/analytics/service/FinancialHealthService.java` calculando limites de granularidad en huso horario local y agregando `revenue` y `orderCount`.
- [x] T027 [US2] Crear `AdminAnalyticsController.java` en `easyeat/backend/src/main/java/com/easyeat/analytics/controller/AdminAnalyticsController.java` exponiendo `GET /api/v1/admin/analytics/financial-health` protegido con `@PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('ADMIN')")` y cabeceras `Cache-Control: no-cache`.
- [x] T028 [US2] Implementar componente `FinancialHealthPanel.tsx` en `easyeat/frontend/src/components/analytics/FinancialHealthPanel.tsx` con tarjetas de metricas, tabs dia/semana/mes y soporte de estado vacio `$0,00` / `0`.
- [x] T029 [US2] Implementar componente `FinancialHealthChart.tsx` en `easyeat/frontend/src/components/analytics/FinancialHealthChart.tsx` integrando Recharts para la visualizacion grafica e interactiva de ingresos y pedidos, con alternancia entre tabla de resumen y grafico.

**Punto de control**: User Story 2 completamente funcional y sincronizada de extremo a extremo.

---

## Phase 5: User Story 3 - Ranking de platos mas vendidos (Prioridad: P2)
> **GitHub Issue**: [#438](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/438)

**Objetivo**: Consultar un reporte de platos comercializados en pedidos concretados, ordenados descendentemente por unidades vendidas con desempate alfabetico ascendente A-Z, con historico global acumulado por defecto y opcion de filtrar por el periodo del panel, omitiendo platos sin ventas.

**Criterio de prueba independiente**: Registrar ventas de varios platos con distintas cantidades y verificar que el endpoint y la tabla listen los platos ordenados de mayor a menor cantidad de unidades, resolviendo empates alfabeticamente A-Z y mostrando estado neutral descriptivo si no hay ventas.

### Pruebas para User Story 3 (TDD RED)

- [x] T030 [P] [US3] Escribir RED test unitario en `easyeat/backend/src/test/java/com/easyeat/analytics/service/DishRankingServiceTest.java` para agregacion `SUM(quantity)` agrupado por plato, orden descendente por unidades, desempate secundario determinista case-insensitive `LOWER(dishName) ASC` y omision de platos con 0 ventas.
- [x] T031 [P] [US3] Escribir RED test de integracion con Testcontainers en `easyeat/backend/src/test/java/com/easyeat/analytics/controller/DishRankingControllerIntegrationTest.java` para `GET /api/v1/admin/analytics/dish-ranking` (200 OK con `granularity=all`, `day`, `week`, `month`, respuesta `[]` sin ventas, consolidacion inmediata tras orden pagada y ausencia de datos monetarios en payload).
- [ ] T032 [P] [US3] Escribir RED test frontend en `easyeat/frontend/tests/components/DishRankingTable.test.tsx` para `DishRankingTable` (columnas de plato y unidades vendidas, orden descendente, desempate A-Z, selector de alcance y estado vacio neutral).

### Implementacion para User Story 3 (GREEN)

- [ ] T033 [US3] Crear record DTO `DishRankingItemResponse` en `easyeat/backend/src/main/java/com/easyeat/analytics/dto/DishRankingItemResponse.java`.
- [ ] T034 [US3] Implementar `DishRankingService.java` en `easyeat/backend/src/main/java/com/easyeat/analytics/service/DishRankingService.java` ejecutando la consulta de unidades por plato con orden `unitsSold DESC, LOWER(dishName) ASC`.
- [ ] T035 [US3] Extender `AdminAnalyticsController.java` en `easyeat/backend/src/main/java/com/easyeat/analytics/controller/AdminAnalyticsController.java` exponiendo `GET /api/v1/admin/analytics/dish-ranking`.
- [ ] T036 [US3] Implementar componente `DishRankingTable.tsx` en `easyeat/frontend/src/components/analytics/DishRankingTable.tsx` con tabla de ranking, selector de periodo (historico global / dia / semana / mes) y estado neutral descriptivo.

**Punto de control**: User Story 3 completamente funcional y sincronizada de extremo a extremo.

---

## Phase 6: Polish & Cross-Cutting Concerns
> **GitHub Issue**: [#439](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/439)

**Proposito**: Integracion de vistas en pantalla principal, navegacion de rutas, verificacion de no-regresion y ejecucion del quickstart.

- [ ] T037 [P] Escribir RED test frontend en `easyeat/frontend/tests/pages/AdminAnalyticsPage.test.tsx` y crear la pagina unificada `AdminAnalyticsPage.tsx` en `easyeat/frontend/src/pages/AdminAnalyticsPage.tsx` integrando `FinancialHealthPanel`, `FinancialHealthChart` y `DishRankingTable`.
- [ ] T038 [P] Agregar la ruta protegida `/admin/analytics` en `easyeat/frontend/src/router/AppRoutes.tsx` con `RoleRouteGuard allowedRoles={['ADMINISTRADOR']}` y agregar la tarjeta de navegacion "Metricas y Rendimiento" en `easyeat/frontend/src/pages/AdminWorkspacePage.tsx`.
- [ ] T039 [P] Escribir RED test de regresion en `easyeat/backend/src/test/java/com/easyeat/order/CrossModuleBoundaryRegressionTest.java` verificando que KDS (spec 019), mostrador (spec 020), seguimiento (spec 021) y PIN de entrega (spec 022) permanecen 100% operativos e inmutables tras las adiciones de analitica.
- [ ] T040 Ejecutar la validacion completa de los 19 escenarios del quickstart en `specs/023-staff-access-bi-dish-ranking/quickstart.md` contra entorno relacional real (`mvn verify` en backend y `npm test` en frontend) y verificar cero errores.

---

## Dependencias y orden de ejecucion

### Dependencias entre fases

- **Setup (Fase 1)**: Sin dependencias previas; inicia de inmediato.
- **Foundational (Fase 2)**: Depende de Setup (Fase 1); bloquea el inicio de las historias de usuario.
- **User Story 1 (Fase 3)**: Depende de Foundational (Fase 2); puede ejecutarse de forma independiente de US2 y US3.
- **User Story 2 (Fase 4)**: Depende de Foundational (Fase 2); no depende de US1 ni US3.
- **User Story 3 (Fase 5)**: Depende de Foundational (Fase 2); comparte definicion de pedidos concretados con US2 pero es independiente en ejecucion.
- **Polish (Fase 6)**: Depende de que US1, US2 y US3 esten completas para la integracion de pantalla unificada y validacion E2E.

### Orden estricto dentro de cada historia (TDD)

1. Escribir pruebas automatizadas (RED) y verificar que fallen.
2. Implementar DTOs y modelos.
3. Implementar servicios de dominio y repositorios.
4. Implementar controladores y endpoints HTTP.
5. Implementar componentes frontend y clientes de API.
6. Verificar que las pruebas pasen (GREEN).

---

## Oportunidades de ejecucion en paralelo

- T001, T002 y T003 en Setup pueden ejecutarse en paralelo (backend SQL/tests, frontend types y frontend API).
- T004, T006 y T007 en Foundational pueden ejecutarse en paralelo (archivos de test independientes).
- Tras completar Foundational (Fase 2), **User Story 1, User Story 2 y User Story 3 pueden desarrollarse en paralelo** si la capacidad del equipo lo permite.
- Dentro de cada User Story, todos los tests marcados con `[P]` pueden escribirse en paralelo.

---

## Estrategia de implementacion

### MVP First (Solo User Story 1)

1. Completar Setup (Fase 1) y Foundational (Fase 2).
2. Completar User Story 1 (Fase 3: control de acceso y suspension de exempleados).
3. **Pausar y validar**: Ejecutar escenarios A, B, C, D, E y F de `quickstart.md`.
4. El sistema cuenta con resguardo de seguridad de acceso operativo como primer incremento entregable.

### Entrega incremental completa

1. Entregar MVP (User Story 1).
2. Implementar User Story 2 (Panel de salud financiera) y validar escenarios G, H, I, J, K, L, M de `quickstart.md`.
3. Implementar User Story 3 (Ranking de platos mas vendidos) y validar escenarios N, O, P, Q, R de `quickstart.md`.
4. Ejecutar Polish (Fase 6), validar integracion unificada y confirmar no-regresion (Escenario S).