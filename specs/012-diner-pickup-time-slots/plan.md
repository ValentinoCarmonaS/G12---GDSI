# Implementation Plan: Diner Order Fulfillment Mode & Time-Slot Reservation Engine

**Branch**: `012-diner-pickup-time-slots` | **Fecha**: 01/11/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Especificación funcional aprobada desde `/specs/012-diner-pickup-time-slots/spec.md`

---

## Summary

Implementar la experiencia integral de selección de modalidad de retiro y el motor transaccional de reservas por franja horaria para comensales (`ROLE_COMENSAL`) en el flujo de checkout de EasyEat. El comensal puede optar de forma mutuamente excluyente entre retiro inmediato ("Lo antes posible", sujeto a apertura del local y ocupación inferior al 100% en el turno en curso) y reserva programada ("Para más tarde", con selección de fecha en los próximos 7 días operativos y turnos homogéneos de 15 minutos dentro del horario comercial).

La arquitectura garantiza estrictamente la invariante fundamental de capacidad Zero Overbooking mediante bloqueo pesimista en Spring Data JPA (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) bajo política First-Come, First-Served (FCFS), resolviendo colisiones concurrentes con rechazo atómico y respuesta de conflicto `HTTP 409 Conflict` (`SLOT_SOLD_OUT`). Se establece un ciclo de vida con TTL de 15 minutos en estado `PENDING_PAYMENT`, implementado con una estrategia híbrida: liberación reactiva inmediata a nivel de consulta combinada con un barredor programado `@Scheduled` cada 60 segundos y cancelación voluntaria instantánea. En el frontend, se incorpora una ruta dedicada `/checkout` que consume las franjas mediante polling periódico liviano (5-10 s), muestra alertas visuales de saturación (ámbar entre 80% y 99% con leyenda "Alta Demanda", gris atenuado al 100% con badge "Agotado") y despliega un contador regresivo de 15 minutos con advertencia en los últimos 2 minutos previo a la expiración.

---

## Technical Context

**Lenguaje/Versión**: Java 21 (Backend) | TypeScript 5.x / React 18 (Frontend)  
**Dependencias principales**:
- Backend: Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`), Flyway Core & PostgreSQL (`V12`), Lombok, Jackson Problem (`problem-spring-web`), Testcontainers (`1.21.4`).
- Frontend: React 18, React Router v6, Tailwind CSS, Lucide icons, Axios / native Fetch API.  
**Almacenamiento**: PostgreSQL 16 (tablas relacionales: `establishments`, `orders`, `order_items`, `users`, `dishes`).  
**Testing**:
- Backend: JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers (pruebas de estrés de concurrencia y transacciones sobre PostgreSQL).
- Frontend: Vitest, React Testing Library, jsdom.  
**Plataforma objetivo**: Linux / contenedores Docker para microservicios backend; navegadores web modernos (desktop y mobile) para la SPA React.  
**Tipo de proyecto**: Aplicación web full-stack (Spring Boot REST backend + React SPA frontend).  
**Objetivos de rendimiento**:
- Garantía de Cero Sobreventa (Zero Overbooking): 100% de cumplimiento en pruebas de estrés de concurrencia extrema (20+ solicitudes simultáneas por el último cupo).
- Latencia de consulta de disponibilidad de franjas: `< 100 ms` bajo condiciones normales.
- Liberación inmediata de cupo por cancelación voluntaria: `< 200 ms` de impacto en base de datos.
- Tiempo de cálculo en memoria de proyección de franjas diarias: `< 10 ms` en la JVM.  
**Restricciones de arquitectura y seguridad**:
- Autenticación obligatoria de comensal: `ROLE_COMENSAL` requerido para consultar franjas de retiro, crear órdenes y cancelarlas. Intentos no autenticados devuelven HTTP 401 con RFC 7807 `ProblemDetail`.
- Invariante de capacidad atómica (FCFS): serialización atómica en la creación de órdenes vía `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre `Establishment`.
- Máximo de una (1) reserva activa: un comensal solo puede mantener simultáneamente una orden en estado `PENDING_PAYMENT`.
- Consumo unitario de cupo: cada orden descuenta exactamente 1 cupo independientemente del número de platos contenidos.
- Lead time mínimo de preparación: franjas programadas del día de la fecha deben distar $\ge 30$ minutos respecto al reloj del servidor (`America/Argentina/Buenos_Aires`).
- Delimitación de alcance: pagos electrónicos (Sprint 4, `COM-7.x`), descuento de stock de recetas (Sprint 4, `CHF-5.1.1`), pantallas de administración de horarios (Sprint 3, `ADM-2.2.x`) y WebSockets (Sprint 5) quedan formalmente excluidos.  
**Escala y alcance**: Plataforma SaaS gastronómica multi-tenant con cientos de restaurantes activos, hasta 96 franjas horarias por día por local y 100+ comensales concurrentes.

---

## Constitution Check

