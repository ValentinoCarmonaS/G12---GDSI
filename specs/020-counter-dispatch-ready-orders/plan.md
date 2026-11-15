# Plan de implementación: despacho a mostrador y sincronización de pedidos listos

**Rama**: `020-counter-dispatch-ready-orders` | **Fecha**: 15/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Entrada**: Especificación funcional desde `/specs/020-counter-dispatch-ready-orders/spec.md`

---

## Resumen

Implementar la arquitectura técnica incremental para el traspaso operativo desde la cocina hacia el mostrador de entrega y la sincronización periódica reactiva del tablero de retiro, cubriendo las historias de usuario `CHF-4.1.3`, `CHF-4.2.3`, `ENC-2.1.1` y `ENC-2.1.2`:

1. **Finalización atómica e idempotente en cocina (`CHF-4.1.3` — `RF-001` a `RF-004`)**:
   - Botón directo "LISTO" en la Columna 2 del KDS de cocina que invoca `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`.
   - Transición transaccional bajo bloqueo pesimista `PESSIMISTIC_WRITE`: mutación atómica de `KdsTicket.status = READY_FOR_PICKUP` y `Order.status = READY_FOR_PICKUP`.
   - Coincidencia temporal inmutable: persistencia simultánea de `ready_at = Instant.now()` tanto en `kds_tickets` como en `orders` (mediante migración Flyway `V26__counter_dispatch_and_security_code.sql`).
   - Idempotencia estricta ante reintentos de red o pulsaciones dobles: si ya está en `READY_FOR_PICKUP`, responde `200 OK` retornando el `ready_at` original sin alterar marcas temporales.
   - Remoción inmediata de la comanda de la Columna 2 en el KDS de cocina al confirmarse la operación.
2. **Sincronización periódica y resiliencia en mostrador (`CHF-4.2.3` — `RF-014`, `RF-018`)**:
   - Polling silencioso en frontend cada 4 segundos con cabeceras `Cache-Control: no-cache`.
   - Cancelación al desmontar con `AbortController` y pausa reactiva al ocultar pestaña (`document.visibilityState === 'hidden'`).
   - Insignia sutil no bloqueante en cabecera ("Sin conexión — Reintentando...") si transcurren más de 15 segundos sin respuesta exitosa, con retiro automático al primer éxito posterior sin colapsar tarjetas abiertas.
3. **Tablero de pedidos listos plegable/expandible (`ENC-2.1.1` — `RF-005` a `RF-011`, `RF-015` a `RF-017`, `RF-019`)**:
   - Pantalla `/mostrador` alimentada por `GET /api/v1/counter/orders`, filtrando por la jornada actual (`pickupDate = hoy` en zona horaria `America/Argentina/Buenos_Aires`) con ordenamiento total `ready_at ASC, ticket_number ASC` optimizado con el nuevo índice `idx_kds_tickets_counter_ready`.
   - Tarjetas compactas cerradas por defecto exhibiendo: `#ticketNumber`, turno `HH:mm - HH:mm` y contador Count-Up `MM min` derivado puramente de `ready_at`.
   - Expansión individual interactiva con soporte simultáneo de múltiples tarjetas abiertas gestionadas en el estado local `expandedTicketIds: Set<string>`.
   - Alternancia bidireccional (toggle) asociada a cabecera/zona neutral con `e.stopPropagation()` en contenedores interiores interactivos para evitar colapsos accidentales.
   - Estado vacío informativo neutral ("No hay pedidos listos para entrega") preservando estabilidad visual.
4. **Verificación rápida de comanda física y cotejo oral (`ENC-2.1.2` — `RF-012`, `RF-013`)**:
   - Tarjeta desplegada exhibe código de seguridad de 4 dígitos (persistido en `orders.security_code`), nombre del comensal (`fullName`), fotografía provista por `GET /api/v1/profile/users/{userId}/avatar` (u omisión limpia si no posee), desglose de platos en formato unificado `[Cantidad]x [Plato]` y contenedor visual del botón de entrega.
   - Agnosticismo monetario absoluto: contrato y DTO desprovistos de importes, precios, subtotales o totales.

