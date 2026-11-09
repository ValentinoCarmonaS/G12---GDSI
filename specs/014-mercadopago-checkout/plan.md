# Implementation Plan: 014 Mercado Pago Checkout

**Branch**: `014-mercadopago-checkout` | **Fecha**: 09/11/2026 | **Spec**: [spec.md](./spec.md)  
**Input**: Especificación funcional aprobada desde `/specs/014-mercadopago-checkout/spec.md`  

---

## Summary

Implementar el flujo de pago con Mercado Pago para comensales (`ROLE_COMENSAL`) en el checkout de EasyEat, unificando y absorbiendo formalmente las historias `COM-7.1.1` (método guardado), `COM-7.1.2` (nueva tarjeta) y `COM-7.1.3` (recordar medio de pago) bajo la integración delegada con **Mercado Pago Checkout Pro**. 

La arquitectura garantiza que EasyEat **nunca capture, procese, almacene ni exhiba datos de tarjetas de crédito o débito** (FR-013, INV-11, SC-009), encuadrando la plataforma en el estándar de seguridad PCI-DSS SAQ A. Al presionar "Pagar", el sistema establece una **reserva temporal conjunta e indivisible** (INV-01) que bloquea simultáneamente el cupo de la franja horaria de retiro y el stock de cocina de los platos del carrito durante una **ventana estricta de 15 minutos** (INV-04), alineada con el TTL de la Spec 012.

La confirmación de la orden y la conversión de la reserva en definitiva dependen de forma exclusiva de la aprobación formal emitida por Mercado Pago (INV-02, INV-08), la cual es procesada de manera idempotente (INV-06, CB-07) tanto por notificaciones asíncronas de webhook firmadas criptográficamente con HMAC-SHA256 (`x-signature`) como por el retorno del comensal. Ante rechazos, abandonos o fallos de red, los recursos se liberan de inmediato (INV-03), preservando intactos el carrito y la franja horaria en la pantalla de checkout para permitir reintentos con un solo clic (Q2, FR-006, FR-008). 

Se articula armónicamente sobre las capacidades preexistentes de Carrito (Spec 009), Configuración Operativa (Spec 010), Selección de Franjas (Spec 012) y Disponibilidad y Pausa por Cocina (Spec 013). Los escenarios de carritos con monto cero, pagos en estado pendiente de resolución incierta y aprobaciones tardías sobre reservas ya expiradas quedan formalmente delimitados y delegados a la historia complementaria COM-7.2.1.

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
- Nueva migración: `V18__mercadopago_checkout_and_reservations.sql` incorporando las tablas `temporary_reservations` y `payments`, junto con índices de unicidad para soporte de idempotencia (`uq_reservation_idempotency_key`, `idx_unique_active_reservation_per_order`, `idx_payments_gateway_id`).

**Estrategia de HTTP / Contratos de API y validación**:
- Arquitectura REST sobre `/api/v1/checkout/` y `/api/v1/payments/`.
- Validación declarativa de Bean Validation (`jakarta.validation.Valid`) en DTOs inmutables (Java records / classes).
- Manejo estandarizado de excepciones mediante RFC 7807 (`ProblemDetail` / `application/problem+json`).
- Contratos formales documentados en `contracts/` y centralizados en OpenAPI 3.0 (`contracts/openapi.yaml`).

**Estrategia de autenticación y sesión del comensal**:
- Autenticación basada en tokens JWT Bearer (`jjwt`), validando rol `ROLE_COMENSAL` mediante Spring Security (`@PreAuthorize("hasRole('COMENSAL')")`). El flujo de pago no altera el mecanismo de sesión existente.

**Patrón de integración con pasarelas de pago (POO / SOLID)**:
- Arquitectura Hexagonal y Patrón Strategy: Puerto de dominio agnóstico `PaymentGatewayPort` (`createPreference`, `queryPayment`, `verifyWebhookSignature`, `parseWebhook`).
- Resolutor dinámico `PaymentGatewayResolver` que mapea cada pasarela soportada según el enum `PaymentMethod` (`MERCADO_PAGO`, extensible a `STRIPE`).
- Implementación concreta exclusiva para esta feature: **`MercadoPagoGatewayAdapter`** integrando Mercado Pago Checkout Pro (`POST https://api.mercadopago.com/checkout/preferences`).
- Redirección externa hacia pasarela segura vía `init_point` (o `sandbox_init_point`).
- Idempotencia saliente: cabecera obligatoria `X-Idempotency-Key` conteniendo UUID v4.
- Recepción de Webhooks en `/api/v1/payments/{provider}/webhook`: validación de firma HMAC-SHA256 sobre cabecera `x-signature` y reconciliación REST directa con `GET /v1/payments/{id}`.

