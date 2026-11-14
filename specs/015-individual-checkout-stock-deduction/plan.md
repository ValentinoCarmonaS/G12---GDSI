# Implementation Plan: 015 Individual Checkout & Stock Deduction

**Branch**: `015-individual-checkout-stock-deduction` | **Fecha**: 09/11/2026 | **Spec**: [spec.md](./spec.md)  
**Input**: Especificación funcional aprobada desde `/specs/015-individual-checkout-stock-deduction/spec.md`  

---

## Summary

Implementar la confirmación definitiva del pago de carrito individual y el débito transaccional de stock de insumos por receta para EasyEat, unificando y resolviendo las historias centrales **`COM-7.2.1` (Pago de carrito individual)** y **`CHF-5.1.1` (Descuento de stock transaccional)**.

La solución articula armónicamente las capacidades operativas consolidadas en las iteraciones previas (Spec 009 Carrito, Spec 010 Operaciones, Spec 012 Franjas Horarias, Spec 013 Pausa y Stock Crítico, Spec 014 Mercado Pago Checkout), y absorbe formalmente los casos delegados por la Spec 014:
1. **Órdenes con bonificación al 100% (monto cero, AS-4, AS-6b, CB-04, FR-005)**: Omisión total de la pasarela de pagos externa, débito atómico de ingredientes por receta, paso inmediato a estado `IN_PROGRESS` y despacho directo a la cocina.
2. **Estado pendiente devuelto por pasarela (AS-5, CB-05, FR-006)**: Transición de la orden a `PENDING_ACCREDITATION`, congelamiento de la retención temporal de stock y franja horaria sin expiración prematura, **cero débito de stock real y cero envío a KDS** hasta recibir la confirmación terminal asíncrona vía webhook de Mercado Pago.
3. **Descuento atómico y estricto de stock de insumos por receta (AS-6, INV-01, FR-007, FR-008)**: Bloqueo pesimista ordenado (`PESSIMISTIC_WRITE` sobre `ingredients` por `id ASC` para erradicar deadlocks en insumos compartidos C3) con comprobación indivisible "todo o nada" sobre cantidades exactas enteras y continuas fraccionarias (`BigDecimal`, 4 decimales), y registro en `stock_audit_entries`.
4. **Despacho a la Cola del KDS (INV-04, INV-07)**: Creación de la comanda en `kds_tickets` con restricción de unicidad estricta `UNIQUE(order_id)` y ordenamiento de preparación por fecha y horario de entrega, alimentando en tiempo real las cuatro tarjetas de estado de `KitchenWorkspacePage.tsx`.
5. **Manejo Seguro de Concurrencia (INV-08, Q1)**:
   - Al presionar "Pagar", comprobación conjunta de stock y franja. Si los insumos se agotaron por concurrencia antes de consolidar la reserva temporal, se rechaza de inmediato con `HTTP 409 Conflict` (`INSUFFICIENT_STOCK`), sin cobro ni débito, preservando el carrito y marcando los platos no disponibles (AS-2, CB-02).
   - Durante la ventana de 15 minutos, los recursos están 100% garantizados para el comensal.
   - Si demora más de 15 minutos y la franja horaria se satura por otros usuarios, se interrumpe la transacción con `HTTP 409 Conflict` (`SLOT_CAPACITY_EXCEEDED`), sin cobro, preservando el carrito y reabriendo el selector de horarios (AS-3, CB-03).
6. **Aprobación sin Retorno del Comensal (AS-1b, CB-11, C5)**: Procesamiento autónomo en el servidor a partir del webhook firmado con HMAC-SHA256, garantizando que el pedido se cocine aunque el usuario cierre el navegador.
7. **Sincronización Inmediata del Catálogo (FR-013, INV-10)**: Si un débito agota las materias primas de una receta, `DishAvailabilityEngine` expone inmediatamente los platos dependientes como no disponibles en el menú.
8. **Aislamiento Total de Datos de Tarjetas (PCI-DSS SAQ A)**: Cero captura, persistencia o visualización de PAN/CVV en EasyEat (FR-013 de Spec 014, INV-11 de Spec 014, SC-009 de Spec 015).

---

## Technical Context

