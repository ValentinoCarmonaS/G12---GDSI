# Implementation Plan: Restaurant Operation Configuration: Business Schedules, Capacity Limits & Online Payment Methods

**Branch**: `010-restaurant-operation-config` | **Fecha**: 31/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-restaurant-operation-config/spec.md`

**Companion artifacts**: [research.md](./research.md) · [data-model.md](./data-model.md) · [contracts/openapi.yaml](./contracts/openapi.yaml) · [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md) · [quickstart.md](./quickstart.md)

---

## 1. Summary

Implementar la arquitectura técnica full-stack para la configuración operativa del restaurante, abordando las tres historias de usuario aprobadas:
1. **ADM-2.2.1 (Configurar horarios comerciales)**: Definición de días y rangos horarios exactos de apertura y cierre por parte del Administrador. Bloqueo automático para el día en curso cuando $\text{hora\_actual} \ge \text{hora\_cierre}$, admitiendo reservas para franjas futuras válidas incluso con local cerrado en el presente. Validación en intervalo semi-abierto $[\text{apertura}, \text{cierre})$ e imposición de días cerrados por defecto si carecen de configuración.
2. **ADM-2.2.2 (Configurar capacidad máxima por franja de 15 min)**: Partición determinística de la jornada en bloques de 15 minutos con asignación de capacidad $\text{maxCapacity} \in \mathbb{N}_0$. Actualización atómica en la aprobación que completa cupo para prevenir sobreventa concurrente (EC-016), y marcado inmediato de "Agotada" en el selector del comensal cuando $\text{pedidos\_aprobados} \ge \text{maxCapacity}$, respetando la no retroactividad sobre órdenes aprobadas previamente (INV-8).
3. **ADM-2.2.3 (Habilitar métodos de pago online)**: Interruptor administrativo de software (`enabled: true/false`) para pasarelas online (MercadoPago, Stripe), con renderizado condicional en checkout, revalidación dinámica en submit (INV-9), y bloqueo preventivo si todos los métodos están apagados. Implementación a **costo cero**, 100% offline, sin credenciales bancarias ni SDKs externos (A-005).

---

## 2. Technical Context

- **Language/Version**: Java 21 (LTS) en backend; TypeScript 5.6 / React 18.3.1 en frontend. *(Verificado en `easyeat/backend/pom.xml` y `easyeat/frontend/package.json`)*.
- **Primary Dependencies**:
  - *Backend*: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Spring Validation), Flyway Core 10.x, PostgreSQL Driver (`42.7.4`), JJWT (`0.12.5`), Lombok, Jackson Problem (`problem-spring-web` RFC 7807), Testcontainers (`1.20.1`).
  - *Frontend*: React 18.3.1, React Router v6 (`react-router-dom`), Tailwind CSS v4, Lucide React, Axios (`apiClient.ts`).
- **Storage**: PostgreSQL 16; Flyway versioned migrations. Siguiente migración libre en el repositorio: `V10__restaurant_operation_config.sql`. Tablas nuevas: `establishment_operating_schedules`, `establishment_slot_capacities`, `establishment_payment_methods`.
- **Testing**:
  - *Backend*: JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers (PostgreSQL 16 real).
  - *Frontend*: Vitest 2, React Testing Library, jsdom, `@testing-library/user-event`.
- **Target Platform**: Servidores Linux / contenedores Docker (Spring Boot JAR) + navegadores web modernos estándar (SPA).
- **Project Type**: Full-stack Web Application (Spring Boot REST API Gateway + React SPA) en monorepo (`easyeat/backend`, `easyeat/frontend`).
- **Performance Goals**:
  - Consulta de selector de franjas y checkout: $\le 100\text{ ms}$ en backend; reflejo en UI en $\le 2\text{ s}$ tras guardado administrativo (`SC-006`).
  - Guardado de configuración en $\le 5$ interacciones administrativas (`SC-005`).
- **Constraints**:
  - Aislamiento multi-tenant estricto por `establishment_id` mediante `TenantContextHolder`.
  - Autoridad horaria exclusiva en servidor proyectada contra la zona horaria del restaurante (`Establishment.timezone`, ej. `America/Argentina/Buenos_Aires`).
  - Desacoplamiento total de servicios externos de pago: cero llamadas a APIs bancarias reales, cero costo (`A-005`).

---

## 3. Constitution Check

*GATE: Evaluación frente a la Constitución SDD versión 2.0.0. Aprobación obligatoria antes de avanzar a descomposición de tareas.*

| Principio | Estado | Evidencia y Justificación Técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El plan cubre con exactitud los 19 criterios de aceptación, 18 casos borde, 10 invariantes, 17 requerimientos funcionales y 11 criterios de éxito de `specs/010-restaurant-operation-config/spec.md`. No se añade funcionalidad fuera del spec. |
| **II. Specification Before Implementation** | **PASS** | `spec.md` ratificado previamente sin marcadores pendientes. Se produjeron los artefactos de diseño requeridos (`research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `plan.md`) antes de cualquier codificación o generación de tareas. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Estrategia de pruebas en 4 capas: unitarias (invariantes y cálculo de horas), integración con Testcontainers (DDL Flyway `V10`, constraints, atomicidad condicional), contratos de API y pruebas de componentes frontend (RTL) para selector de franjas y checkout. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se reutiliza `Establishment.timezone` y `LocalTime`, evitando dependencias externas complejas. El control de cupos se realiza con una actualización condicional nativa en SQL (`approvedOrdersCount < maxCapacity`), sin introducir locks distribuidos innecesarios. |
| **V. Bounded Scope** | **PASS** | Alcance rigurosamente delimitado a horarios, matriz de capacidad por franja y visibilidad de métodos de pago. Pasarelas reales, comisiones, cuentas bancarias, hardware fiscal, reportes y reservas de salón quedan explícitamente excluidos en Out of Scope. |
| **VI. Controlled Technical Debt** | **PASS** | Migración limpia `V10` sin tocar tablas previas incompatibles. Se preserva la compatibilidad con el modelo de establecimientos de `V8`. |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | Matriz de trazabilidad bidireccional completa documentada: 19/19 ACs, 18/18 ECs, 10/10 INVs, 17/17 FRs y 11/11 SCs mapeados a decisiones, contratos y pruebas. |
| **VIII. Evidence Over Assumption and Human Escalation** | **PASS** | Decisiones ancladas en código verificado del repositorio (`Establishment.java`, `TenantContextHolder`, `SecurityConfig`). Se documentan 11 supuestos explícitos (`A-001` a `A-011`) con defaults razonables acordados en el grill-me. |
| **IX. Engineering Over Convenience** | **PASS** | Validación atómica en base de datos para prevenir sobreventa concurrente en lugar de chequear en memoria. Formato estructurado RFC 7807 para todos los errores. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Vertical slice completo: backend (Flyway, JPA, repositorios, servicios, endpoints REST) y frontend (panel administrativo en `/admin/operations`, selector de franjas en comensal y checkout dinámico). |

