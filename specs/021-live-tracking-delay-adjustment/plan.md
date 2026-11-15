# Plan de implementación: seguimiento en vivo y demora granular (+5)

**Rama**: `021-live-tracking-delay-adjustment` | **Fecha**: 15/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Entrada**: Especificación funcional desde `/specs/021-live-tracking-delay-adjustment/spec.md`

---

## Resumen

Implementar la arquitectura técnica incremental para el seguimiento adaptativo en vivo del pedido por parte del comensal y el control táctil de demoras granulares acumulativas (+5) para el personal de cocina, cubriendo las historias de usuario `COM-8.1.1` y `COM-7.3.2 (BIS)`:

1. **Seguimiento adaptativo en vivo por etapas operativas (`COM-8.1.1`)**:
   - Pantalla del comensal (`/seguimiento/{orderId}` y `/pedidos/{orderId}/seguimiento`) sincronizada de forma reactiva y silenciosa cada 6 segundos (rango 5–8 s) mediante el endpoint `GET /api/v1/orders/{orderId}/live-tracking` con cabeceras `no-cache`.
   - Estado `QUEUED`: renderiza `#ticketNumber`, insignia `"En cola en cocina"` y la leyenda explícita `"Tenés $N$ pedidos por delante"`, donde $N$ es el recuento determinista de órdenes previas pendientes en cola bajo regla FIFO (`CHF-3.1.2`, Spec 019). Cuando $N = 0$, exhibe literalmente `"Tenés 0 pedidos por delante"`. El temporizador dinámico y la barra de progreso permanecen estrictamente ocultos.
   - Estado `IN_PREPARATION`: al iniciar la cocción el chef ("EMPEZAR"), la leyenda de pedidos por delante se remueve, la insignia conmuta a `"Cocinando / En preparación"` con icono dinámico, y se activan el minutero dinámico y la barra porcentual.
   - Estado `READY_FOR_PICKUP`: al finalizar cocina ("LISTO"), el minutero, la barra y cualquier residuo de demora se purgan visualmente por completo; la pantalla conmuta al panel verde de éxito con el código numérico de seguridad de 4 dígitos y el resumen de platos.
   - Insignia de desconexión: ante fallas de red $> 15 \text{ s}$, muestra insignia sutil no bloqueante en cabecera `"Sin conexión — Reintentando..."`, conservando el último estado válido y retirándose automáticamente al reconectar.

2. **Ajuste granular acumulativo de demoras de cocina (+5) (`COM-7.3.2 (BIS)`)**:
   - Control táctil directo "+5" en el pie de la comanda en preparación activa (Columna 2 del KDS).
   - Invocación a `POST /api/v1/kitchen/tickets/{ticketId}/add-delay` ejecutada bajo bloqueo pesimista `PESSIMISTIC_WRITE` (`findByIdForUpdate` en `KdsTicketRepository`), sumando de forma inmediata, acumulativa y estrictamente lineal 5 minutos a `kds_tickets.specific_delay_minutes` (mediante migración Flyway `V27__kds_specific_delay_minutes.sql`).
   - Insignia contigua al botón en cocina reflejando la demora total acumulada (`+5 min`, `+10 min`, `+15 min`...). Consolidación determinista ante clics rápidos (3 pulsaciones = $+15 \text{ min}$ exactos).
   - El comensal absorbe reactivamente la demora en su siguiente ciclo silencioso ($M \to M + 5 \text{ min}$) y la barra porcentual recalcula su avance de acuerdo al nuevo horizonte temporal.
   - Reglas de frontera matemática: el minutero hace piso en 1 minuto (nunca desciende a 0 ni negativos) y la barra de progreso hace techo en 92% mientras la orden continúe en preparación.

---

## Contexto técnico

- **Lenguaje / Versión**: Java 21 (Backend), TypeScript 5.6 / React 18.3 (Frontend).
- **Dependencias principales**:
  - Backend: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Validation), Flyway, PostgreSQL Driver.
  - Frontend: React 18.3.1, React Router DOM 6.27.0, Axios 1.7.7, Tailwind CSS 3.4.13, Lucide React 0.453.0, Vitest 2.1.3.
- **Persistencia**:
  - PostgreSQL: tablas `orders`, `kds_tickets`, `order_items`.
  - Migración incremental Flyway: `V27__kds_specific_delay_minutes.sql` incorporando `specific_delay_minutes INTEGER NOT NULL DEFAULT 0` con check `specific_delay_minutes >= 0` en `kds_tickets`, y la columna `security_code VARCHAR(4) NULL` en `orders` de forma idempotente (`ADD COLUMN IF NOT EXISTS`).
- **Pruebas**:
  - Backend: JUnit 5, Mockito, Spring `@WebMvcTest`, Testcontainers PostgreSQL (`BaseIntegrationTest`).
  - Frontend: Vitest + React Testing Library (ejecutando en `easyeat/frontend/src/test/` y `easyeat/frontend/tests/`).