**Lenguaje(s) y versión(es)**:
- Backend: Java 21 (LTS).
- Frontend: TypeScript 5.6.3 / React 18.3.1.

**Framework(s) y dependencias principales**:
- Backend: Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`), Flyway Core (`9.x/10.x`), PostgreSQL Driver, Jackson (`jackson-databind`), JJWT (`0.12.6`), Caffeine Cache, Testcontainers (`1.21.4`).
- Frontend: React 18.3.1, Vite 5.4.8, Tailwind CSS 3.4.13, React Router v6.27.0, Axios 1.7.7, Lucide React 0.453.0.

**Persistencia y esquema de base de datos**:
- PostgreSQL 16 relacional.
- Migraciones gestionadas por Flyway en `easyeat/backend/src/main/resources/db/migration/`.
- Nueva migración: **`V23__individual_checkout_stock_deduction_and_kds.sql`**:
  - Amplía el constraint `chk_order_status` de la tabla `orders` para incorporar los estados `PENDING_ACCREDITATION` e `IN_PROGRESS`.
  - Crea la tabla `kds_tickets` con restricción de unicidad `UNIQUE (order_id)` y estados `QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP`, `COMPLETED`.
  - Incorpora índices especializados para consultas operativas de cocina y barrido de estados.

**Estrategia de HTTP / Contratos de API y validación**:
- Arquitectura REST sobre `/api/v1/checkout/` y `/api/v1/kitchen/`.
- Validación declarativa mediante Jakarta Bean Validation (`jakarta.validation.Valid`) en records inmutables de Java.
- Manejo estandarizado de excepciones según RFC 7807 (`ProblemDetail` / `application/problem+json`).
- Contratos formales documentados en `contracts/` y centralizados en especificación OpenAPI 3.0 (`contracts/openapi.yaml`).

**Estrategia de autenticación y sesión del comensal**:
- Autenticación basada en tokens JWT Bearer (`jjwt`), validando rol `ROLE_COMENSAL` para el flujo de pago y checkout, y `ROLE_CHEF` para la gestión de comandas en el KDS. El flujo de pago no altera el mecanismo de autenticación existente.

**Patrón de integración con pasarelas de pago (heredado de Spec 014)**:
- Arquitectura Hexagonal y Patrón Strategy: Puerto de dominio agnóstico `PaymentGatewayPort` (`createPreference`, `queryPayment`, `verifyWebhookSignature`, `parseWebhook`).
- Adaptador concreto `MercadoPagoGatewayAdapter` integrando Mercado Pago Checkout Pro (`POST https://api.mercadopago.com/checkout/preferences`) con cabecera `X-Idempotency-Key`.
- Verificación de Webhooks en `/api/v1/payments/{provider}/webhook`: validación de firma HMAC-SHA256 (`x-signature`) en tiempo constante y reconciliación REST directa con `GET /v1/payments/{id}`.

**Manejo de configuración, secretos y entornos**:
- Clase `@ConfigurationProperties(prefix = "mercadopago")` inyectada en componentes de integración.
- Secretos leídos exclusivamente desde variables de entorno: `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_PUBLIC_KEY`, `MERCADOPAGO_WEBHOOK_SECRET`, `MERCADOPAGO_SANDBOX`.
- Perfiles de configuración: `application-dev.yml` (sandbox habilitado), `application-test.yml` (test determinístico), `application-local.yml`.

**Estrategia de testing**:
- Backend: JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers PostgreSQL para integración y pruebas de estrés de concurrencia (`CountDownLatch` con 20 hilos compitiendo por cupo y stock de recetas).
- Frontend: Vitest 2.1.3, React Testing Library 16.0.1, jsdom 25.0.1.
- Mocks de pasarela: `MockRestServiceServer` para pruebas aisladas y determinísticas de fallos de red.

**Observabilidad y auditoría**:
- Logging estructurado con SLF4J / Logback registrando `orderId`, `reservationId`, `gatewayPaymentId` y `kdsTicketId`.
- Prohibición estricta de registrar datos bancarios o tokens privados en logs.
- Registro indeleble de auditoría de inventario en `stock_audit_entries` por cada ingrediente debitado.