---

## 4. Complexity Tracking

> No se detectaron violaciones a los principios de la Constitución. El diseño adopta los patrones existentes más simples y directos del repositorio.

---

## 5. Project Structure & Source Code Layout

### 5.1 Documentación de la Feature
```text
specs/010-restaurant-operation-config/
├── spec.md                  # Especificación funcional aprobada
├── checklists/
│   └── requirements.md      # Checklist de calidad de requisitos
├── plan.md                  # Este plan técnico de implementación
├── research.md              # Fase 0: investigación y decisiones técnicas
├── data-model.md            # Fase 1: modelo conceptual y esquema DDL
├── quickstart.md            # Fase 1: guía de validación ejecutable
├── contracts/               # Fase 1: contratos de interfaz
│   ├── openapi.yaml         # Especificación OpenAPI 3.0.3
│   └── behavioral-contracts.md # Contratos de comportamiento por operación
└── tasks.md                 # Fase 2: desglose de tareas (a ser generado por /speckit-tasks)
```

### 5.2 Estructura del Código Fuente en el Repositorio

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/easyeat/
│   │   │   │   ├── operations/                                   # Nuevo módulo de operaciones
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── AdminOperatingScheduleController.java # PUT/GET /api/v1/admin/operating-schedules
│   │   │   │   │   │   ├── AdminSlotCapacityController.java      # PUT/GET /api/v1/admin/slot-capacities
│   │   │   │   │   │   ├── AdminPaymentMethodController.java     # PUT/GET /api/v1/admin/payment-methods
│   │   │   │   │   │   ├── DinerTimeSlotController.java          # GET /api/v1/establishments/{id}/time-slots
│   │   │   │   │   │   └── DinerPaymentMethodController.java     # GET & POST validate payment
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── EstablishmentOperatingSchedule.java   # Entidad JPA horarios semanales
│   │   │   │   │   │   ├── EstablishmentSlotCapacity.java        # Entidad JPA capacidad por franja
│   │   │   │   │   │   ├── EstablishmentPaymentMethod.java       # Entidad JPA visibilidad de pagos
│   │   │   │   │   │   ├── PaymentMethod.java                    # Enum: MERCADO_PAGO, STRIPE
│   │   │   │   │   │   └── TimeSlot.java                         # Value Object / DTO para franja de 15 min
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── UpdateScheduleRequest.java
│   │   │   │   │   │   ├── OperatingScheduleResponse.java
│   │   │   │   │   │   ├── UpdateSlotCapacityRequest.java
│   │   │   │   │   │   ├── SlotCapacityAdminResponse.java
│   │   │   │   │   │   ├── TogglePaymentMethodRequest.java
│   │   │   │   │   │   ├── PaymentMethodResponse.java
│   │   │   │   │   │   ├── DinerTimeSlotsResponse.java
│   │   │   │   │   │   └── DinerPaymentMethodResponse.java
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── OperatingScheduleRepository.java
│   │   │   │   │   │   ├── SlotCapacityRepository.java          # Contiene query atómica para incremento de cupo
│   │   │   │   │   │   └── PaymentMethodRepository.java
│   │   │   │   │   └── service/
│   │   │   │   │       ├── OperatingScheduleService.java
│   │   │   │   │       ├── SlotCapacityService.java
│   │   │   │   │       ├── PaymentMethodService.java
│   │   │   │   │       └── DinerTimeSlotService.java             # Genera grilla de 15 min y evalúa INV-1 a INV-3
│   │   │   ├── resources/
│   │   │   │   └── db/migration/
│   │   │   │       └── V10__restaurant_operation_config.sql     # Migración DDL Flyway
│   │   └── test/
│   │       └── java/com/easyeat/operations/
│   │           ├── domain/OperatingInvariantsUnitTest.java      # Tests unitarios puros de INV-1 a INV-10
│   │           ├── repository/SlotCapacityConcurrencyTest.java   # Testcontainers concurrencia en cupo 50 (EC-016)
│   │           └── controller/AdminOperationsControllerTest.java # MockMvc RBAC y validaciones
└── frontend/
    └── src/
        ├── api/
        │   └── operationsApi.ts                                  # Cliente tipado Axios para admin y comensal
        ├── types/
        │   └── operations.ts                                     # Tipos TypeScript para schedules, slots y pagos
        ├── components/
        │   └── operations/
        │       ├── WeeklyScheduleManager.tsx                     # Formulario de días y rangos horarios
        │       ├── SlotCapacityGrid.tsx                          # Grilla de 15 min y edición de capacidad
        │       ├── PaymentMethodToggles.tsx                      # Switches para MercadoPago / Stripe
        │       └── DinerSlotSelector.tsx                         # Selector del comensal con badges "Agotada"
        └── pages/
            └── AdminOperationsPage.tsx                           # Nueva vista en Back-Office (/admin/operations)