---

## Contexto técnico

- **Lenguaje / Versión**: Java 21 (Backend), TypeScript 5.x / React 18 (Frontend).
- **Dependencias principales**:
  - Backend: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Validation), PostgreSQL Driver, Flyway.
  - Frontend: React 18, React Router DOM 6, Axios, Tailwind CSS, Lucide React, Vitest.
- **Persistencia**:
  - PostgreSQL: tablas `orders`, `kds_tickets`, `order_items`, `users`, `user_photos`.
  - Migración incremental Flyway: `V26__counter_dispatch_and_security_code.sql` añadiendo `orders.ready_at`, `orders.security_code` e índice compuesto `idx_kds_tickets_counter_ready`.
- **Pruebas**:
  - Backend: JUnit 5, Mockito, Spring `@WebMvcTest`, Testcontainers PostgreSQL (`BaseIntegrationTest`).
  - Frontend: Vitest + React Testing Library (`easyeat/frontend/tests/`).
- **Plataforma objetivo**: Entorno Docker / Render (Backend Linux), terminales táctiles de cocina y monitores de despacho de mostrador (Frontend).
- **Tipo de proyecto**: Aplicación Web Full-Stack (Backend REST API + Frontend SPA).
- **Objetivos de rendimiento**:
  - Consulta de mostrador (`GET /api/v1/counter/orders`): tiempo de respuesta en base de datos < 20 ms bajo índice compuesto.
  - Finalización de comanda (`POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`): tiempo p95 < 150 ms con bloqueo pesimista acotado.
  - Frontend: renderizado a 60 fps, recálculo de cronómetro en memoria sin llamadas de red adicionales, cero parpadeos visuales durante el polling.
- **Restricciones arquitectónicas**:
  - Sin WebSockets ni colas de eventos (estricto polling HTTP resiliente).
  - Bloqueo pesimista `PESSIMISTIC_WRITE` para atomicidad e idempotencia.
  - Ausencia de datos financieros en toda la superficie de mostrador.
- **Alcance / Escala**: Mostrador de despacho para restaurantes con cientos de pedidos diarios y decenas de pedidos listos concurrentes en horas pico.

---

## Verificación constitucional

*(Evaluación frente a `.specify/memory/constitution.md` v2.0.0 tras Fase 0 y Fase 1)*

- **I. La especificación es la fuente de verdad**: Cumplido. Todos los requisitos funcionales (`RF-001` a `RF-019`), invariantes y casos borde provienen taxativamente de `spec.md`.
- **II. Especificación antes de implementación**: Cumplido. La planificación técnica genera previamente `research.md`, `data-model.md`, `contracts/` y `quickstart.md` sin producir código de producción no planificado.
- **III. Verificación en capas proporcional al riesgo**: Cumplido. Se definen escenarios de prueba end-to-end reproducibles en `quickstart.md`, pruebas de integración de base de datos con concurrencia (Testcontainers) y pruebas unitarias de polling, toggle y temporizadores en frontend.
- **IV. Diseño simple, cohesivo y justificado**: Cumplido. Se extienden los controladores y servicios existentes (`OrderLiveTrackingService`, `KitchenTicketDispatchController`) y se introduce `CounterOrderController` de manera puramente cohesiva sin sobreingeniería.
- **V. Alcance acotado y VI. Deuda técnica controlada**: Cumplido. Se respetan estrictamente los límites con la spec 021 (demoras `+5`) y la spec 022 (verificación interactiva de PIN y botón de entrega como contenedor pasivo).
- **VII. Trazabilidad, consistencia y documentación**: Cumplido. Mapeo bidireccional íntegro `spec.md` ↔ `research.md` ↔ `data-model.md` ↔ `contracts/` ↔ `quickstart.md` ↔ `plan.md`.
- **VIII. Evidencia sobre supuestos y escalamiento humano**: Cumplido. Las discrepancias de persistencia (`security_code`, `ready_at`, endpoint de fotos) fueron auditadas en el código fuente y resueltas interactivamente con el usuario mediante la herramienta de preguntas directas (`grill-me`).
- **IX. Ingeniería sobre conveniencia y X. Rebanadas verticales (Full-Stack)**: Cumplido. La entrega abarca la rebanada vertical completa: migración Flyway, lógica de dominio, endpoints REST seguros, DTOs sin datos financieros, componentes visuales de UI y suite de validación.