**Mecanismo de Cola del KDS**:
- Entidad `KdsTicket` mapeada a la tabla `kds_tickets`, vinculada 1:1 con `orders` (`order_id UNIQUE`).
- Estados de comanda: `QUEUED` ("En Cola"), `IN_PREPARATION` ("En Preparación"), `READY_FOR_PICKUP` ("Para Retirar"), `COMPLETED` ("Completadas hoy").
- Conectada directamente a los cuatro contadores de `KitchenWorkspacePage.tsx`.

**Mecanismo de Inventario, Recetas e Insumos**:
- Entidad `DishRecipeIngredient` con clave compuesta `(dish_id, ingredient_id)` y `quantity` con precisión de 4 decimales.
- Entidad `Ingredient` con `current_stock` (`BigDecimal`, escala 4) y bloqueo pesimista `findByIdAndTenantIdForUpdate` ordenado por `id ASC`.
- Motor de disponibilidad `DishAvailabilityEngine` evaluando stock en tiempo real para alineación continua del catálogo (FR-013, INV-10).

**Restricciones de arquitectura y seguridad**:
- **Atomicidad Estricta (INV-01, FR-008)**: Todos los insumos de todas las recetas de la orden o ninguno. Cero débitos parciales.
- **Idempotencia (INV-07, FR-012, CB-12)**: Notificaciones duplicadas o recargas no debitan dos veces ni generan comandas repetidas en KDS.
- **TTL de 15 minutos (INV-08, Q1)**: Ventana garantizada de exclusividad para stock y cupo; expiración automática tras superar el lapso.
- **Aislamiento Financiero (PCI-DSS SAQ A)**: Cero almacenamiento o procesamiento de PAN/CVV en EasyEat.
- **Costo Cero en Sandbox (RT-1)**: Ejecución y verificación al 100% en entorno de prueba sin dinero real.
- **Equivalencia Funcional (RT-2)**: Reglas idénticas en Sandbox y producción.
- **Reproducibilidad Determinística (RT-3)**: Uso de matriz oficial de tarjetas de prueba documentadas por Mercado Pago Argentina (`APRO`, `FUND`, `OTHE`).

---

## Constitution Check

*GATE: Evaluación exhaustiva frente a la Constitución SDD del proyecto (v2.0.0).*

| Principio | Estado | Evidencia y Justificación Técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El plan responde con exactitud a los 13 requerimientos funcionales (FR-001 a FR-013), las 10 invariantes (INV-01 a INV-10), los 12 casos borde (CB-01 a CB-12), las 3 restricciones transversales (RT-1 a RT-3) y los 10 criterios de éxito (SC-001 a SC-010) de `spec.md`. No se reabren clarificaciones ya consensuadas (Q1, Q2, C1 a C5). |
| **II. Specification Before Implementation** | **PASS** | La especificación funcional está formalmente aprobada. Este plan técnico y sus artefactos de diseño (`research.md`, `data-model.md`, `contracts/`, `quickstart.md`) se completan íntegramente antes de descomponer tareas o escribir código de implementación. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Se define una estrategia de pruebas por capas: (1) Unit tests de cálculo exacto fraccionario de recetas y validación de estados de orden; (2) Tests de integración transaccional con Testcontainers verificando rollback completo ante falta de un solo insumo (INV-01); (3) Tests de estrés de concurrencia con 20 hilos compitiendo por insumos y cupo (INV-08); (4) Pruebas de idempotencia con webhooks duplicados (INV-07); (5) Guía de verificación determinística en Sandbox documentada en `quickstart.md`. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se aplica KISS y alta cohesión: se crea la entidad `KdsTicket` justificada en la necesidad de auditar la cola de cocina y alimentar el dashboard de KDS sin corromper la tabla de órdenes; se reutiliza el bloqueo pesimista de `IngredientRepository` y `EstablishmentRepository`; se extiende `OrderStatus` de forma normalizada. |
| **V. Bounded Scope** | **PASS** | Alcance estrictamente delimitado: no se introducen otros medios de pago, no se tocan reembolsos ni compras a proveedores, no se alteran recetas técnicas (Spec 005), y las aprobaciones tardías sobre órdenes ya vencidas se delegan a contingencia posterior según lo acordado. |
| **VI. Controlled Technical Debt** | **PASS** | Se introduce la migración secuencial aditiva `V23__individual_checkout_stock_deduction_and_kds.sql` sin romper el esquema existente ni realizar alteraciones destructivas sobre `orders`, `dishes` o `ingredients`. |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | Trazabilidad completa y bidireccional entre `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md`. Todos los estados, campos y contratos están documentados en español profesional, con identificadores técnicos en inglés. |
| **VIII. Evidence Over Assumption & Escalation** | **PASS** | Todas las decisiones técnicas se basan estrictamente en el código existente del repositorio (entidades, repositorios, controladores, vistas React) y en la documentación oficial de Mercado Pago Developers. No se inventaron endpoints ni tablas inexistentes. |
| **IX. Engineering Over Convenience** | **PASS** | Atomicidad real garantizada mediante transacciones ACID y bloqueos pesimistas ordenados anti-deadlock (`PESSIMISTIC_WRITE`); idempotencia reforzada a nivel de restricciones `UNIQUE` en base de datos; seguridad criptográfica verificada con HMAC-SHA256 en tiempo constante. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Vertical slice completo y funcional: migración Flyway `V23`, entidades JPA y repositorios, servicios de dominio transaccionales, controladores REST de checkout y KDS, actualización de `CheckoutPage.tsx` y `PaymentReturnPage.tsx`, y actualización de métricas en `KitchenWorkspacePage.tsx`. |

