# Plan de implementación: protocolo antifraude de entrega y cierre de orden

**Rama**: `022-pin-antifraud-delivery-completion` | **Fecha**: 15/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Entrada**: Especificación funcional desde `/specs/022-pin-antifraud-delivery-completion/spec.md`  
**Prerrequisito de integración**: Requiere la implementación e integración previa de la especificación `020-counter-dispatch-ready-orders` (proveedora de `CounterTicketCard.tsx`, `useCounterOrdersPolling.ts`, `CounterWorkspacePage.tsx` y `GET /api/v1/counter/orders`). **Cumplido el 16/11/2026**: la rama de la spec se avanzó a `develop/sprint-5`, que integra 019, 020 y 021; los artefactos se alinearon con ese código (research §3).

---

> [!IMPORTANT]
> **Estrategia de precedencia y cohesión**: Para garantizar máxima cohesión de dominio y mínimo acoplamiento (evitando reimplementaciones paralelas o colisiones en Git), la presente especificación extiende de forma quirúrgica los componentes de mostrador provistos por la especificación 020. Su implementación en código debe ejecutarse de forma secuencial una vez estabilizada la 020.

---

## Resumen

El presente plan diseña la arquitectura técnica vertical e incremental para el "Protocolo antifraude de entrega y cierre de orden", cubriendo las historias de usuario `ENC-3.2.1`, `ENC-3.3.1` y `ENC-3.3.2`:

1. **Protocolo antifraude de verificación oral (`ENC-3.2.1` — `RF-001` a `RF-005`)**:
   - El `security_code` aleatorio de 4 dígitos en $[1000, 9999]$ ya existe (spec 020: `VARCHAR(4)` con `CHECK ~ '^[1-9][0-9]{3}$'`, migración `V26`, `String securityCode`, generado por `OrderService.generateSecurityCode()` y por el `@PrePersist` de `Order.java`). Esta spec agrega solo `orders.completed_at` mediante `V28__add_completed_at_to_orders.sql` (ver research §3).
   - Inmutabilidad de escritura única en el dominio (`Order.assignSecurityCodeIfAbsent`), que reemplaza los `setSecurityCode` de los servicios (research D-02).
   - Componente visual compartido `components/common/SecurityPinDisplay.tsx` que aplica tipografía de ancho fijo destacada (`font-mono`) con espaciado amplio (`tracking-[0.25em]` estilo `d d d d`) y marcador neutral `- - - -` ante eventuales órdenes sin PIN (Caso Borde 10). Reutilizado idénticamente en la tarjeta de mostrador y en la pantalla de seguimiento del comensal.
2. **Acción directa, confirmación verde y anti doble clic (`ENC-3.3.1` — `RF-006` a `RF-009`)**:
   - Botón directo "ENTREGAR PEDIDO" en la tarjeta desplegada de mostrador accionado con un único clic, sin modales ni cuadros emergentes de confirmación.
   - Inhabilitación instantánea síncrona en cliente (`isSubmitting = true`, `disabled`) antes de la petición asíncrona, inmunizando la interfaz contra pulsaciones múltiples.
   - Retroalimentación verde inmediata ("Entrega Confirmada") desplegada en el mismo tick de la interacción (< 50 ms).
   - Manejo de fallos de red con notificación no intrusiva, reestablecimiento del botón y preservación de la tarjeta visible para reintento manual (`RF-018`).
3. **Archivado atómico, idempotencia y cierre de ciclo (`ENC-3.3.2` — `RF-010` a `RF-017`)**:
   - Endpoint backend `POST /api/v1/counter/orders/{orderId}/complete-delivery` bajo `@Transactional(isolation = Isolation.READ_COMMITTED)` coordinado mediante bloqueo pesimista `orderRepository.findByIdForUpdate(orderId)`.
   - Transición atómica e indivisible: `Order.status = COMPLETED`, `KdsTicket.status = COMPLETED` y registro simultáneo de `completed_at = Instant.now()`.
   - Idempotencia estricta: si la orden ya está `COMPLETED`, responde `200 OK` con el `completed_at` original sin alterar datos ni duplicar efectos secundarios. Ante estados previos inválidos (`PENDING_PAYMENT`, `IN_PROGRESS`, etc.), responde `409 Conflict`.
   - Remoción de mostrador sin layout shift: eliminación optimista de la comanda entregada preservando las claves de identidad (`key={order.orderId}`) y el estado del conjunto `expandedOrderIds` de las tarjetas adyacentes.
   - Exclusión automática en el polling de mostrador (`GET /api/v1/counter/orders`) mediante filtrado estricto `status = READY_FOR_PICKUP`.
   - Conmutación terminal de seguimiento (`/seguimiento/{orderId}`): mensaje definitivo `"Pedido Entregado — ¡Que disfrutes tu comida!"`, resumen íntegro de compra, purga total del PIN en el payload HTTP (`securityCode: null`) y en la vista, y cese permanente del sondeo en `useOrderLiveTrackingPolling.ts`.
   - Aislamiento total con KDS de cocina (spec 019): las comandas en `COMPLETED` quedan excluidas de las columnas operativas de cocina.