---

## Estructura del proyecto y cambios planificados

### Documentación de esta funcionalidad

```text
specs/020-counter-dispatch-ready-orders/
├── spec.md                          # Especificación funcional aprobada
├── plan.md                          # Este archivo (plan técnico de implementación)
├── research.md                      # Decisiones técnicas y análisis de alternativas (Fase 0)
├── data-model.md                    # Entidades, migraciones, DTOs y máquina de estados (Fase 1)
├── quickstart.md                    # Guía reproducible de validación E2E (Fase 1)
├── checklists/                      # Checklists de calidad de requerimientos
└── contracts/
    └── counter-dispatch-contract.md # Contratos REST de ready-for-pickup, counter orders y avatar
```

### Archivos de código afectados

#### Backend (`easyeat/backend/src/main/java/com/easyeat/` y `resources/`)

- `src/main/resources/db/migration/V26__counter_dispatch_and_security_code.sql`:
  - Migración Flyway para incorporar `orders.ready_at`, `orders.security_code` e índice compuesto `idx_kds_tickets_counter_ready`.
- `order/domain/Order.java`:
  - Incorporar campos `readyAt` (`Instant`) y `securityCode` (`String`) con sus accesores y mutadores.
- `order/domain/KdsTicket.java`:
  - Mapear el nuevo índice `idx_kds_tickets_counter_ready` en la anotación `@Table(indexes = ...)`.
- `order/repository/KdsTicketRepository.java`:
  - Agregar `findByIdForUpdate(@Param("id") UUID id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` si no estuviera preexistente.
  - Agregar `findCounterReadyTickets(@Param("establishmentId") UUID establishmentId, @Param("today") LocalDate today)`.
- `order/service/OrderLiveTrackingService.java`:
  - Refactorizar `markTicketReadyForPickup(UUID ticketOrOrderId, UUID userId)` para aplicar bloqueo pesimista, verificar idempotencia retornando el `readyAt` original, y actualizar atómicamente `KdsTicket` y `Order`.
- `order/service/OrderService.java`:
  - Generar código numérico aleatorio de 4 dígitos (`1000..9999`) al persistir órdenes confirmadas.
- `order/controller/KitchenTicketDispatchController.java`:
  - Mantener y asegurar `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup` protegido para roles `CHEF`, `ENCARGADO`, `ADMINISTRADOR`.
- `order/controller/CounterOrderController.java` (nuevo):
  - Exponer `GET /api/v1/counter/orders` protegido para `ENCARGADO` y `ADMINISTRADOR`, retornando lista ordenada de `CounterReadyOrderResponse`.
- `profile/controller/ProfileAvatarController.java`:
  - Refactorizar mapeo a nivel de clase a `@RequestMapping("/api/v1/profile")`, preservando los endpoints existentes bajo `/avatar` para el comensal propio e incorporando endpoint `GET /users/{userId}/avatar` protegido para roles de personal (`ENCARGADO`, `CHEF`, `ADMINISTRADOR`).
- `order/dto/CounterReadyOrderResponse.java` (nuevo) y `order/dto/KdsTicketResponse.java` (reutilizado):
  - DTOs tipados estrictamente desprovistos de campos monetarios.

#### Frontend (`easyeat/frontend/src/`)

- `types/counter.ts` (nuevo):
  - Interfaces TypeScript: `CounterReadyOrder`, `CounterTimeSlot`, `CounterCustomerSummary`, `CounterOrderItem`.
- `api/counterApi.ts` (nuevo):
  - Función `fetchCounterOrders(): Promise<CounterReadyOrder[]>`.
- `api/orderTrackingApi.ts`:
  - Consolidar `markTicketReadyForPickup(ticketId: string): Promise<KdsTicketResponse>`.