---

## Project Structure

### Documentation (this feature)

```text
specs/015-individual-checkout-stock-deduction/
├── spec.md                                 # Especificación funcional aprobada
├── checklists/
│   ├── requirements.md                     # Checklist de requerimientos
│   └── individual-checkout-stock-deduction-quality.md # Checklist de calidad funcional
├── plan.md                                 # Este plan de implementación técnica
├── research.md                             # Fase 0: Decisiones arquitectónicas y de concurrencia
├── data-model.md                           # Fase 1: Esquema relacional, entidades JPA y DDL V23
├── contracts/                              # Fase 1: Contratos modulares y OpenAPI 3.0
│   ├── openapi.yaml                        # Especificación OpenAPI 3.0 unificada
│   ├── 01-payment-confirmation.md          # Contrato de confirmación de pago aprobado (AS-1, AS-1b)
│   ├── 02-pending-status-resolution.md     # Contrato de estado pendiente y resolución (AS-5, CB-05)
│   ├── 03-zero-amount-bonus.md             # Contrato de orden bonificada 100% (AS-4, AS-6b)
│   ├── 04-concurrency-stock-exhaustion.md  # Contrato de rechazo por insumos agotados (AS-2)
│   ├── 05-slot-saturation-interruption.md  # Contrato de interrupción por franja saturada (AS-3)
│   ├── 06-atomic-stock-deduction.md        # Contrato de descuento atómico de stock (CHF-5.1.1)
│   ├── 07-kds-dispatch.md                  # Contrato de despacho a KDS y métricas de cocina
│   └── 08-catalog-stock-sync.md            # Contrato de sincronización de catálogo por agotamiento
└── quickstart.md                           # Fase 1: Guía de ejecución determinística en Sandbox
```