---

## Contexto técnico

- **Lenguaje / Versión**: Java 21 (Backend), TypeScript 5.x / React 18 (Frontend).
- **Dependencias principales**:
  - Backend: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Validation), Flyway, PostgreSQL Driver.
  - Frontend: React 18, React Router DOM 6, Axios, Tailwind CSS, Lucide React, Vitest.
- **Persistencia**:
  - Base de datos relacional PostgreSQL.
  - Tablas afectadas: `orders` (agrega `completed_at TIMESTAMPTZ`; reutiliza `security_code VARCHAR(4)` de la spec 020), `kds_tickets` (utiliza su columna existente `completed_at`).
  - Migración incremental Flyway: `V28__add_completed_at_to_orders.sql` (registrada en `.specify/memory/migrations.md`).
- **Pruebas**:
  - Backend: JUnit 5, Mockito, Spring `@WebMvcTest`, Testcontainers PostgreSQL (`BaseIntegrationTest`).
  - Frontend: Vitest + React Testing Library (`easyeat/frontend/tests/`).
- **Plataforma objetivo**: Entorno Docker / Render (Backend Linux), terminales web responsivas de mostrador y teléfonos móviles de comensales (Frontend SPA).
- **Tipo de proyecto**: Aplicación Web Full-Stack (Backend REST API + Frontend SPA).
- **Objetivos de rendimiento**:
  - Latencia en cliente ante clic en "ENTREGAR PEDIDO": inhabilitación y feedback visual verde en menos de 50 milisegundos.
  - Transición atómica de entrega en base de datos: ejecución en menos de 30 milisegundos bajo bloqueo de fila.
  - Cese inmediato de tráfico en comensal al recibir `COMPLETED`: cero peticiones subsiguientes.
- **Restricciones transaccionales y de dominio**:
  - Rango numérico cerrado de PIN: $1000 \le \text{security\_code} \le 9999$.
  - Inmutabilidad absoluta de `security_code` y `completed_at`.
  - Idempotencia determinista en reintentos HTTP.
  - No exponer el PIN en la respuesta terminal de seguimiento.
- **Alcance / Escala**:
  - Vertical slice completo: 1 migración Flyway, 1 entidad extendida (`Order`), 1 servicio nuevo (`CounterOrderService`) y 1 controlador extendido (`CounterOrderController`, spec 020), 1 servicio adaptado (`OrderLiveTrackingService`), 1 componente compartido (`SecurityPinDisplay`), extensión de tarjetas de mostrador (`CounterTicketCard`) y pantalla de seguimiento (`ReadyForPickupCallout`, `DeliveredOrderCallout`, `useOrderLiveTrackingPolling`).

---

## Verificación de la Constitución (Constitution Check)

*GATE: Evaluación frente a la Constitución del Proyecto SDD (v2.0.0).*