- **Plataforma objetivo**: Entorno Docker / Render (Backend Linux), terminales táctiles de cocina y pantallas de comensales en dispositivos móviles y de escritorio (Frontend).
- **Tipo de proyecto**: Aplicación Web Full-Stack (Backend REST API + Frontend SPA).
- **Objetivos de rendimiento**:
  - Consulta `GET /api/v1/orders/{orderId}/live-tracking`: tiempo de respuesta en base de datos $< 15 \text{ ms}$ apoyada en el índice de cola activa.
  - Mutación `POST /api/v1/kitchen/tickets/{ticketId}/add-delay`: tiempo p95 $< 100 \text{ ms}$ con bloqueo pesimista acotado a la transacción.
  - Frontend: polling a 60 fps, recálculo matemático en memoria sin saltos de diseño (*layout shifts*), cero reinicios de icono ante respuestas idénticas.
- **Restricciones arquitectónicas**:
  - Sin WebSockets ni colas de mensajería (estricto polling HTTP silencioso con cabeceras `no-cache`).
  - Bloqueo pesimista `PESSIMISTIC_WRITE` para atomicidad lineal y eliminación de condiciones de carrera en el "+5".
  - Purga de métricas en `READY_FOR_PICKUP` estrictamente visual (la persistencia en backend permanece intacta para auditoría).
  - Desacoplamiento total con mostrador (Spec 020) y verificación de PIN (Spec 022).
- **Alcance / Escala**: Seguimiento para cientos de comensales concurrentes y ajuste de demoras en horas pico para cocinas con alta demanda.

---

## Verificación constitucional

*(Evaluación frente a `.specify/memory/constitution.md` v2.0.0 tras Fase 0 y Fase 1)*

- **I. La especificación es la fuente de verdad**: Cumplido. La leyenda literal `"Tenés 0 pedidos por delante"`, la exclusión de métricas en `QUEUED`, el piso de 1 minuto, el techo de 92% y la acumulación lineal sin tope provienen taxativamente de `spec.md`.
- **II. Especificación antes de implementación**: Cumplido. Toda la arquitectura se formaliza previamente en `research.md`, `data-model.md`, `contracts/` y `quickstart.md` sin escribir código de producción.
- **III. Verificación en capas proporcional al riesgo**: Cumplido. Se definen escenarios de prueba end-to-end reproducibles en `quickstart.md`, pruebas de integración de concurrencia y atomicidad bajo Testcontainers, y pruebas unitarias de polling, desconexión e idempotencia visual en frontend.
- **IV. Diseño simple, cohesivo y justificado**: Cumplido. Se reutilizan las entidades y controladores existentes (`KdsTicket`, `OrderConfirmationTrackingController`, `KitchenKdsController`), incorporando una única migración incremental limpia (`V27`) sin sobreingeniería.
- **V. Alcance acotado y VI. Deuda técnica controlada**: Cumplido. Se respetan de manera estricta las fronteras con Spec 019 (no se toca el cronómetro Count-Up del KDS), Spec 020 (no se altera el cálculo de mostrador) y Spec 022 (no se implementa validación de PIN ni botón de entrega).
- **VII. Trazabilidad, consistencia y documentación**: Cumplido. Mapeo bidireccional íntegro `spec.md` ↔ `research.md` ↔ `data-model.md` ↔ `contracts/` ↔ `quickstart.md` ↔ `plan.md`.
- **VIII. Evidencia sobre supuestos y escalamiento humano**: Cumplido. Las decisiones de persistencia, concurrencia, cálculo de $N$ y diseño de DTOs fueron auditadas directamente en el código fuente del repositorio y acordadas interactivamente con el usuario mediante la herramienta `grill-me`.
- **IX. Ingeniería sobre conveniencia y X. Rebanadas verticales (Full-Stack)**: Cumplido. La entrega abarca la rebanada vertical completa: migración de base de datos Flyway, servicios de dominio, endpoints REST protegidos, clientes API en frontend, componentes UI y suite de validación.

---

## Estructura del proyecto y cambios planificados

### Documentación de esta funcionalidad

```text
specs/021-live-tracking-delay-adjustment/
├── spec.md                                # Especificación funcional aprobada
├── plan.md                                # Este archivo (plan técnico de implementación)
├── research.md                            # Decisiones técnicas y análisis de alternativas (Fase 0)
├── data-model.md                          # Entidades, migraciones, DTOs y máquina de estados (Fase 1)
├── quickstart.md                          # Guía reproducible de validación E2E (Fase 1)
├── checklists/                            # Checklists de requerimientos
└── contracts/
    └── live-tracking-and-delay-contract.md # Contratos REST de add-delay y live-tracking adaptativo
```

### Archivos de código afectados en el repositorio

#### Backend (`easyeat/backend/`)

- `src/main/resources/db/migration/V27__kds_specific_delay_minutes.sql`:
  - Migración Flyway para agregar `specific_delay_minutes INTEGER NOT NULL DEFAULT 0` con check `CHECK (specific_delay_minutes >= 0)` en `kds_tickets`, y la columna `security_code VARCHAR(4) NULL` en `orders` de forma idempotente (`ADD COLUMN IF NOT EXISTS`).