### Source Code Layout (Repository Root)

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/easyeat/
│   │   │   │   ├── catalog/
│   │   │   │   │   └── service/
│   │   │   │   │       └── DishAvailabilityEngine.java          # Evalúa stock real para disponibilidad en catálogo
│   │   │   │   ├── inventory/
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   └── StockAuditEntry.java                 # Auditoría de deducción por confirmación de orden
│   │   │   │   │   ├── exception/
│   │   │   │   │   │   └── InsufficientIngredientStockException.java # Excepción disparadora de rollback total
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── IngredientRepository.java            # Bloqueo pesimista ordenado (findByIdAndTenantIdForUpdate)
│   │   │   │   │   └── service/
│   │   │   │   │       ├── StockDeductionService.java           # Interfaz de servicio de débito atómico
│   │   │   │   │       └── StockDeductionServiceImpl.java       # Implementación transaccional "todo o nada" (INV-01)
│   │   │   │   └── order/
│   │   │   │       ├── controller/
│   │   │   │       │   ├── CheckoutPaymentController.java       # Extendido: confirm-payment, pending-payment, confirm-zero-amount
│   │   │   │       │   └── KitchenKdsController.java            # GET /kitchen/orders, GET /kitchen/dashboard/summary
│   │   │   │       ├── domain/
│   │   │   │       │   ├── Order.java                           # Extendido: soporte de nuevos estados
│   │   │   │       │   ├── OrderStatus.java                     # Extendido: PENDING_ACCREDITATION, IN_PROGRESS
│   │   │   │       │   ├── KdsTicket.java                       # Nueva entidad JPA: comanda en cola de cocina
│   │   │   │       │   └── KdsTicketStatus.java                 # Enum: QUEUED, IN_PREPARATION, READY_FOR_PICKUP, COMPLETED
│   │   │   │       ├── dto/
│   │   │   │       │   ├── PaymentConfirmationRequest.java      # DTO request confirmación de pago
│   │   │   │       │   ├── PaymentConfirmationResponse.java     # DTO response con comanda KDS
│   │   │   │       │   ├── PendingPaymentRequest.java           # DTO request estado pendiente
│   │   │   │       │   ├── PendingPaymentResponse.java          # DTO response estado pendiente
│   │   │   │       │   ├── ZeroAmountOrderRequest.java          # DTO request orden bonificada
│   │   │   │       │   ├── ZeroAmountOrderResponse.java         # DTO response orden bonificada
│   │   │   │       │   ├── KdsTicketSummaryDto.java             # DTO comanda KDS
│   │   │   │       │   └── KdsDashboardSummaryResponse.java     # DTO métricas de las 4 tarjetas del KDS
│   │   │   │       ├── repository/
│   │   │   │       │   └── KdsTicketRepository.java             # Consultas operativas de comandas por estado/fecha
│   │   │   │       └── service/
│   │   │   │           ├── CheckoutConfirmationService.java     # Orquestador: cobro, stock atómico, orden y KDS
│   │   │   │           ├── KdsQueueService.java                 # Servicio de encolado atómico e idempotente
│   │   │   │           └── WebhookProcessingService.java        # Procesador autónomo de webhooks de MP en servidor
│   │   │   └── resources/
│   │   │       └── db/migration/
│   │   │           └── V23__individual_checkout_stock_deduction_and_kds.sql # DDL Flyway ampliación orders y tabla kds_tickets
│   │   └── test/
│   │       └── java/com/easyeat/
│   │           ├── inventory/
│   │           │   ├── AtomicStockDeductionIntegrationTest.java # Verifica INV-01, FR-007, FR-008 (todo o nada)
│   │           │   └── SharedIngredientsDeadlockConcurrencyTest.java # Verifica C3 (bloqueo ordenado anti-deadlock)
│   │           └── order/
│   │               ├── IndividualCheckoutIntegrationTest.java   # Escenarios AS-1, AS-1b, AS-4, AS-5, AS-6, AS-6b
│   │               ├── ConcurrencyStockExhaustionIntegrationTest.java # Escenario AS-2 (insumos agotados al pagar)
│   │               ├── SlotSaturationInterruptionIntegrationTest.java # Escenario AS-3 (franja saturada tras 15 min)
│   │               ├── KdsIdempotencyIntegrationTest.java       # Verifica INV-07 y CB-12 (idempotencia KDS y stock)
│   │               └── CardDataIsolationSecurityTest.java       # Verifica cero persistencia o tránsito de PAN/CVV
│   └── pom.xml
└── frontend/
    ├── src/
    │   ├── api/
    │   │   ├── checkoutApi.ts                                   # Clientes Axios: confirm-payment, pending-payment, confirm-zero-amount
    │   │   └── kitchenKdsApi.ts                                 # Cliente Axios para órdenes KDS y contadores del dashboard
    │   ├── components/
    │   │   └── checkout/
    │   │       ├── PendingAccreditationBanner.tsx               # Banner explicativo de pago pendiente de acreditación (AS-5)
    │   │       └── ConcurrencyAlertModal.tsx                    # Modal de alerta para stock agotado (AS-2) o franja saturada (AS-3)
    │   ├── pages/
    │   │   ├── CheckoutPage.tsx                                 # Extendido: gestión botón Pagar / Confirmar ($0), alertas y redirección
    │   │   ├── PaymentReturnPage.tsx                            # Extendido: confirmación fehaciente, vaciado de carrito y estado pendiente
    │   │   └── KitchenWorkspacePage.tsx                         # Extendido: conexión real de las 4 tarjetas de comandas con el backend
    │   └── types/
    │       ├── checkout.ts                                      # Tipos TypeScript para respuestas de checkout y KDS
    │       └── kds.ts                                           # Tipos TypeScript para comandas y estados de cocina
    └── tests/
        ├── checkout/
        │   ├── IndividualCheckoutPayment.test.tsx               # Tests UI de checkout individual y bonificación 100%
        │   └── ConcurrencyAlerts.test.tsx                       # Tests UI de rechazo por stock y franja saturada
        └── kitchen/
            └── KitchenWorkspaceKds.test.tsx                     # Tests UI de contadores en tiempo real de cocina