**Manejo de configuración, secretos y entornos**:
- Clase `@ConfigurationProperties(prefix = "mercadopago")` inyectada en componentes de integración.
- Propiedades sensibles leídas desde variables de entorno: `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_PUBLIC_KEY`, `MERCADOPAGO_WEBHOOK_SECRET`, `MERCADOPAGO_SANDBOX`.
- Perfiles de configuración: `application-dev.yml`, `application-test.yml`, `application-local.yml`.

**Estrategia de testing**:
- Backend: JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers PostgreSQL para integración y pruebas de estrés de concurrencia (`CountDownLatch` con 20 hilos compitiendo por cupo y stock).
- Frontend: Vitest 2.1.3, React Testing Library 16.0.1, jsdom 25.0.1.
- Mocks de pasarela: `MockRestServiceServer` y emulación de respuestas de Mercado Pago para pruebas aisladas y determinísticas de fallos de red (AS-6).

**Observabilidad y auditoría**:
- Logging estructurado con SLF4J / Logback registrando `orderId`, `reservationId`, `gatewayPreferenceId`, `gatewayPaymentId` y códigos de estado.
- Prohibición estricta de registrar datos bancarios o tokens privados en logs.

**Restricciones de arquitectura y seguridad**:
- **Idempotencia (INV-06)**: Notificaciones o retornos repetidos procesan la confirmación exactamente una sola vez.
- **TTL de 15 minutos (INV-04)**: Expiración garantizada vía doble mecanismo: lazy evaluation en consultas y barredor programado `@Scheduled` cada 60 s.
- **Aislamiento de datos de tarjeta (FR-013, INV-11, SC-009)**: Cero captura, persistencia o visualización de PAN/CVV en EasyEat.
- **Costo Cero en Sandbox (RT-1)**: Ejecución y verificación al 100% en entorno de prueba sin dinero real.
- **Equivalencia Funcional (RT-2)**: Reglas y flujos idénticos en Sandbox y producción.
- **Reproducibilidad Determinística (RT-3)**: Uso de tarjetas de prueba documentadas por Mercado Pago Argentina (`APRO`, `FUND`, `OTHE`).

---

## Constitution Check

*GATE: Evaluación exhaustiva frente a la Constitución SDD del proyecto (v2.0.0).*

| Principio | Estado | Evidencia y Justificación Técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El plan responde estrictamente a los 13 requerimientos (FR-001 a FR-013), las 11 invariantes (INV-01 a INV-11), los 13 casos borde (CB-01 a CB-13) y los 10 criterios de éxito (SC-001 a SC-010) de `spec.md`. No se reabren aclaraciones ya aprobadas (Q1-Q5). |
| **II. Specification Before Implementation** | **PASS** | El proceso de especificación funcional está formalmente completado y aprobado. Los artefactos técnicos de Fase 0 (`research.md`) y Fase 1 (`data-model.md`, `contracts/`, `quickstart.md`, `plan.md`) quedan concluidos y validados antes de generar tareas o código. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Se define una estrategia de pruebas por capas: (1) Unit tests de cálculo de stock y validación de firmas HMAC; (2) Tests de integración con Testcontainers verificando transacciones y persistencia en PostgreSQL; (3) Test de estrés de concurrencia verificando que ante 20 hilos compitiendo por el último recurso solo uno gana (INV-07); (4) Escenarios reproducibles en Sandbox de Mercado Pago (RT-1, RT-3) en `quickstart.md`. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se aplica KISS y alta cohesión: se crea la entidad `TemporaryReservation` y `Payment` justificadas en la necesidad de auditar transacciones delegadas y motivos de liberación sin corromper el modelo de `Order`. Se reutiliza el bloqueo pesimista de `EstablishmentRepository` y el cálculo de stock de `DishAvailabilityEngine`. |
| **V. Bounded Scope** | **PASS** | Alcance rigurosamente acotado: no se introducen otros medios de pago, no se tocan reembolsos ni pantallas de administración, y los casos de carritos con monto cero, aprobaciones tardías y estados pendientes se delegan formalmente a COM-7.2.1. |
| **VI. Controlled Technical Debt** | **PASS** | Se agrega la migración secuencial aditiva `V18__mercadopago_checkout_and_reservations.sql` sin alterar de forma destructiva las tablas preexistentes (`orders`, `order_items`, `establishments`, `dishes`). |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | Trazabilidad completa y bidireccional entre `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md`. Todos los campos, estados y códigos de error están rigurosamente documentados en español neutro profesional. |
| **VIII. Evidence Over Assumption & Escalation** | **PASS** | Todas las decisiones técnicas sobre la API de Mercado Pago se sustentan con URLs oficiales de la documentación oficial de desarrolladores de Mercado Pago Argentina (`mercadopago.com.ar/developers`). No se inventaron endpoints ni cabeceras. |
| **IX. Engineering Over Convenience** | **PASS** | Se implementa verificación criptográfica real de firma de webhooks (HMAC-SHA256 con tiempo constante) y reconciliación REST directa en lugar de asumir ciegamente el payload entrante. Idempotencia soportada por constraints en base de datos. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Entrega un vertical slice full-stack completo: migración DDL Flyway `V18`, entidades JPA, servicios de dominio, controlador REST de checkout, endpoint de webhook, servicio de integración con MP, actualización de `CheckoutPage.tsx`, panel de pago en curso, alertas y página de retorno en frontend. |