| Principio Constitucional | Estado | Justificación y evidencia técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **CUMPLE** | Todas las decisiones técnicas provienen directamente de los RF, invariantes y escenarios de aceptación de `spec.md` (`ENC-3.2.1`, `ENC-3.3.1`, `ENC-3.3.2`). No se alteraron reglas funcionales. |
| **II. Specification Before Implementation** | **CUMPLE** | La especificación funcional fue previamente aprobada y auditada con checklist de calidad antes de generar este plan técnico. No se ha generado código de producción ni tareas de ejecución en esta fase. |
| **III. Layered Verification Proportional to Risk** | **CUMPLE** | Se definen escenarios de prueba reproducibles en `quickstart.md` que cubren caminos felices, casos borde (1000/9999, ausencia de PIN, dobles clics rápidos, cortes de red, concurrencia entre terminales múltiples e idempotencia). |
| **IV. Simple, Cohesive, and Justified Design** | **CUMPLE** | Se reutiliza la generación de PIN existente de la spec 020 (`@PrePersist` + `OrderService.generateSecurityCode()`), el bloqueo pesimista ya disponible en `OrderRepository.findByIdForUpdate` y los componentes existentes de layout, evitando frameworks o brokers externos. |
| **V. Bounded Scope** | **CUMPLE** | El plan se ciñe estrictamente al protocolo antifraude oral, la acción directa de entrega y el archivado terminal. Se excluyeron taxativamente lectores biométricos/ópticos, validación por software del PIN, modales bloqueantes, gestión de no-show/merma y conciliación de pagos. |
| **VI. Controlled Technical Debt** | **CUMPLE** | Se resuelve la parada prematura de polling detectada en `useOrderLiveTrackingPolling.ts` de la spec 021 sin arrastrar inconsistencias hacia el cierre definitivo. |
| **VII. Traceability, Consistency, and Documentation** | **CUMPLE** | Trazabilidad completa en la cadena: `spec.md` → `research.md` → `data-model.md` → `/contracts/` → `quickstart.md` → `plan.md`. |
| **VIII. Evidence Over Assumption and Human Escalation** | **CUMPLE** | Se relevó la base de código real antes de asumir la existencia de campos. Cada decisión de diseño con alternativas técnicas válidas fue escalada y acordada con el usuario mediante la sesión de `grill-me`. |
| **IX. Engineering Over Convenience** | **CUMPLE** | No se omitió la atomicidad ni la idempotencia para simplificar la implementación; se garantizó la seguridad transaccional pesimista y el purgado del PIN en backend. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **CUMPLE** | Se cubre de punta a punta la entrega: migración Flyway, entidades relacionales, repositorios, controladores REST autenticados, DTOs, componentes React, hooks y pruebas automatizadas en ambas capas. |
| **Additional Constraints (Human Escalation)** | **CUMPLE** | Se utilizó el mecanismo formal `grill-me` (preguntas y recomendaciones una a una) para consensuar los 5 puntos clave de arquitectura. |

---

## Estructura del proyecto

### Documentación de la funcionalidad (`specs/022-pin-antifraud-delivery-completion/`)

```text
specs/022-pin-antifraud-delivery-completion/
├── spec.md                         # Especificación funcional aprobada
├── plan.md                         # Plan de implementación técnica (este artefacto)
├── research.md                     # Hallazgos del repo y resoluciones arquitectónicas (Fase 0)
├── data-model.md                   # Modelo de entidades, migración Flyway y estados (Fase 1)
├── quickstart.md                   # Guía de validación end-to-end reproducible (Fase 1)
├── contracts/                      # Contratos formales de API HTTP (Fase 1)
│   ├── 01-complete-delivery.md     # POST /api/v1/counter/orders/{orderId}/complete-delivery
│   ├── 02-counter-orders-polling.md# GET /api/v1/counter/orders
│   └── 03-order-live-tracking.md   # GET /api/v1/orders/{orderId}/live-tracking
└── checklists/                     # Auditorías de calidad de requisitos
    ├── requirements.md
    └── pin-antifraud-delivery-completion-quality.md
```

### Código fuente a impactar (`easyeat/`)