```

**Decisión de Estructura**:  
Se preserva de forma estricta la arquitectura monolítica modular existente en el repositorio (`easyeat/backend` y `easyeat/frontend`). La lógica transaccional de stock reside en el paquete `com.easyeat.inventory`, la gestión de checkout y KDS en `com.easyeat.order`, y la visualización de platos en `com.easyeat.catalog`, garantizando alta cohesión, separación de responsabilidades y cero duplicación.

---

## Phase 0 — Research Summary

La investigación técnica y las decisiones de diseño se encuentran formalizadas en detalle en [research.md](./research.md).  
Principales definiciones consolidadas:
- **Deducción Atómica de Stock**: Bloqueo pesimista ordenado (`PESSIMISTIC_WRITE` por `ingredient_id ASC`) sobre la tabla `ingredients`, con validación estricta de existencias previa al débito y reversión total ante falta de un solo insumo (INV-01, FR-008). Precisión decimal de 4 dígitos (`BigDecimal`) para unidades enteras y fraccionarias (CB-07, SC-009).
- **Resolución de Concurrencia**: Validación conjunta de stock y franja horaria al presionar "Pagar". Ventana de exclusividad de 15 minutos (Q1, INV-08). Rechazo inmediato sin cobro si los insumos se agotaron por concurrencia antes del bloqueo (AS-2). Interrupción inmediata sin cobro y reapertura de horarios si el usuario tardó más de 15 min y la franja se saturó (AS-3).
- **Idempotencia de Inventario y KDS**: Doble barrera con restricción de unicidad relacional `UNIQUE(order_id)` en `kds_tickets` y guardas lógicas en servicio para ignorar notificaciones duplicadas de webhooks sin re-debitar inventario ni duplicar comandas (INV-07, CB-12).
- **Tratamiento de Estados de Pasarela**:
  - Estado pendiente (`pending`): Transición a `PENDING_ACCREDITATION`, congelamiento de retención temporal sin débito físico y sin comanda en KDS, con resolución asíncrona posterior vía webhook (CB-05, AS-5).
  - Bonificación 100%: Omisión total de la pasarela, débito atómico de stock, paso a `IN_PROGRESS` y envío directo a cocina (AS-4, AS-6b, CB-04).
  - Aprobación sin retorno: Procesamiento autónomo en servidor vía webhook (AS-1b, CB-11, C5).
- **Mecanismo de KDS**: Entidad `KdsTicket` con estados `QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP` y `COMPLETED`, alimentando directamente el dashboard del chef en `KitchenWorkspacePage.tsx`.
- **Aislamiento Financiero**: Cumplimiento de PCI-DSS SAQ A mediante Checkout Pro sin persistir PAN/CVV (FR-013 de Spec 014, SC-009).
- **Reproducibilidad Determinística**: Pruebas en Sandbox oficial de Mercado Pago Argentina con tarjetas documentadas (`APRO`, `FUND`, `OTHE`) y tests de estrés de 20 hilos con Testcontainers (RT-1, RT-2, RT-3).

---

## Phase 1 — Design Summary

Los artefactos de diseño técnico se encuentran detallados en:
- **[data-model.md](./data-model.md)**: Modelo relacional, entidades JPA (`Order` extendida, `KdsTicket`, `Ingredient`, `StockAuditEntry`, `Payment`, `TemporaryReservation`), diagramas Mermaid ER y State Machine, especificación de invariantes y script Flyway `V23`.
- **[contracts/](./contracts/)**: Catálogo de contratos modulares:
  - `01-payment-confirmation.md`: Confirmación de pago aprobado, débito de stock y comanda en KDS (AS-1, AS-1b).
  - `02-pending-status-resolution.md`: Estado pendiente de pasarela y resolución posterior (AS-5, CB-05).
  - `03-zero-amount-bonus.md`: Confirmación de orden bonificada al 100% sin pasarela (AS-4, AS-6b).
  - `04-concurrency-stock-exhaustion.md`: Rechazo por insumos agotados por concurrencia (AS-2, CB-02).
  - `05-slot-saturation-interruption.md`: Interrupción por franja horaria saturada tras superar 15 min (AS-3, CB-03).
  - `06-atomic-stock-deduction.md`: Contrato interno de descuento atómico de recetas (CHF-5.1.1).
  - `07-kds-dispatch.md`: Despacho a KDS y métricas para dashboard del chef.
  - `08-catalog-stock-sync.md`: Sincronización inmediata del catálogo por agotamiento de insumos (FR-013).
  - `openapi.yaml`: Especificación OpenAPI 3.0 unificada.
- **[quickstart.md](./quickstart.md)**: Guía de ejecución determinística en Sandbox para validar paso a paso todos los escenarios de aceptación (AS-1, AS-1b, AS-2, AS-3, AS-4, AS-5, AS-6, AS-6b) y prueba de aislamiento de datos bancarios.

---

## Re-evaluación de Constitution Check Post-Diseño

Tras completar los artefactos de diseño técnico (`research.md`, `data-model.md`, `contracts/`, `quickstart.md` y `plan.md`), se re-evalúan los gates de la Constitución SDD v2.0.0:

1. **Principio I (The Specification Is the Source of Truth)**: **PASS**. Todos los contratos, modelos y diagramas reflejan fielmente los requisitos y casos borde de `spec.md`.
2. **Principio II (Specification Before Implementation)**: **PASS**. La totalidad de los artefactos de diseño preceden a la descomposición de tareas y a la implementación de código productivo.
3. **Principio III (Layered Verification Proportional to Risk)**: **PASS**. Estrategia exhaustiva por capas: unitaria (fórmulas fraccionarias de recetas), integración (rollback total con Testcontainers), estrés de concurrencia (20 hilos compitiendo por insumos y turnos), idempotencia y validación determinística en Sandbox.
4. **Principio IV (Simple, Cohesive, and Justified Design)**: **PASS**. Diseño normalizado y cohesivo, reutilizando componentes existentes (`DishAvailabilityEngine`, `IngredientRepository`, `EstablishmentRepository`) sin patrones especulativos ni sobreingeniería.
5. **Principio V (Bounded Scope)**: **PASS**. Alcance rigurosamente acotado a `COM-7.2.1` y `CHF-5.1.1`. Casos delegados de contingencia financiera tardía expresamente excluidos.
6. **Principio VI (Controlled Technical Debt)**: **PASS**. Migración aditiva secuencial `V23` limpia y compatible.
7. **Principio VII (Traceability, Consistency, and Documentation)**: **PASS**. Trazabilidad completa y explícita entre requerimientos funcionales, contratos, entidades, casos borde y guías de validación.
8. **Principio VIII (Evidence Over Assumption & Escalation)**: **PASS**. Basado exclusivamente en evidencia comprobada del repositorio y especificaciones previas del producto.
9. **Principio IX (Engineering Over Convenience)**: **PASS**. Atomicidad estricta y bloqueos ordenados anti-deadlock implementados con máximo rigor técnico en el backend.
10. **Principio X (Full-Stack End-to-End Integrity)**: **PASS**. Vertical slice full-stack balanceado: persistencia, servicios de dominio, controladores REST, webhooks, interfaz de checkout de comensal y dashboard KDS de cocina.

**Resultado del Gate Post-Diseño**: **PASS (10/10 Principios Cumplidos sin Excepciones ni Violaciones)**.

---

## Complexity Tracking

> **Estado**: No se detectaron violaciones a la Constitución que requieran justificación o complejidad accidental. El diseño se ajusta estrictamente a los estándares existentes del repositorio.