---

## Project Structure

### Documentation (this feature)

```text
specs/014-mercadopago-checkout/
├── spec.md                       # Especificación funcional aprobada
├── checklists/
│   └── requirements.md           # Checklist de calidad de la especificación
├── plan.md                       # Este plan de implementación técnica
├── research.md                   # Fase 0: Decisiones arquitectónicas y referencias oficiales MP
├── data-model.md                 # Fase 1: Esquema relacional, entidades JPA y ciclo de vida
├── contracts/                    # Fase 1: Contratos modulares y especificación OpenAPI 3.0
│   ├── openapi.yaml              # Especificación OpenAPI 3.0 unificada
│   ├── 01-payment-initiation.md  # Contrato de inicio de pago (POST /checkout/pay)
│   ├── 02-payment-return.md      # Contrato de retorno al sitio (Back URLs)
│   ├── 03-webhook-notification.md# Contrato de webhook de Mercado Pago
│   ├── 04-in-process-panel.md    # Contrato del panel "Pago en curso" y cancelación
│   ├── 05-order-reservation-status.md # Contrato de estado consolidado de orden y pago
│   └── 06-errors.md              # Contrato de formato de errores RFC 7807
└── quickstart.md                 # Fase 1: Guía paso a paso ejecutable en Sandbox (AS-1 a AS-6)
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
│   │   │   │   │       └── DishAvailabilityEngine.java          # Extendido: resta stock comprometido por reservas activas
│   │   │   │   ├── config/
│   │   │   │   │   └── MercadoPagoProperties.java               # Configuración tipada segregada para Mercado Pago
│   │   │   │   └── order/
│   │   │   │       ├── controller/
│   │   │   │       │   ├── CheckoutPaymentController.java       # POST /checkout/pay, POST /checkout/cancel-payment
│   │   │   │       │   ├── PaymentWebhookController.java        # POST /payments/{provider}/webhook (ruteo desacoplado)
│   │   │   │       │   └── OrderPaymentStatusController.java    # GET /orders/{orderId}/payment-status
│   │   │   │       ├── domain/
│   │   │   │       │   ├── TemporaryReservation.java            # Entidad JPA: reserva temporal conjunta
│   │   │   │       │   ├── ReservationStatus.java               # Enum: ACTIVE, CONFIRMED, RELEASED, EXPIRED
│   │   │   │       │   ├── ReleaseReason.java                   # Enum: REJECTED, CANCELLED_BY_USER, EXPIRED, GATEWAY_ERROR
│   │   │   │       │   ├── Payment.java                         # Entidad JPA: transacción de pasarela
│   │   │   │       │   └── PaymentStatus.java                   # Enum: IN_PROCESS, APPROVED, REJECTED, CANCELLED, FAILED, EXPIRED
│   │   │   │       ├── dto/
│   │   │   │       │   ├── PaymentInitiationRequest.java        # DTO request de checkout (paymentMethod opcional)
│   │   │   │       │   ├── PaymentInitiationResponse.java       # DTO response con redirectUrl
│   │   │   │       │   ├── CancelPaymentRequest.java            # DTO para cancelar intento
│   │   │   │       │   ├── CancelPaymentResponse.java           # DTO respuesta de cancelación
│   │   │   │       │   └── OrderPaymentStatusResponse.java      # DTO estado consolidado
│   │   │   │       ├── repository/
│   │   │   │       │   ├── TemporaryReservationRepository.java  # Consultas de reservas activas y barrido
│   │   │   │       │   └── PaymentRepository.java               # Consultas por preference/gatewayId
│   │   │   │       └── service/
│   │   │   │           ├── CheckoutPaymentService.java          # Orquestador de dominio: reserva atómica y cobro
│   │   │   │           ├── PaymentGatewayPort.java              # Puerto universal agnóstico a proveedores (SOLID DIP)
│   │   │   │           ├── PaymentGatewayResolver.java          # Resolutor dinámico de pasarelas (Strategy Pattern)
│   │   │   │           ├── MercadoPagoGatewayAdapter.java       # Adaptador concreto de Mercado Pago Checkout Pro
│   │   │   │           ├── WebhookSignatureValidator.java       # Validación criptográfica HMAC-SHA256
│   │   │   │           └── ReservationExpirationScheduler.java  # Barredor @Scheduled cada 60s
│   │   │   └── resources/
│   │   │       └── db/migration/
│   │   │           └── V18__mercadopago_checkout_and_reservations.sql # DDL Flyway tablas de reservas y pagos
│   │   └── test/
│   │       └── java/com/easyeat/order/
│   │           ├── AtomicReservationConcurrencyIntegrationTest.java   # 20 hilos compitiendo por último cupo/stock (INV-07)
│   │           ├── CheckoutPaymentIntegrationTest.java                # Escenarios AS-1, AS-2, AS-3, AS-4, AS-5
│   │           ├── MercadoPagoGatewayFailureIntegrationTest.java      # Simulación de indisponibilidad de red (AS-6)
│   │           └── WebhookSignatureValidatorTest.java                 # Verificación unitaria de firmas HMAC
│   └── pom.xml
└── frontend/
    ├── src/
    │   ├── api/
    │   │   └── paymentApi.ts                                    # Clientes Axios para inicio, estado y cancelación
    │   ├── components/
    │   │   └── checkout/
    │   │       ├── InProcessPaymentPanel.tsx                    # Panel "Pago en curso" con Retomar y Cancelar (Q3)
    │   │       └── PaymentAlertBanner.tsx                       # Banner de alerta visible de rechazo/abandono (Q2)
    │   ├── pages/
    │   │   ├── CheckoutPage.tsx                                 # Extendido: gestión de botón Pagar y panel
    │   │   └── PaymentReturnPage.tsx                            # Página de aterrizaje de back_urls con vaciado de carrito (Q4)
    │   ├── router/
    │   │   └── index.tsx                                        # Incorpora ruta /checkout/payment-return
    │   └── types/
    │       └── payment.ts                                       # Tipos TypeScript para pagos y reservas
    └── tests/
        └── checkout/
            ├── CheckoutPaymentFlow.test.tsx                     # Pruebas de integración UI de estados de pago
            └── InProcessPaymentPanel.test.tsx                   # Pruebas de renderizado de panel y acciones
```