```text
easyeat/
├── backend/
│   └── src/
│       ├── main/
│       │   ├── java/com/easyeat/order/
│       │   │   ├── controller/
│       │   │   │   ├── CounterOrderController.java              # EXISTENTE (020): agregar complete-delivery; quitar PIN fabricado
│       │   │   │   └── OrderConfirmationTrackingController.java # Mapeo de live-tracking
│       │   │   ├── domain/
│       │   │   │   ├── Order.java                               # Extender con completed_at y escritura única de security_code
│       │   │   │   └── KdsTicket.java                           # Reutilizar completed_at existente
│       │   │   ├── dto/
│       │   │   │   ├── CompleteDeliveryResponse.java            # NUEVO: payload de respuesta terminal
│       │   │   │   ├── CounterReadyOrderResponse.java           # EXISTENTE (020): sin cambios de forma
│       │   │   │   └── OrderLiveTrackingResponse.java           # EXISTENTE (021): ya expone String securityCode
│       │   │   ├── repository/
│       │   │   │   ├── OrderRepository.java                     # Reutilizar findByIdForUpdate (PESSIMISTIC_WRITE)
│       │   │   │   └── KdsTicketRepository.java                 # Consultas de ticket vinculado
│       │   │   └── service/
│       │   │       ├── CounterOrderService.java                 # NUEVO: lógica transaccional atómica e idempotente
│       │   │       └── OrderLiveTrackingService.java            # Adaptar mensaje COMPLETED y omisión de PIN
│       │   └── resources/
│       │       └── db/migration/
│       │           └── V28__add_completed_at_to_orders.sql      # NUEVA migración Flyway
│       └── test/java/com/easyeat/order/
│           ├── controller/
│           │   └── CounterOrderControllerTest.java              # Pruebas MVC unitarias
│           └── service/
│               └── CompleteDeliveryIntegrationTest.java         # Testcontainers: atomicidad, concurrencia e idempotencia
└── frontend/
    └── src/
        ├── api/
        │   ├── counterApi.ts                                    # EXISTENTE (020): agregar completeDelivery (T030)
        │   └── orderTrackingApi.ts                              # Tipado de respuesta live-tracking
        ├── components/
        │   ├── common/
        │   │   └── SecurityPinDisplay.tsx                       # NUEVO: componente compartido monospace d d d d
        │   ├── counter/
        │   │   └── CounterTicketCard.tsx                        # Tarjeta desplegable con PIN, botón verde y anti doble clic
        │   └── tracking/
        │       ├── ReadyForPickupCallout.tsx                    # Renderizar SecurityPinDisplay
        │       └── DeliveredOrderCallout.tsx                    # NUEVO: estado terminal 'Pedido Entregado'
        ├── hooks/
        │   ├── useCounterOrdersPolling.ts                       # Polling de mostrador con remoción reactiva
        │   └── useOrderLiveTrackingPolling.ts                   # Continuar polling en READY y cesar en COMPLETED
        ├── pages/
        │   ├── CounterWorkspacePage.tsx                         # Integración de tablero de mostrador
        │   └── OrderConfirmationTrackingPage.tsx                # Integración de vista terminal
        ├── types/
        │   ├── counter.ts                                       # EXISTENTE (020): securityCode nullable + CompleteDeliveryResponse
        │   └── orderTracking.ts                                 # EXISTENTE (021): ya incluye securityCode y COMPLETED
        └── test/
            └── hooks/
                └── useOrderLiveTrackingPolling.test.ts          # EXISTENTE (021): pruebas de cese definitivo ante COMPLETED
    └── tests/
        └── components/                                          # Ubicación real de las pruebas de componentes (corregido 17/11/2026, T008)
            ├── SecurityPinDisplay.test.tsx                      # Pruebas de renderizado monospace y fallback
            └── CounterTicketCard.test.tsx                       # EXISTENTE (020): acción directa, deshabilitado y feedback verde
```

---

## Seguimiento de complejidad

| Decisión / Mecanismo | Por qué es necesaria | Alternativa más simple descartada |
| :--- | :--- | :--- |
| Migración Flyway `V28` para añadir `completed_at` a `orders` | `orders` no contenía la marca temporal de entrega (`RF-011`); `security_code` ya existe desde la spec 020 (`V26`). | Reutilizar solo `kds_tickets.completed_at`: descartado porque la orden es la entidad del comensal y el contrato 01 devuelve `completedAt` de la orden. |
| Bloqueo pesimista `orderRepository.findByIdForUpdate` en `complete-delivery` | Previene condiciones de carrera cuando dos encargados pulsan la entrega casi en simultáneo, serializando la transición y garantizando la idempotencia en el segundo hilo (`SC-005`, `SC-007`). | Bloqueo optimista (`@Version`): descartado porque requeriría agregar columnas de versión en tablas ya estabilizadas y arrojaría errores de concurrencia al usuario en lugar de responder `200 OK` idempotente. |
| Escritura única `Order.assignSecurityCodeIfAbsent` | Hace cumplir RF-002 en todos los caminos sin perder la asignación tardía de PIN a órdenes heredadas en la confirmación (research D-02). | `@Column(updatable = false)`: descartado porque Hibernate omitiría la columna en el `UPDATE` de confirmación y el PIN se perdería en silencio. |
| Componente React compartido `SecurityPinDisplay.tsx` | Garantiza consistencia tipográfica monospace y espaciado amplio idéntico (`d d d d`) entre mostrador y seguimiento móvil, centralizando el caso borde de PIN nulo. | Clases CSS aplicadas manualmente en cada pantalla: descartada por riesgo de deriva visual. |
| Ajuste del ciclo de polling en `useOrderLiveTrackingPolling.ts` | Permite que el comensal conectado en ventanilla detecte en vivo la entrega sin recargar manualmente la página, deteniendo el sondeo únicamente tras recibir `COMPLETED`. | Dejar `READY_FOR_PICKUP` como estado de parada de polling: descartada porque incumplía el Caso Borde 8. |
| Purga de `securityCode` en el payload de `COMPLETED` | Protege el PIN en origen a nivel de backend para evitar inspección en DevTools tras concretar la entrega (`RF-016`). | Ocultamiento exclusivo en React manteniendo el valor en el JSON: descartado por seguridad. |