```

---

## 6. Architectural & Technical Decisions

### D-001 — Autoridad Horaria Exclusiva del Servidor
- **Decisión**: El servidor evalúa la admisión temporal proyectando `ZonedDateTime.now()` en la zona horaria del restaurante (`Establishment.timezone`, default `America/Argentina/Buenos_Aires`). Se ignora el reloj del cliente.
- **Fuente**: `spec.md §A-008, EC-014, EC-018` + `Establishment.java`.
- **Cubre**: `AC-1.1`, `INV-1`, `SC-001`, `SC-002`, `EC-014`, `EC-018`.
- **Riesgo**: Desincronización del servidor host.
- **Mitigación**: Servidores sincronizados con NTP; tests unitarios usan `Clock` inyectado para simular 15:59, 16:00 y 16:01 determinísticamente.
- **Alternativa descartada**: Confiar en la hora provista por el navegador cliente (vulnerable a fraude o inconsistencias).

### D-002 — Partición Determinística de Franjas en Bloques de 15 Minutos
- **Decisión**: Las franjas se generan mediante un bucle de progresión temporal discreta a partir de `openingTime`, incrementando de a 15 minutos exactos hasta alcanzar `closingTime`.
- **Fuente**: `spec.md §INV-5, FR-005`.
- **Cubre**: `INV-5`, `FR-005`, `EC-015`.
- **Riesgo**: Apertura y cierre no múltiplos de 15 minutos.
- **Mitigación**: Validación de entrada que asegura que apertura y cierre terminen en minutos `:00`, `:15`, `:30` o `:45`.
- **Alternativa descartada**: Intervalos libres o definidos por el comensal (rompe la grilla de slots y satura cocina).

### D-003 — Incremento Atómico Condicional para Control de Capacidad (EC-016)
- **Decisión**: Para admitir/aprobar pedidos sin sobrepasar la capacidad máxima, se ejecuta una sentencia SQL atómica:
  `UPDATE establishment_slot_capacities SET approved_orders_count = approved_orders_count + 1 WHERE id = :id AND approved_orders_count < max_capacity`.
- **Fuente**: `spec.md §INV-2, INV-3, EC-016` + `research.md §D-003`.
- **Cubre**: `AC-2.1`, `INV-2`, `INV-3`, `FR-008`, `FR-009`, `SC-003`, `EC-016`.
- **Riesgo**: Dos transacciones concurrentes aprobando el pedido número 50.
- **Mitigación**: Solo una transacción obtiene filas afectadas = 1; la segunda obtiene 0 y lanza `SlotCapacityExceededException` (409 Conflict).
- **Alternativa descartada**: Bloqueo pesimista `PESSIMISTIC_WRITE` (alta contención y lentitud en cocina).

### D-004 — No Retroactividad sobre Pedidos Aprobados Previamente (INV-8)
- **Decisión**: Si el Administrador reduce la capacidad a un valor inferior a los pedidos ya aprobados (ej. de 50 a 30 con 40 aprobados), la franja transiciona inmediatamente a "Agotada" (`40 >= 30`), pero los 40 pedidos aprobados permanecen inalterados. De igual forma, adelantar el cierre no cancela órdenes previas.
- **Fuente**: `spec.md §INV-8, EC-003, EC-004, EC-005, SC-011`.
- **Cubre**: `INV-8`, `EC-003`, `EC-004`, `EC-005`, `SC-011`, `FR-014`.
- **Riesgo**: Cancelación accidental de pedidos en curso.
- **Mitigación**: Lógica de actualización de configuración no emite eventos de cancelación hacia el dominio de pedidos.
- **Alternativa descartada**: Cancelar pedidos retroactivamente al reducir capacidad (inadmisible operativamente).

### D-005 — Política de Valores por Defecto (Capacidad 0 y Día Cerrado)
- **Decisión**: Días sin horario configurado = `isClosed = true`. Franjas sin configuración explícita de capacidad = `maxCapacity = 0` (Agotada).
- **Fuente**: `spec.md §EC-007, EC-008, FR-015, FR-016, A-010`.
- **Cubre**: `EC-007`, `EC-008`, `FR-015`, `FR-016`, `A-010`.
- **Riesgo**: Establecimiento sin configurar bloquea completamente las ventas.
- **Mitigación**: Es el comportamiento intencional y seguro: evita pedidos en horarios no supervisados por la cocina.
- **Alternativa descartada**: Asumir capacidad infinita o local abierto 24/7 (riesgo de saturación total).

### D-006 — Métodos de Pago a Costo Cero: Interruptor de Visibilidad y Revalidación
- **Decisión**: Modelado como lista fija de métodos (`PaymentMethod`), persistida en `establishment_payment_methods` con flag booleano `is_enabled`. En checkout se filtran los habilitados y en el submit de compra se revalida dinámicamente (`INV-9`).
- **Fuente**: `spec.md §ADM-2.2.3, AC-3.1, INV-4, INV-9, A-005`.
- **Cubre**: `AC-3.1`, `INV-4`, `INV-9`, `FR-010`, `FR-011`, `FR-012`, `FR-013`, `SC-004`, `EC-009`, `EC-010`, `EC-011`.
- **Riesgo**: Intentar integrar pasarelas reales y requerir cuentas comerciales o pagar comisiones.
- **Mitigación**: Declarado explícitamente en `spec.md` y en este plan: cero llamadas externas, cero SDKs, pruebas 100% locales con mocks.
- **Alternativa descartada**: Integración real con MercadoPago SDK / Stripe SDK (prohibido por alcance y costo).

### D-007 — Control de Concurrencia Optimista entre Administradores
- **Decisión**: Uso de `@Version Long version` en las entidades JPA de configuración.
- **Fuente**: `spec.md §EC-012` + `research.md §D-007`.
- **Cubre**: `EC-012`, `SC-010`.
- **Riesgo**: Colisión entre dos administradores modificando la misma franja o día.
- **Mitigación**: La segunda transacción detecta conflicto de versión y retorna HTTP 409 Conflict con mensaje amigable.
- **Alternativa descartada**: Sobreescritura silenciosa (*last-write-wins* descontrolado).

### D-008 — Propagación sin Reinicio ni Infraestructura Compleja
- **Decisión**: El frontend del comensal consulta el estado vivo al abrir el selector de franjas y al ingresar al checkout.
- **Fuente**: `spec.md §SC-006, FR-017`.
- **Cubre**: `SC-006`, `FR-017`, `EC-013`.
- **Riesgo**: Caché estática del navegador mostrando franjas desactualizadas.
- **Mitigación**: Headers HTTP `Cache-Control: no-cache, no-store` en los endpoints de disponibilidad.
- **Alternativa descartada**: Introducir WebSockets o servidores SSE (no existen en el repo y agregan complejidad innecesaria).

---

## 7. Cumplimiento de Invariantes de Negocio

| Invariante | Expresión Lógica / Matemática | Enforce en | Mecanismo de Enforcement | Test de Verificación |
|---|---|---|---|---|
| **INV-1** | $\text{pedido\_admitido} \iff (\text{apertura} \le \text{franja} < \text{cierre}) \land \text{no\_caducada}$ | Backend | `DinerTimeSlotService` evalúa la hora de la franja destino contra el horario del día y bloquea franjas pasadas del día actual. | `OperatingInvariantsUnitTest.shouldBlockExpiredSlotsAndAdmitValidFutureSlots` |
| **INV-2** | $\text{pedido\_admitido\_en\_F} \iff \text{aprobados} < \text{capacidad}$ | Backend | `SlotCapacityService` valida cupo remanente antes de autorizar el ingreso. | `OperatingInvariantsUnitTest.shouldAdmitOnlyWhenApprovedBelowCapacity` |
| **INV-3** | $\text{marcada\_agotada} \iff \text{aprobados} \ge \text{capacidad}$ | Backend + Frontend | Backend proyecta `status = SOLD_OUT`; Frontend renderiza badge "Agotada" y deshabilita selección. | `OperatingInvariantsUnitTest.shouldMarkSoldOutWhenCapacityReachedOrExceeded` |
| **INV-4** | $\text{visible\_checkout} \iff \text{admin\_habilitó} = \text{true}$ | Backend + Frontend | Endpoint `/payment-methods` filtra por `is_enabled = true`; Checkout renderiza únicamente la lista devuelta. | `OperatingInvariantsUnitTest.shouldExposeOnlyEnabledPaymentMethods` |
| **INV-5** | $\text{franja\_F} = \text{bloque\_de\_15\_minutos\_iniciando\_en\_hora\_F}$ | Backend | Generación determinística con `LocalTime.plusMinutes(15)` desde `openingTime` hasta `closingTime`. | `OperatingInvariantsUnitTest.shouldPartitionDayIntoDeterministic15MinSlots` |
| **INV-6** | $\text{capacidad\_maxima} \in \mathbb{N}_0$ | Backend (JPA + DDL) | Validación `@Min(0)` en DTO + constraint `CHECK (max_capacity >= 0)` en PostgreSQL. | `OperatingInvariantsUnitTest.shouldRejectNegativeCapacity` |
| **INV-7** | $\text{hora\_apertura} < \text{hora\_cierre}$ | Backend (JPA + DDL) | Validación cruzada en DTO + constraint `CHECK (is_closed = TRUE OR opening_time < closing_time)`. | `OperatingInvariantsUnitTest.shouldRejectOpeningEqualOrAfterClosing` |
| **INV-8** | $\text{config\_actualizada} \implies \text{no\_altera}(\text{aprobados}) \land \text{aplica}(\text{nuevos})$ | Backend | Modificaciones de capacidad no disparan lógica de actualización ni borrado sobre órdenes existentes. | `OperatingInvariantsUnitTest.shouldPreserveApprovedOrdersWhenCapacityReduced` |
| **INV-9** | $\text{pago\_disponible} \iff \text{admin\_habilitó} = \text{true} \text{ (en submit)}$ | Backend | Revalidación viva en `/checkout/validate-payment`; rechaza transacción si el método fue apagado. | `OperatingInvariantsUnitTest.shouldRejectConfirmationIfPaymentMethodDisabled` |
| **INV-10** | $\text{reserva\_en\_AC-1.1} \equiv \text{pedido\_de\_retiro\_programado}$ | Backend + Dominio | Tratamiento semántico unificado de pedidos de retiro y delivery bajo el régimen de cocina abierta. | `OperatingInvariantsUnitTest.shouldApplyScheduleRulesToAllFulfillmentTypes` |

---

## 8. Estrategia de Manejo de Errores

Todos los errores de validación de entrada, violaciones de invariantes y conflictos de concurrencia se canalizan a través de `GlobalExceptionHandler.java` utilizando el formato estándar **RFC 7807 `ProblemDetail`** (`application/problem+json`):

1. **Horarios inválidos (`INV-7`)**:
   - Status: HTTP 400 Bad Request.
   - Title: "Parámetros de horario inválidos".
   - Detail: "La hora de apertura (17:00) debe ser estrictamente anterior a la hora de cierre (15:00)."
2. **Capacidad negativa (`INV-6`)**:
   - Status: HTTP 400 Bad Request.
   - Title: "Capacidad inválida".
   - Detail: "La capacidad máxima por franja debe ser un número entero mayor o igual a 0."
3. **Franja horaria agotada / Cupo excedido concurrente (`INV-2`, `EC-016`)**:
   - Status: HTTP 409 Conflict.
   - Title: "Franja horaria agotada".
   - Detail: "La franja seleccionada ha alcanzado su capacidad máxima permitida (50 pedidos)."
4. **Método de pago apagado durante el checkout (`INV-9`, `EC-010`)**:
   - Status: HTTP 409 Conflict.
   - Title: "Método de pago no disponible".
   - Detail: "El método de pago 'STRIPE' ha sido deshabilitado por el restaurante. Por favor seleccione otra opción."
5. **Conflicto de edición concurrente (`EC-012`)**:
   - Status: HTTP 409 Conflict.
   - Title: "Conflicto de concurrencia".
   - Detail: "La configuración fue modificada por otro administrador. Por favor recargue la pantalla para ver los cambios."

---

## 9. Estrategia de Testing

```text
Pruebas de Componentes Frontend (Vitest + RTL)
        ↑