**Decisión de Estructura**:  
Se respeta la organización monolítica modular ya consolidada en el repositorio (`easyeat/backend` y `easyeat/frontend`). La nueva lógica se aloja dentro del paquete `com.easyeat.order` y se articula con `com.easyeat.catalog.service.DishAvailabilityEngine` para el cómputo de stock comprometido, minimizando el impacto colateral y manteniendo alta cohesión.

---

## Phase 0 — Research Summary

La Fase 0 se encuentra formalizada en detalle en [research.md](./research.md).  
Principales definiciones consolidadas:
- **Modo Checkout Pro**: Redirección mediante `init_point` o `sandbox_init_point` creada vía `POST /checkout/preferences`. Garantiza aislamiento absoluto de datos de tarjetas (FR-013, INV-11).
- **Modelo de Reserva Conjunta**: Entidad explícita `TemporaryReservation` con restricción de unicidad activa por orden (`UNIQUE WHERE status = 'ACTIVE'`). Atomicidad garantizada mediante transacción pesimista.
- **TTL de 15 Minutos**: Implementación híbrida mediante evaluación perezosa en consultas (`GET /payment-status`) y barredor `@Scheduled` cada 60 s (`ReservationExpirationScheduler`).
- **Idempotencia Transaccional**: Cabecera saliente `X-Idempotency-Key` hacia Mercado Pago y restricción de unicidad en base de datos para procesamiento de webhooks y pagos repetidos (INV-06, CB-07).
- **Validación Criptográfica de Webhooks**: Verificación del header `x-signature` (`ts`, `v1`) con algoritmo HMAC-SHA256 sobre manifest canónico y consulta de confirmación contra `GET /v1/payments/{id}` (INV-08).
- **Sandbox y Reproducibilidad**: Configuración de credenciales de prueba, matriz oficial de tarjetas documentadas (`APRO`, `FUND`, `OTHE`) y simulación controlada de fallos de red (RT-1, RT-2, RT-3).