*GATE: Evaluación frente a la Constitución SDD del proyecto (v2.0.0).*

| Principio | Estado | Evidencia y justificación |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El diseño responde con fidelidad a los 29 requerimientos funcionales (FR-001 a FR-029) y 7 invariantes de `spec.md`. No se introducen pagos ni descuentos de stock adelantados. |
| **II. Specification Before Implementation** | **PASS** | Especificación formal completada y aprobada; artefactos de diseño técnico (`research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, `plan.md`) elaborados y validados antes de generar tareas o código. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Estrategia de prueba en tres niveles: (1) Unit tests puros de proyección de franjas y lead time; (2) Integration & Concurrency Stress Tests con Testcontainers simulando 20 hilos disputando el último cupo; (3) Component & State tests en Vitest para frontend. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se extiende la tabla `establishments` en Flyway `V12` sin crear tablas auxiliares 1:1; se utiliza proyección efímera en memoria para franjas horarias; se aplica bloqueo pesimista estándar de JPA sobre la entidad raíz. |
| **V. Bounded Scope** | **PASS** | Limitado estrictamente a selección de modalidad, cálculo de franjas, reserva atómica con TTL y pantalla de espera. Pagos y stock ACID se difieren formalmente a Sprint 4. |
| **VI. Controlled Technical Debt** | **PASS** | Migración limpia y secuencial `V12__add_time_slot_and_order_fulfillment.sql` siguiendo `V11`. Sin modificaciones destructivas sobre esquemas previos. |
| **VII. Traceability & Documentation** | **PASS** | Trazabilidad bidireccional exhaustiva entre requerimientos funcionales, esquema relacional, contratos OpenAPI y escenarios ejecutables de `quickstart.md`. |
| **VIII. Evidence Over Assumption & Escalation** | **PASS** | Sesión de `grill-me` completada con el usuario, alineando la tecnología (Java 21 / Spring Boot 3.3.4), estrategia de bloqueo pesimista, persistencia de ítems y ruta `/checkout`. |
| **IX. Engineering Over Convenience** | **PASS** | Manejo riguroso de concurrencia y serialización en base de datos en lugar de validaciones optimistas frágiles en memoria. Manejo de errores RFC 7807 uniforme. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Entrega un vertical slice completo: migración DDL `V12`, entidades JPA y repositorios, controladores REST, servicios de dominio, ruta frontend `/checkout`, hooks y componentes UI interactivos. |

---

## Project Structure

### Documentation (this feature)

```text
specs/012-diner-pickup-time-slots/
├── spec.md              # Especificación funcional aprobada
├── checklists/
│   └── requirements.md  # Checklist de calidad de la especificación
├── plan.md              # Este plan de implementación técnica
├── research.md          # Fase 0: Decisiones arquitectónicas y de concurrencia
├── data-model.md        # Fase 1: Esquema relacional, entidades y ciclo de vida
├── contracts/
│   └── openapi.yaml     # Fase 1: Contrato OpenAPI 3.0 para endpoints de retiro y órdenes
└── quickstart.md        # Fase 1: Guía y escenarios de validación ejecutable
```

### Source Code Layout

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/easyeat/
│   │   │   │   ├── catalog/
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   └── Establishment.java                  # Extendido con lead_time, max_capacity, operating_days
│   │   │   │   │   └── repository/
│   │   │   │   │       └── EstablishmentRepository.java        # Consulta con @Lock(PESSIMISTIC_WRITE)
│   │   │   │   ├── order/
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── PickupSlotController.java           # GET /api/v1/establishments/{id}/pickup-slots
│   │   │   │   │   │   └── OrderController.java                # POST /orders, GET /orders/{id}, POST /orders/{id}/cancel
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── Order.java                          # Agregado Order con franja, status y TTL
│   │   │   │   │   │   ├── OrderItem.java                      # Línea de pedido con snapshot inmutable
│   │   │   │   │   │   ├── OrderStatus.java                    # PENDING_PAYMENT, PAID, CONFIRMED, CANCELLED, EXPIRED
│   │   │   │   │   │   └── FulfillmentMode.java                # IMMEDIATE, SCHEDULED
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── TimeSlotAvailability.java           # DTO proyección de franja de 15 min
│   │   │   │   │   │   ├── PickupSlotCatalogResponse.java      # DTO respuesta de grilla diaria
│   │   │   │   │   │   ├── CreateOrderRequest.java             # DTO payload de checkout
│   │   │   │   │   │   ├── OrderCreatedResponse.java           # DTO respuesta 201 Created
│   │   │   │   │   │   └── OrderDetailResponse.java            # DTO consulta de estado y countdown
│   │   │   │   │   ├── exception/
│   │   │   │   │   │   ├── SlotCapacityExceededException.java  # Mapea a HTTP 409 SLOT_SOLD_OUT
│   │   │   │   │   │   ├── ActiveReservationExistsException.java# Mapea a HTTP 409 ACTIVE_RESERVATION_EXISTS
│   │   │   │   │   │   └── LeadTimeViolationException.java     # Mapea a HTTP 422
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── OrderRepository.java                # Consultas de conteo activo, expiración y verificación diner
│   │   │   │   │   │   └── OrderItemRepository.java
│   │   │   │   │   └── service/
│   │   │   │   │       ├── PickupSlotService.java              # Proyección dinámica de turnos y saturación
│   │   │   │   │       ├── OrderService.java                   # Orquestación atómica FCFS de checkout y cancelación
│   │   │   │   │       └── OrderExpirationScheduler.java       # Tarea @Scheduled para barrido periódico
│   │   │   ├── resources/
│   │   │   │   └── db/migration/
│   │   │   │       └── V12__add_time_slot_and_order_fulfillment.sql # DDL de establishments, orders y order_items
│   │   └── test/
│   │       └── java/com/easyeat/order/
│   │           ├── PickupSlotServiceTest.java                  # Pruebas unitarias de proyección y lead time
│   │           ├── OrderServiceTest.java                       # Pruebas unitarias de lógica de reserva
│   │           ├── ZeroOverbookingConcurrencyIntegrationTest.java # Prueba de estrés concurrente (Testcontainers)
│   │           ├── OrderExpirationIntegrationTest.java         # Prueba de ciclo de vida TTL
│   │           └── OrderControllerIntegrationTest.java         # Pruebas de integración MockMvc
│   └── pom.xml
│
└── frontend/
    ├── src/
    │   ├── api/
    │   │   └── orderApi.ts                                     # Clientes HTTP para pickup-slots y orders
    │   ├── components/
    │   │   └── checkout/
    │   │       ├── FulfillmentModeSelector.tsx                 # Selector excluyente "Lo antes posible" / "Para más tarde"
    │   │       ├── OperatingDatePicker.tsx                     # Selector de fechas operativas (7 días)
    │   │       ├── TimeSlotGrid.tsx                            # Grilla de 15 min con badges Alta Demanda y Agotado
    │   │       ├── CheckoutSummary.tsx                         # Resumen del pedido con aviso no bloqueante de demora
    │   │       ├── PendingOrderCountdown.tsx                   # Cuenta regresiva 15:00 con banner < 2 min y botón cancelar
    │   │       └── ConflictNotificationModal.tsx               # Modal de recuperación ante colisión concurrente 409
    │   ├── hooks/
    │   │   ├── useCheckoutFulfillment.ts                       # Gestión de estado de modalidad, fecha y turno
    │   │   └── useTimeSlotPolling.ts                           # Polling periódico de saturación (5-10 s)
    │   ├── pages/
    │   │   └── CheckoutPage.tsx                                # Vista principal de checkout y espera de pago
    │   ├── router/
    │   │   └── AppRoutes.tsx                                   # Ruta protegida /checkout para ROLE_COMENSAL
    │   └── types/
    │       └── order.ts                                        # Tipos TypeScript para slots, órdenes y estados
    └── tests/
        └── checkout/
            ├── FulfillmentModeSelector.test.tsx
            ├── TimeSlotGrid.test.tsx
            ├── PendingOrderCountdown.test.tsx
            └── CheckoutPage.test.tsx
```

---

## Complexity Tracking

| Decisión arquitectónica / Patrón | Por qué se necesita | Alternativa más simple descartada y motivo |
|---|---|---|
| **Bloqueo pesimista `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre `Establishment`** | Garantiza Cero Sobreventa atómica y FCFS en base de datos durante ráfagas de reservas concurrentes sobre el último cupo sin persistir filas previas de franjas vacías. | *Bloqueo optimista con `@Version`*: Descartado porque no previene carreras en consultas agregadas (`COUNT`) sin una entidad intermedia compartida que se actualice. |
| **Estrategia híbrida de expiración TTL de 15 minutos** | La verificación inmediata en consulta (`expiresAt > NOW()`) libera el cupo en el milisegundo exacto de expiración, mientras que `@Scheduled` mantiene la base de datos limpia. | *Dependencia exclusiva del worker programado*: Descartado porque si el worker corre cada 60 s, los cupos podrían permanecer retenidos indebidamente hasta un minuto extra. |
| **Ruta dedicada `/checkout` (`CheckoutPage.tsx`)** | Proporciona ergonomía visual, persistencia en la navegación web, renderizado ordenado del selector de fecha/turnos y la pantalla de espera post-checkout. | *Incrustar todo en `CartDrawer`*: Descartado porque saturaría el panel lateral móvil y dificultaría la gestión del cronómetro regresivo y los modales de colisión. |
| **Snapshot inmutable en `OrderItem`** | Preserva la integridad contable del pedido (nombre y precio al momento de reservar) ante eventuales cambios de carta de la cocina en el catálogo. | *Referenciar solo `dish_id`*: Descartado porque cambios de precio en el menú alterarían retroactivamente los importes de órdenes pendientes. |