- `src/main/java/com/easyeat/order/domain/KdsTicket.java` y `Order.java`:
  - En `KdsTicket`: mapear atributo `specificDelayMinutes` (`Integer`) con valor por defecto 0 y sus respectivos getters y setters.
  - En `Order`: mapear atributo `securityCode` (`@Column(name = "security_code", length = 4)`) con getter y setter.
- `src/main/java/com/easyeat/order/repository/KdsTicketRepository.java`:
  - Agregar consulta `countQueuedTicketsAhead(UUID establishmentId, LocalDate pickupDate, LocalTime timeSlotStart, Instant queuedAt, UUID ticketId, LocalDate today)` para cálculo determinista de $N$ y método `findByIdForUpdate(@Param("id") UUID id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
- `src/main/java/com/easyeat/order/dto/KdsDelayIncrementResponse.java`:
  - Nuevo DTO de respuesta para `POST .../add-delay` con `ticketId`, `orderId`, `ticketNumber`, `specificDelayMinutes`, `estimatedTotalMinutes`, `updatedAt`.
- `src/main/java/com/easyeat/order/dto/OrderLiveTrackingResponse.java`:
  - Extender el record incorporando `positionAhead`, `estimatedTotalMinutes`, `elapsedMinutes`, `remainingMinutes`, `progressPercent`, `specificDelayMinutes`, `securityCode`, `startedAt`, `readyAt`, `items`.
- `src/main/java/com/easyeat/order/service/KdsQueueService.java` y `KdsQueueServiceImpl.java`:
  - Agregar método `KdsDelayIncrementResponse addTicketDelay(UUID ticketId, UUID establishmentId)` bajo bloqueo `PESSIMISTIC_WRITE`.
- `src/main/java/com/easyeat/order/service/OrderLiveTrackingService.java`:
  - Actualizar `getLiveTracking` implementando el cálculo adaptativo: en `QUEUED` computa $N$ (o 0); en `IN_PREPARATION` computa minutero con piso 1 y barra con techo 92% considerando `specific_delay_minutes`; en `READY_FOR_PICKUP` purga métricas y provee `securityCode`.
- `src/main/java/com/easyeat/order/controller/KitchenKdsController.java`:
  - Exponer endpoint `POST /api/v1/kitchen/tickets/{ticketId}/add-delay` restringido a roles `CHEF` y `ADMINISTRADOR`.

#### Frontend (`easyeat/frontend/`)

- `src/types/orderTracking.ts` y `src/types/kds.ts`:
  - Extender interfaces de tracking y KDS incorporando `specificDelayMinutes`, `positionAhead`, `remainingMinutes`, `progressPercent`, `securityCode`, `KdsDelayIncrementResponse` y `isDisconnected: boolean` en `UseOrderLiveTrackingPollingResult`.
- `src/api/orderTrackingApi.ts` y `src/api/kitchenKdsApi.ts`:
  - Agregar función `addTicketDelay(ticketId: string): Promise<KdsDelayIncrementResponse>`.
- `src/hooks/useOrderLiveTrackingPolling.ts`:
  - Ajustar cadencia a 6 segundos, cabeceras `no-cache`, pausa con `document.visibilityState === 'hidden'`, umbral de 15 s para corte de red con exposición de `isDisconnected: boolean` e idempotencia visual con `useRef`.
- `src/components/common/ConnectionStatusBadge.tsx`:
  - Componente de cabecera no bloqueante `"Sin conexión — Reintentando..."` ante desconexión $> 15 \text{ s}$.
- `src/components/tracking/LiveTrackingWidget.tsx`:
  - Adaptar renderizado según los 3 estados:
    - `QUEUED`: renderiza `#ticketNumber`, insignia `"En cola en cocina"` y `"Tenés $N$ pedidos por delante"` ($N=0 \to$ `"Tenés 0 pedidos por delante"`), ocultando minutero y barra.
    - `IN_PREPARATION`: renderiza insignia `"Cocinando / En preparación"` con icono dinámico, minutero (piso 1 min) y barra (techo 92%).
    - `READY_FOR_PICKUP`: renderiza panel verde de éxito con código de seguridad de 4 dígitos y resumen de platos, purgando minutero y barra.
- `src/pages/OrderConfirmationTrackingPage.tsx`:
  - Integrar la insignia de desconexión y el renderizado adaptativo.
- `src/router/AppRoutes.tsx`:
  - Declarar la ruta directa `/seguimiento/:orderId` vinculada a la experiencia de seguimiento adaptativo en vivo del comensal.
- `src/components/kitchen/KitchenTicketDelayControl.tsx` (montado en `KitchenWorkspacePage.tsx`):
  - Componente modular de control táctil "+5" con debounce en vuelo e insignia de demora acumulada contigua (`+5 min`, `+10 min`...), integrado en `KitchenWorkspacePage.tsx` y reutilizable en el tablero KDS.

---

## Complejidad y seguimiento

No se registran violaciones a los principios fundamentales ni se introducen patrones o capas no justificadas. El diseño es 100% incremental, estrictamente cohesivo y respeta las convenciones del repositorio.