- `hooks/useCounterOrdersPolling.ts` (nuevo):
  - Hook de polling silencioso cada 4 segundos con `no-cache`, `AbortController`, pausa ante `document.visibilityState === 'hidden'`, umbral de 15 segundos para activación de insignia de desconexión y retiro automático al reconectar.
- `utils/counterTimeUtils.ts` (nuevo):
  - Función pura `calculateWaitMinutes(readyAtIso: string, serverOffsetMs?: number): number`.
  - Formateo determinista `${mins} min`.
- `components/counter/CounterTicketCard.tsx` (nuevo):
  - Componente de tarjeta de mostrador con estado plegado / desplegado (`toggle`), protección `e.stopPropagation()` en interior, renderizado limpio de avatar (`photoUrl`), código de seguridad de 4 dígitos, desglose `[Cantidad]x [Plato]` y contenedor visual para botón de entrega.
- `pages/CounterWorkspacePage.tsx`:
  - Implementar el tablero de pedidos listos para retiro en `/mostrador` con soporte de insignia de desconexión y estado vacío neutral.
- `pages/KitchenWorkspacePage.tsx` / `components/kds/KitchenKanbanBoard.tsx`:
  - Vincular el botón "LISTO" en la tarjeta de Columna 2 de cocina con remoción inmediata optimista tras confirmar la operación.

#### Tests (`easyeat/backend/src/test/` y `easyeat/frontend/tests/`)

- **Backend tests**:
  - `KitchenReadyForPickupIntegrationTest.java`: Pruebas de endpoint `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup` (transición atómica, coincidencia temporal inmutable `kds_tickets.ready_at == orders.ready_at`, idempotencia ante llamadas repetidas, errores 404/403/409).
  - `CounterOrderControllerIntegrationTest.java`: Pruebas de `GET /api/v1/counter/orders` (ordenamiento `ready_at ASC, ticket_number ASC`, exclusión de fechas futuras, omisión de datos monetarios, filtrado por sucursal).
  - `ProfileAvatarControllerStaffTest.java`: Pruebas del endpoint de avatares para comensales con y sin foto.
- **Frontend tests**:
  - `CounterWorkspacePage.test.tsx` / `CounterTicketCard.test.tsx`: Pruebas de renderizado cerrado inicial, toggle en cabecera, preservación ante clics interiores, omisión limpia de foto, despliegue simultáneo y mensaje de estado vacío.
  - `useCounterOrdersPolling.test.ts`: Pruebas de ciclo de polling, cancelación al desmontar, conmutación de insignia de desconexión a los 15 s y auto-recuperación al reconectar.

---

## Seguimiento de complejidad

| Decisión / Mecanismo | Por qué es necesaria | Alternativa más simple descartada |
| :--- | :--- | :--- |
| Migración Flyway `V26` para añadir `ready_at` y `security_code` a `orders` | Garantiza coincidencia temporal inmutable con `kds_tickets.ready_at` y provee persistencia real del PIN de 4 dígitos para cotejo visual oral en mostrador (`RF-012`). | Generar códigos efímeros en memoria en el DTO: descartado porque produciría desincronización con la pantalla del comensal y rompería la consistencia exigida por SDD. |
| Bloqueo pesimista `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `ready-for-pickup` | Asegura atomicidad e idempotencia estricta ante pulsaciones repetidas o reintentos de red, impidiendo condiciones de carrera concurrentes. | Bloqueo optimista (`@Version`): descartado porque requeriría agregar columnas de versión en tablas ya estabilizadas en producción. |
| Endpoint dedicado `GET /api/v1/profile/users/{userId}/avatar` para staff | Permite a la terminal de mostrador visualizar la fotografía del comensal titular respetando la privacidad del usuario y las restricciones de roles. | Hacer público el avatar sin autenticación o embeber imágenes en Base64 en el polling: descartado por seguridad y consumo excesivo de ancho de banda. |
| Polling silencioso a 4 s con detección de desconexión a los 15 s | Provee sincronización en tiempo cuasi-real sin infraestructura compleja de WebSockets, advirtiendo fallos prolongados de red de forma no intrusiva. | WebSockets o Server-Sent Events (SSE): descartados explícitamente por los requisitos de la especificación técnica. |