---

## Phase 1 — Design Summary

La Fase 1 se encuentra distribuida y documentada en los siguientes artefactos:
- **[data-model.md](./data-model.md)**: Modelo relacional, entidades JPA (`Order` extendida, `TemporaryReservation`, `Payment`), diagramas Mermaid ER y State Machine, especificación taxativa de qué se guarda y qué NO se guarda (prohibición de PAN/CVV), y script Flyway `V18`.
- **[contracts/](./contracts/)**: Catálogo de contratos modulares (`01-payment-initiation.md`, `02-payment-return.md`, `03-webhook-notification.md`, `04-in-process-panel.md`, `05-order-reservation-status.md`, `06-errors.md`) y especificación centralizada [openapi.yaml](./contracts/openapi.yaml).
- **[quickstart.md](./quickstart.md)**: Guía de ejecución determinística en Sandbox para validar paso a paso todos los escenarios de aceptación (AS-1, AS-2, AS-2b, AS-3, AS-4, AS-4b, AS-5 y AS-6) y test de no persistencia de datos financieros.

---

## Re-evaluación de Constitution Check Post-Diseño

Tras completar los artefactos de diseño técnico (`research.md`, `data-model.md`, `contracts/` y `quickstart.md`), se re-evalúan los gates de la Constitución SDD v2.0.0:

1. **Principio I (Fuente de Verdad)**: Cumplimiento verificado. Todos los contratos y modelos reflejan fielmente los requerimientos y casos borde de `spec.md`.
2. **Principio II (Especificación previa a la implementación)**: Cumplimiento verificado. No existe código productivo escrito antes de la aprobación del diseño.
3. **Principio III (Verificación Proporcional)**: Cumplimiento verificado. Pruebas unitarias, de integración con Testcontainers, de concurrencia extrema con 20 hilos y escenarios end-to-end en Sandbox documentados en `quickstart.md`.
4. **Principio IV (Diseño Simple y Cohesivo)**: Cumplimiento verificado. Entidades y migraciones normalizadas, sin patrones especulativos ni sobreingeniería.
5. **Principio V (Alcance Acotado)**: Cumplimiento verificado. Sin features no solicitadas; casos delegados a COM-7.2.1 formalmente excluidos.
6. **Principio VI (Deuda Técnica Controlada)**: Cumplimiento verificado. Migración aditiva limpia `V18`.
7. **Principio VII (Trazabilidad)**: Cumplimiento verificado. Trazabilidad completa entre requerimientos, contratos, modelos y tests.
8. **Principio VIII (Evidencia y Escalación)**: Cumplimiento verificado. Referencias citadas de la documentación oficial de Mercado Pago Developers.
9. **Principio IX (Ingeniería sobre Conveniencia)**: Cumplimiento verificado. Idempotencia y seguridad criptográfica implementadas con rigor técnico.
10. **Principio X (Integridad Vertical Slice Full-Stack)**: Cumplimiento verificado. Se cubren todas las capas: base de datos, backend, webhooks, frontend UI y contratos compartidos.

**Resultado del Gate Post-Diseño**: **PASS (10/10 Principios Cumplidos sin Excepciones ni Violaciones)**.

---

## Complexity Tracking

> **Estado**: No se detectaron violaciones a la Constitución que requieran justificación o complejidad accidental. El diseño se ajusta estrictamente a los estándares existentes del repositorio.