Pruebas de Integración y Concurrencia (Spring Boot + Testcontainers PostgreSQL 16)
        ↑
Pruebas de Slices de Controladores y RBAC (MockMvc)
        ↑
Pruebas Unitarias de Dominio e Invariantes (JUnit 5 + AssertJ)
```

1. **Pruebas Unitarias de Invariantes (`OperatingInvariantsUnitTest.java`)**:
   - Cobertura 1:1 de `INV-1` a `INV-10`.
   - Simulación con `Clock` determinístico para el límite exacto de cierre (15:59:59 admitido, 16:00:00 y 16:01:00 bloqueados para hoy; 22:00 admite franja de mañana).
   - Generación determinística de 8 bloques de 15 min en jornada 12:00 a 14:00 (`INV-5`).
2. **Pruebas de Integración y Concurrencia (`SlotCapacityConcurrencyTest.java`)**:
   - Ejecución real en PostgreSQL con Testcontainers.
   - Prueba de estrés con 10 hilos concurrentes intentando tomar el cupo 50: verifica que exactamente 1 gane y 9 reciban 409 Conflict (`EC-016`).
   - Prueba de reducción de capacidad de 50 a 30 con 40 pedidos aprobados: comprueba que ningún pedido sea modificado y la franja quede agotada (`EC-005`).
3. **Pruebas de Controladores y RBAC (`AdminOperationsControllerTest.java`)**:
   - Clientes sin rol `ADMIN` reciben 403 Forbidden en endpoints administrativos.
   - Clientes anónimos reciben 401 Unauthorized.
4. **Pruebas de Componentes Frontend (RTL)**:
   - `WeeklyScheduleManager.test.tsx`: Validación de formulario y bloqueo ante apertura $\ge$ cierre.
   - `SlotCapacityGrid.test.tsx`: Renderizado de matriz y actualización de cupos.
   - `DinerSlotSelector.test.tsx`: Renderizado de badge "Agotada" y deshabilitación de click en franjas saturadas.
   - `CheckoutPaymentOptions.test.tsx`: Ocultamiento de métodos apagados y mensaje ante todos apagados.

---

## 10. Matriz de Trazabilidad Completa

| Elemento del Spec | Tipo | Decisión / Diseño | Test de Verificación |
|---|---|---|---|
| **US1 (ADM-2.2.1)** | Historia P1 | D-001, D-002, Contrato 1 | `AdminOperationsControllerTest`, `WeeklyScheduleManager.test` |
| **US1-AC1** | Aceptación | D-001, INV-1, FR-003 | `OperatingInvariantsUnitTest.shouldBlockAt1601` |
| **US1-AC2** | Aceptación | D-001, FR-001, Contrato 1 | `AdminOperationsControllerTest.shouldSaveSchedule` |
| **US1-AC3** | Aceptación | D-001, INV-1, FR-004 | `OperatingInvariantsUnitTest.shouldBlockBeforeOpening` |
| **US1-AC4** | Aceptación | D-001, EC-002, FR-003 | `OperatingInvariantsUnitTest.shouldAllowApprovalAfterClosingIfAdmittedBefore` |
| **US1-AC5** | Aceptación | D-001, INV-7, FR-002 | `OperatingInvariantsUnitTest.shouldRejectOpeningEqualOrAfterClosing` |
| **US1-AC6** | Aceptación | D-005, EC-008, FR-016 | `OperatingInvariantsUnitTest.shouldTreatUnconfiguredDayAsClosed` |
| **US1-AC7** | Aceptación | D-001, EC-017, FR-004 | `OperatingInvariantsUnitTest.shouldAdmitFutureReservationWhenClosedToday` |
| **US2 (ADM-2.2.2)** | Historia P1 | D-002, D-003, Contrato 2 | `SlotCapacityConcurrencyTest`, `SlotCapacityGrid.test` |
| **US2-AC1** | Aceptación | D-002, D-003, INV-3 | `OperatingInvariantsUnitTest.shouldMarkSoldOutAtOrder50` |
| **US2-AC2** | Aceptación | D-002, INV-3, Contrato 3 | `DinerSlotSelector.test.shouldRenderSoldOutBadge` |
| **US2-AC3** | Aceptación | D-002, FR-006, Contrato 2 | `AdminOperationsControllerTest.shouldUpdateCapacity` |
| **US2-AC4** | Aceptación | D-002, INV-6, FR-007 | `OperatingInvariantsUnitTest.shouldRejectNegativeCapacity` |
| **US2-AC5** | Aceptación | D-004, EC-005, INV-8 | `SlotCapacityConcurrencyTest.shouldHandleCapacityReduction` |
| **US2-AC6** | Aceptación | D-004, EC-004, FR-008 | `OperatingInvariantsUnitTest.shouldReopenSlotWhenCapacityIncreased` |
| **US2-AC7** | Aceptación | D-005, EC-006, FR-006 | `OperatingInvariantsUnitTest.shouldTreatZeroCapacityAsPermanentlySoldOut` |
| **US3 (ADM-2.2.3)** | Historia P2 | D-006, Contrato 4, 5, 6 | `PaymentMethodToggles.test`, `CheckoutPaymentOptions.test` |
| **US3-AC1** | Aceptación | D-006, INV-4, FR-011 | `OperatingInvariantsUnitTest.shouldHideDisabledPaymentMethod` |
| **US3-AC2** | Aceptación | D-006, INV-4, FR-010 | `OperatingInvariantsUnitTest.shouldShowReenabledPaymentMethod` |
| **US3-AC3** | Aceptación | D-006, EC-009, FR-013 | `CheckoutPaymentOptions.test.shouldRenderAllDisabledMessage` |
| **US3-AC4** | Aceptación | D-006, INV-9, FR-012 | `OperatingInvariantsUnitTest.shouldRejectConfirmationIfPaymentMethodDisabled` |
| **US3-AC5** | Aceptación | D-006, A-005 | `OperatingInvariantsUnitTest.shouldOperateOfflineWithoutExternalGateways` |
| **EC-001** | Caso Borde | D-001, INV-1 | `OperatingInvariantsUnitTest.shouldBlockAtExactClosingMinute` |
| **EC-002** | Caso Borde | D-001, US1-AC4 | `OperatingInvariantsUnitTest.shouldProcessApprovalAfterClosing` |
| **EC-003** | Caso Borde | D-004, INV-8 | `OperatingInvariantsUnitTest.shouldPreservePendingOrdersOnScheduleChange` |
| **EC-004** | Caso Borde | D-004, US2-AC6 | `OperatingInvariantsUnitTest.shouldReopenSlotWhenCapacityIncreased` |
| **EC-005** | Caso Borde | D-004, INV-8 | `SlotCapacityConcurrencyTest.shouldHandleCapacityReduction` |
| **EC-006** | Caso Borde | D-005, US2-AC7 | `OperatingInvariantsUnitTest.shouldTreatZeroCapacityAsPermanentlySoldOut` |
| **EC-007** | Caso Borde | D-005, FR-015 | `OperatingInvariantsUnitTest.shouldDefaultUnconfiguredSlotToZeroCapacity` |
| **EC-008** | Caso Borde | D-005, FR-016 | `OperatingInvariantsUnitTest.shouldTreatUnconfiguredDayAsClosed` |
| **EC-009** | Caso Borde | D-006, US3-AC3 | `CheckoutPaymentOptions.test.shouldRenderAllDisabledMessage` |
| **EC-010** | Caso Borde | D-006, INV-9 | `OperatingInvariantsUnitTest.shouldRejectConfirmationIfPaymentMethodDisabled` |
| **EC-011** | Caso Borde | D-006, Contrato 5 | `CheckoutPaymentOptions.test.shouldShowNewlyEnabledMethodOnRefresh` |
| **EC-012** | Caso Borde | D-007 | `AdminOperationsControllerTest.shouldFailOnOptimisticLockConflict` |
| **EC-013** | Caso Borde | D-008, INV-2 | `DinerSlotSelector.test.shouldRevalidateSlotBeforeOrderCreation` |
| **EC-014** | Caso Borde | D-001, A-008 | `OperatingInvariantsUnitTest.shouldEvaluateAgainstEstablishmentTimezone` |
| **EC-015** | Caso Borde | D-002, INV-5 | `OperatingInvariantsUnitTest.shouldHandleContiguousSlotsIndependently` |
| **EC-016** | Caso Borde | D-003 | `SlotCapacityConcurrencyTest.shouldPreventOvercapacityOnOrder50` |
| **EC-017** | Caso Borde | D-001, US1-AC7 | `OperatingInvariantsUnitTest.shouldAdmitFutureReservationWhenClosedToday` |
| **EC-018** | Caso Borde | D-001 | `OperatingInvariantsUnitTest.shouldIgnoreClientClockDrift` |
| **INV-1…INV-10** | Invariantes | D-001…D-006, Tabla §7 | Cobertura en `OperatingInvariantsUnitTest.java` |
| **FR-001…FR-017** | Requerimientos | D-001…D-008, DDL V10 | Cobertura en controladores, servicios y repositorios |
| **SC-001…SC-011** | Criterios Éxito | D-001…D-008, Quickstart | Verificados en suites unitarias, e2e y quickstart |

---

## 11. Assumptions & Dependencias

- **A-001**: `[SUPUESTO]` Rol `ADMINISTRADOR` / `ADMIN` autenticado preexistente provisto por specs 001, 002 y 004.
- **A-002**: `[SUPUESTO]` Estado `APPROVED` de órdenes existente conceptualmente para disparar el consumo de cupos en franjas.
- **A-003**: `[SUPUESTO]` Componente selector de franjas integrado en la experiencia de compra del comensal.
- **A-004**: `[SUPUESTO]` Pantalla de checkout que consulta y renderiza las opciones de pago online.
- **A-005**: `[SUPUESTO]` Pasarelas de pago online modeladas como flags booleanos a costo cero, sin llamadas externas.
- **A-006**: `[SUPUESTO]` El término "reserva" en AC-1.1 se interpreta como pedido de retiro programado (pickup), y delivery sigue la misma restricción de cocina cerrada.
- **A-007**: `[SUPUESTO]` Intervalo semi-abierto $[\text{apertura}, \text{cierre})$; 16:00:00 se considera cerrado.
- **A-008**: `[SUPUESTO]` Zona horaria única de cada local gobernada por `Establishment.timezone`.
- **A-009**: `[SUPUESTO]` No retroactividad de configuraciones sobre pedidos ya aprobados (INV-8).
- **A-010**: `[SUPUESTO]` Franjas sin configurar = capacidad 0; días sin horario = cerrados.
- **A-011**: `[SUPUESTO]` Máximo 5 interacciones para guardar cualquier bloque de configuración en el Back-Office.

---

## 12. Out of Scope (Preservado Íntegro del Spec)

- Integración real o profunda de bajo nivel con las APIs de MercadoPago, Stripe u otras pasarelas financieras (prohibido explícitamente el uso de credenciales reales o llamadas HTTP a terceros).
- Hardware fiscal, controladores fiscales físicos, impresoras de comandas fiscales e integración con AFIP, CAE o facturación electrónica legal.
- Procesamiento de cobros directos, conciliación bancaria, acreditación de fondos, transferencias, retenciones impositivas, reembolsos o contracargos.
- Configuración de comisiones porcentuales de pasarelas, tasas bancarias, claves privadas de API o vinculación de cuentas bancarias de la empresa.
- Módulos de autenticación, registro de usuarios, reseteo de claves y asignación de roles para el personal.
- Soporte para múltiples sucursales bajo una misma cuenta o gestión de múltiples zonas horarias simultáneas para un mismo local.
- Gestión de calendarios de feriados nacionales, días no laborables dinámicos, horarios especiales de festividades o cierres extraordinarios programados.
- Generación de reportes analíticos de ocupación de cocina, métricas de rendimiento por franja o historial y auditoría de modificaciones de configuración.
- Envío de notificaciones al comensal por correo electrónico, SMS o mensajería push ante cambios en los horarios o estados de franjas.
- Modificación, reprogramación, edición de ítems o cancelación de pedidos que ya hubieran sido aprobados.
- Reasignación de pedidos entre diferentes franjas horarias o entre establecimientos.
- Capacidad máxima diferenciada según el canal de entrega (cupos separados para retiro en mostrador vs. delivery).
- Sistema de reservas de mesas físicas para comensales en el salón del restaurante.
- Bloqueo o reserva de stock de platos por franja horaria.
- Mecanismos de lista de espera, turnos virtuales o colas de espera automáticas cuando una franja horaria se encuentra agotada.

---

## 13. Riesgos y Mitigaciones

| Riesgo Técnico | Impacto | Probabilidad | Estrategia de Mitigación |
|---|---|---|---|
| Carrera en el pedido 50 que sobrepasa la capacidad en cocina | Alto | Media | Actualización condicional en SQL (`approvedOrdersCount < maxCapacity`) que asegura atomicidad estricta (`D-003`). |
| Inconsistencia por desincronización de reloj del cliente | Medio | Alta | Servidor como única autoridad temporal proyectado con `Establishment.timezone` (`D-001`). |
| Comensal pagando con método recién apagado por el Administrador | Medio | Baja | Revalidación dinámica obligatoria en el endpoint de submit de orden (`INV-9`, `D-006`). |
| Conflictos de edición simultánea entre dos Administradores | Bajo | Baja | Control de concurrencia optimista mediante columna `@Version` en las entidades JPA (`D-007`). |

---

## 14. Fases de Implementación (Alto Nivel)

1. **Fase 1 — Migración de Base de Datos y Entidades de Dominio**:
   - Creación de `V10__restaurant_operation_config.sql`.
   - Entidades JPA: `EstablishmentOperatingSchedule`, `EstablishmentSlotCapacity`, `EstablishmentPaymentMethod`.
   - Repositorios Spring Data con query atómica de cupos.
2. **Fase 2 — Lógica de Horarios y Admisión Temporal**:
   - Servicios para partición de bloques de 15 min y evaluación de horarios con `ZoneId`.
   - Controladores REST de horarios para Administrador y Comensal.
   - Tests unitarios de `INV-1`, `INV-5`, `INV-7` y `INV-10`.
3. **Fase 3 — Gestión de Capacidad y Atomicidad en Aprobación**:
   - Lógica de actualización de capacidades, evaluación de `isSoldOut` y servicio de consumo atómico de cupo.
   - Controladores REST de matriz de capacidad y selector de franjas.
   - Tests de concurrencia Testcontainers para `EC-016` y tests unitarios de `INV-2`, `INV-3`, `INV-6`, `INV-8`.
4. **Fase 4 — Toggles de Métodos de Pago a Costo Cero y Revalidación**:
   - Servicios de habilitación/deshabilitación y filtrado condicional en checkout.
   - Revalidación en tiempo real en submit de orden (`INV-9`).
   - Tests unitarios y de controladores para `INV-4`, `INV-9`, `EC-009`, `EC-010`.
5. **Fase 5 — Vistas Frontend e Integración End-to-End**:
   - Vistas en Back-Office (`AdminOperationsPage` con horarios, matriz y toggles).
   - Componente `DinerSlotSelector` con badges "Agotada" y checkout con renderizado condicional.
   - Validación completa contra [quickstart.md](./quickstart.md).

---

## 15. Resumen Ejecutivo de Cierre

- **Stack Verificado**: Java 21, Spring Boot 3.3.4, PostgreSQL 16 (Flyway V10), React 18.3.1, TypeScript 5.6, Tailwind CSS.
- **Decisiones Clave**: Autoridad horaria del servidor con `Establishment.timezone`, incremento condicional atómico para cupos (`EC-016`), evaluación dinámica de pagos (`INV-9`), desacoplamiento a costo cero (`A-005`), control de concurrencia optimista (`@Version`).
- **Cobertura Total**: 19/19 Acceptance Scenarios, 18/18 Edge Cases, 10/10 Invariantes de Negocio, 17/17 Requerimientos Funcionales, 11/11 Criterios de Éxito.
- **Garantía**: Cero marcadores `[NEEDS CLARIFICATION]` pendientes. **NO se generaron tareas de implementación** (reservadas para `/speckit-tasks`).
