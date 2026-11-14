# Implementation Plan: 016 Payment Confirmation, Live Tracking & Restaurant Branding

**Branch**: `016-payment-confirmation-tracking-branding` | **Fecha**: 09/11/2026 | **Spec**: [spec.md](./spec.md)  
**Input**: Especificación funcional aprobada desde `/specs/016-payment-confirmation-tracking-branding/spec.md`  

---

## 1. Summary

Implementar la experiencia integral posterior al pago para el comensal, el seguimiento dinámico del pedido en vivo y la personalización de la identidad visual de marca del restaurante para EasyEat, unificando y resolviendo las tres historias de usuario centrales:
1. **`COM-7.3.1` — Confirmación de pago (P1)**:
   - Redirección inmediata a pantalla verde de confirmación con animación de verificación, exhibición destacada del número de orden asignado y detalle de la franja horaria pactada (**AS-1**, **AS-3**, **FR-001**, **FR-003**, **INV-001**, **INV-004**).
   - Bloqueo preventivo de la interfaz de usuario durante el procesamiento de cobro, impidiendo clics compulsivos y garantizando cero cobros duplicados (**AS-4**, **FR-004**, **INV-003**, **SC-004**).
   - Despacho universal, asíncrono e incondicional del correo electrónico con el comprobante formal, detalle de ítems y enlace seguro de seguimiento ante todo pago aprobado, garantizando la entrega incluso si el navegador sufre una interrupción brusca (**AS-2**, **FR-002**, **INV-002**, **Q4/A4**, **CB-002**).
2. **`COM-7.3.2` — Tiempo estimado en vivo (P1)**:
   - Seguimiento para retiro inmediato (`IMMEDIATE`): barra de progreso y tiempo remanente calculado dinámicamente según la saturación real de comandas activas en el KDS y modificadores de cocina (**AS-5**, **FR-005**, **INV-005**, **CB-004**).
   - Seguimiento para reserva programada (`SCHEDULED`): horario pactado estático mientras falten más de 15 minutos; conmutación automática y reactiva a cuenta regresiva dinámica en tiempo real al ingresar en el umbral de los 15 minutos previos al turno (**AS-6**, **FR-006**, **INV-006**, **Q1/A1**, **CB-005**, **CB-006**).
   - Actualización silenciosa del reloj dinámico ante demoras o ajustes globales del KDS, sin popups ni alertas disruptivas (**AS-7**, **FR-007**, **INV-007**, **Q6/A6**, **CB-007**).
   - Transición definitiva e irreversible a estado "listo para retirar" por mostrador al marcarse la orden como finalizada en el KDS, con supresión del tiempo y número de comanda en gran formato (**AS-8**, **FR-008**, **INV-008**, **CB-008**).
   - Convivencia unificada y persistente de confirmación y seguimiento ante recarga o reingreso desde el correo electrónico (**Q2/A2**, **CB-012**).
3. **`ADM-2.1.1` — Configurar branding del restaurante (P2)**:
   - Módulo administrativo "Mi Local" (`/admin/mi-local`): carga de logotipo, edición de nombre de fantasía y descripción comercial, y selección de la tríada de colores de marca (`primaryColor`, `secondaryColor`, `accentColor`) en formato hexadecimal `#RRGGBB` (**AS-9**, **ADM-2.1.1**, **Q7/A7**, **CB-009**).
   - Cálculo automático de contraste accesible (WCAG 2.1) para textos e iconos, garantizando legibilidad sin sobrecarga para el administrador (**Q5/A5**).
   - Adopción dinámica de la identidad en la vista del comensal (catálogo, cabeceras, botones y badges) con aislamiento estricto multi-tenant (**FR-009**, **INV-009**, **CB-010**).
   - Fallback institucional neutro de EasyEat ante ausencia de configuración previa (**INV-010**, **Q3/A3**, **CB-011**).

---

## 2. Technical Context

**Lenguaje(s) y versión(es)**:
- Backend: Java 21 (LTS).
- Frontend: TypeScript 5.6.3 / React 18.3.1.

**Framework(s) y dependencias principales**:
- Backend: Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-mail`), Flyway Core (`9.x/10.x`), PostgreSQL Driver, Jackson (`jackson-databind`), JJWT (`0.12.6`), Caffeine Cache, Testcontainers (`1.21.4`).
- Frontend: React 18.3.1, Vite 5.4.8, Tailwind CSS 3.4.13, React Router v6.27.0, Axios 1.7.7, Lucide React 0.453.0.

**Persistencia y esquema de base de datos**:
- PostgreSQL 16 relacional.
- Migraciones gestionadas por Flyway en `easyeat/backend/src/main/resources/db/migration/`.
- Nueva migración secuencial: **`V20__payment_confirmation_tracking_and_branding.sql`**:
  - Amplía el constraint `chk_order_status` de la tabla `orders` para incorporar el estado `READY_FOR_PICKUP` y `COMPLETED`.
  - Crea la tabla `restaurant_brandings` con clave primaria foránea `establishment_id` (1:1), validaciones de formato `#RRGGBB` y persistencia de contrastes calculados (`#000000` / `#FFFFFF`).
  - Crea la tabla `establishment_kds_settings` para demoras operativas globales en cocina.
  - Crea la tabla `order_receipts` con restricción de unicidad `UNIQUE(order_id)` y clave de idempotencia para garantizar despacho único e incondicional de correo.

**Estrategia de HTTP / Contratos de API y validación**:
- Arquitectura REST sobre `/api/v1/orders/`, `/api/v1/kitchen/` y `/api/v1/admin/establishments/`.
- Validación declarativa mediante Jakarta Bean Validation (`jakarta.validation.Valid`, `@Pattern(regexp = "^#[0-9A-Fa-f]{6}$")`) en records inmutables de Java.
- Manejo estandarizado de excepciones según RFC 7807 (`ProblemDetail` / `application/problem+json`).
- Contratos modulares documentados en `contracts/` y centralizados en especificación OpenAPI 3.0 (`contracts/openapi.yaml`).

**Estrategia de autenticación y sesión**:
- Autenticación JWT Bearer (`jjwt`), validando rol `ROLE_COMENSAL` para confirmación y seguimiento de la orden (con verificación de titularidad `order.user_id == current_user.id`), `ROLE_CHEF` o `ROLE_ENCARGADO` para operaciones de KDS y demoras, y `ROLE_ADMINISTRADOR` para la configuración de marca en "Mi Local" (con aislamiento de tenant `current_user.tenant_id == establishmentId`).
- La lectura de branding por el comensal en catálogo es pública o autenticada.

**Mecanismo de correo electrónico transaccional y plantillas**:
- Despacho asíncrono (`@Async`) gestionado por `OrderReceiptEmailService`, apoyado en `JavaMailSender` y `spring-boot-starter-mail` (preconfigurado en el repo en `${app.mail.from}`).
- Plantilla HTML y texto plano con detalle de ítems, montos, franja horaria, datos del local y enlace de seguimiento directo.
- Registro en `order_receipts` para auditar entregas y garantizar idempotencia ante reintentos de webhooks (**INV-002**).

**Mecanismo del KDS y dependencias upstream**:
- Esta especificación asume la integración y consumo de los modelos de comanda en cocina (`KdsTicket` y tabla `kds_tickets`) introducidos en la Spec 015, así como el flujo de pago con Mercado Pago de la Spec 014. La migración `V20` se apoya sobre el esquema resultante de `V18` y `V19` al ejecutarse en el pipeline del proyecto.
- Entidad `KdsTicket` mapeada a la tabla `kds_tickets` (1:1 con `orders`).
- Estados de comanda: `QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP`, `COMPLETED`.
- Endpoint de finalización: `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`, que actualiza ticket y orden a `READY_FOR_PICKUP`.
- Ajustes globales de cocina: `PUT /api/v1/kitchen/delay` persistido en `establishment_kds_settings`.

**Mecanismo de actualización de estado en el frontend del comensal**:
- Short HTTP Polling inteligente (`useOrderLiveTrackingPolling`) con intervalo dinámico (5s durante elaboración, 3s al acercarse al turno o en fase final, y detención inmediata en `READY_FOR_PICKUP`).
- Integración obligatoria con Page Visibility API (`document.visibilityState`) para suspender peticiones en pestañas inactivas y `AbortController` contra saturación de red.
- Cero dependencias adicionales (reutiliza el patrón establecido en `useTimeSlotPolling.ts`).

**Manejo de configuración, secretos y entornos**:
- Configuración de correo: `spring.mail.*` y `app.mail.from`.
- Perfiles Spring: `application-dev.yml`, `application-test.yml`, `application-local.yml`.

**Estrategia de testing**:
- Backend: JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers PostgreSQL para integración y pruebas de idempotencia con mock de `JavaMailSender`.
- Frontend: Vitest 2.1.3, React Testing Library 16.0.1, jsdom 25.0.1 probando comportamiento observable (bloqueo de botón, transición a "listo para retirar", actualización silenciosa del reloj y renderizado de branding).

**Observabilidad y auditoría**:
- Logging estructurado con SLF4J / Logback registrando `orderId`, `ticketNumber`, `establishmentId`, `receiptId` y `delayMinutes`.
- Prohibición estricta de registrar datos bancarios o información sensible.

**Restricciones de arquitectura y seguridad**:
- **Aprobación Autoritativa (INV-001)**: Pantalla verde exclusivamente ante confirmación real de pasarela.
- **Universalidad del Correo (INV-002, Q4/A4)**: Despacho incondicional ante todo pago aprobado.
- **Idempotencia (INV-002, INV-003)**: Cero correos duplicados y cero cobros dobles por clics repetidos.
- **Umbral T-15 (INV-006, Q1/A1)**: Horario estático hasta 15 min antes; cuenta regresiva activa dentro de los 15 min.
- **Actualización Silenciosa (INV-007, Q6/A6)**: Sincronización del minutero sin modales ni alertas.
- **Irreversibilidad (INV-008)**: Supresión de tiempo y estado destacado permanente al estar listo.
- **Aislamiento Multi-Tenant (INV-009)**: Cada branding circunscrito a su restaurante.
- **Fallback Neutro (INV-010, Q3/A3)**: Identidad base de EasyEat ante ausencia de branding.
- **Accesibilidad WCAG 2.1 (Q5/A5)**: Contraste automático para legibilidad garantizada.
- **Aislamiento Financiero (PCI-DSS SAQ A)**: Cero almacenamiento o procesamiento de PAN/CVV en EasyEat.

---

## 3. Constitution Check

*GATE: Evaluación exhaustiva frente a la Constitución SDD del proyecto (v2.0.0).*

| Principio | Estado | Evidencia y Justificación Técnica |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | El plan responde con precisión matemática a los 9 requerimientos funcionales (FR-001 a FR-009), las 10 invariantes (INV-001 a INV-010), los 12 casos borde (CB-001 a CB-012) y los 9 criterios de éxito (SC-001 a SC-009) de `spec.md`. No se reabren clarificaciones ya consensuadas (Q1 a Q7). |
| **II. Specification Before Implementation** | **PASS** | La especificación funcional está aprobada. Este plan técnico y sus artefactos de diseño (`research.md`, `data-model.md`, `contracts/`, `quickstart.md`) se completan íntegramente antes de descomponer tareas o escribir código de implementación. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Estrategia de pruebas por capas: (1) Unit tests de cálculo exacto de luminancia WCAG 2.1 y umbral T-15; (2) Tests de integración con Testcontainers verificando persistencia de branding y actualización de KDS; (3) Pruebas de idempotencia de correo electrónico con mock de `JavaMailSender` evitando duplicados; (4) Pruebas de componentes frontend con React Testing Library verificando comportamiento observable de botones, pantallas y reloj silencioso; (5) Guía de verificación determinística documentada en `quickstart.md`. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Se aplica KISS: reutilización de `JavaMailSender` y `@Async` sin brokers externos pesados; short polling inteligente en frontend reutilizando el patrón de `useTimeSlotPolling.ts`; tabla separada `restaurant_brandings` para alta cohesión; cálculo de contraste automático puramente algorítmico sin bibliotecas externas. |
| **V. Bounded Scope** | **PASS** | Alcance estrictamente delimitado a las tres historias fuente: no se agregan otros medios de pago, no se tocan reembolsos, no se envían avisos por WhatsApp ni notificaciones push móviles, y no se introduce personalización avanzada de layouts o fuentes tipográficas. |
| **VI. Controlled Technical Debt** | **PASS** | Se introduce la migración secuencial aditiva `V20__payment_confirmation_tracking_and_branding.sql` sin romper el esquema existente ni realizar modificaciones destructivas sobre `orders` ni `establishments`. |
| **VII. Traceability, Consistency, and Documentation** | **PASS** | Trazabilidad completa y bidireccional entre `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md`. Todo el diseño está redactado en español profesional con identificadores técnicos en inglés. |
| **VIII. Evidence Over Assumption & Escalation** | **PASS** | Todas las decisiones técnicas se basan estrictamente en la evidencia del código existente del repositorio (entidades, servicios, hooks de polling, vistas React) y en las resoluciones explícitas de `spec.md`. Cero suposiciones no respaldadas. |
| **IX. Engineering Over Convenience** | **PASS** | Despacho asíncrono e incondicional con idempotencia a nivel de base de datos; bloqueo preventivo en frontend y en backend (`X-Idempotency-Key`); fórmula matemática estricta de luminancia relativa WCAG 2.1 para accesibilidad real. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Vertical slice completo y funcional: migración Flyway `V20`, entidades JPA y repositorios, servicios de dominio transaccionales, controladores REST, actualización de `CheckoutPage.tsx`, nueva vista unificada `OrderConfirmationTrackingPage.tsx`, vista administrativa `AdminBrandingPage.tsx`, y hooks de seguimiento en vivo. |

---

## 4. Project Structure

### Documentation (this feature)

```text
specs/016-payment-confirmation-tracking-branding/
├── spec.md                                 # Especificación funcional aprobada
├── checklists/
│   └── payment-confirmation-tracking-branding-quality.md # Checklist de calidad funcional
├── plan.md                                 # Este plan de implementación técnica
├── research.md                             # Fase 0: Decisiones arquitectónicas y de sincronización
├── data-model.md                           # Fase 1: Esquema relacional, entidades JPA y DDL V20
├── contracts/                              # Fase 1: Contratos modulares y OpenAPI 3.0
│   ├── openapi.yaml                        # Especificación OpenAPI 3.0 unificada
│   ├── 01-payment-confirmation.md          # Contrato de pantalla verde y orden (AS-1, AS-3)
│   ├── 02-email-receipt-dispatch.md        # Contrato de comprobante por correo electrónico (AS-2)
│   ├── 03-ui-interaction-lock.md           # Contrato de bloqueo de interfaz ante clics (AS-4)
│   ├── 04-live-tracking-immediate.md       # Contrato de seguimiento inmediato y saturación (AS-5, AS-7)
│   ├── 05-live-tracking-scheduled.md       # Contrato de reserva programada y umbral T-15 (AS-6)
│   ├── 06-ready-for-pickup-transition.md   # Contrato de finalización KDS y retiro listo (AS-8)
│   ├── 07-branding-admin.md                # Contrato de configuración de branding en Mi Local (AS-9)
│   └── 08-branding-diner.md                # Contrato de adopción de branding y fallback neutro (AS-9)
└── quickstart.md                           # Fase 1: Guía de ejecución determinística de extremo a extremo
```

### Source Code Layout (Repository Root)

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/easyeat/
│   │   │   │   ├── catalog/
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── AdminBrandingController.java         # GET/PUT /api/v1/admin/establishments/{id}/branding
│   │   │   │   │   │   └── DinerBrandingController.java         # GET /api/v1/establishments/{id}/branding
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   └── RestaurantBranding.java              # Entidad JPA para branding multi-tenant
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── RestaurantBrandingDto.java           # DTO de lectura con contrastes calculados
│   │   │   │   │   │   └── UpdateBrandingRequest.java           # DTO de guardado con validación #RRGGBB
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── RestaurantBrandingRepository.java    # Repositorio JPA de branding
│   │   │   │   │   └── service/
│   │   │   │   │       ├── RestaurantBrandingService.java       # Lógica de guardado, fallback y contraste
│   │   │   │   │       ├── BrandingColorContrastCalculator.java # Algoritmo WCAG 2.1 de luminancia relativa
│   │   │   │   │       └── BrandingLogoStorageService.java      # Carga y almacenamiento seguro de logos
│   │   │   │   └── order/
│   │   │   │       ├── controller/
│   │   │   │       │   ├── OrderConfirmationTrackingController.java # Endpoints unificados comensal
│   │   │   │       │   └── KitchenTicketDispatchController.java    # Finalización de comanda y demoras KDS
│   │   │   │       ├── domain/
│   │   │   │       │   ├── Order.java                           # Extendida con READY_FOR_PICKUP
│   │   │   │       │   ├── OrderReceipt.java                    # Entidad de comprobante para correo
│   │   │   │       │   ├── OrderStatus.java                     # READY_FOR_PICKUP incorporado
│   │   │   │       │   └── EstablishmentKdsSettings.java        # Ajustes de demoras globales en cocina
│   │   │   │       ├── dto/
│   │   │   │       │   ├── OrderConfirmationTrackingResponse.java # DTO unificado confirmación + widget
│   │   │   │       │   └── OrderLiveTrackingResponse.java       # DTO dinámico de polling
│   │   │   │       ├── event/
│   │   │   │       │   └── OrderPaymentApprovedEvent.java       # Evento disparador de comprobante
│   │   │   │       ├── repository/
│   │   │   │       │   ├── OrderReceiptRepository.java          # Repositorio JPA de comprobantes
│   │   │   │       │   └── EstablishmentKdsSettingsRepository.java # Repositorio de demoras de KDS
│   │   │   │       └── service/
│   │   │   │           ├── OrderConfirmationTrackingService.java# Ensamblador de estado de confirmación
│   │   │   │           ├── OrderLiveTrackingService.java        # Motor dinámico de tiempos y umbral T-15
│   │   │   │           └── OrderReceiptEmailService.java        # Emisión asíncrona garantizada de email
│   │   │   └── resources/
│   │   │       └── db/migration/
│   │   │           └── V20__payment_confirmation_tracking_and_branding.sql # Migración Flyway DDL
│   │   └── test/
│   │       └── java/com/easyeat/
│   │           ├── catalog/
│   │           │   ├── service/BrandingColorContrastCalculatorTest.java
│   │           │   └── controller/AdminBrandingControllerIntegrationTest.java
│   │           └── order/
│   │               ├── service/OrderReceiptEmailServiceTest.java
│   │               ├── service/OrderLiveTrackingServiceTest.java
│   │               └── controller/OrderConfirmationTrackingControllerTest.java
│   └── pom.xml
└── frontend/
    ├── src/
    │   ├── api/
    │   │   ├── brandingApi.ts                                   # Llamadas cliente a endpoints de branding
    │   │   └── orderTrackingApi.ts                              # Llamadas a confirmación y seguimiento
    │   ├── components/
    │   │   ├── admin/
    │   │   │   ├── ColorPickerInput.tsx                         # Input selector con cálculo en vivo de contraste
    │   │   │   └── LogoUploader.tsx                             # Componente de subida de logotipo
    │   │   ├── tracking/
    │   │   │   ├── OrderSuccessGreenBanner.tsx                  # Cabecera verde con animación y número de orden
    │   │   │   ├── LiveTrackingWidget.tsx                       # Widget reactivo de seguimiento
    │   │   │   ├── ScheduledSlotCountdown.tsx                   # Horario estático o cuenta regresiva a 15 min
    │   │   │   └── ReadyForPickupCallout.tsx                    # Banner destacado verde con número gigante
    │   │   └── common/
    │   │       └── BrandThemeProvider.tsx                       # Inyector de variables CSS de branding
    │   ├── hooks/
    │   │   ├── useOrderLiveTrackingPolling.ts                   # Hook de short polling con Visibility API
    │   │   └── useRestaurantBranding.ts                         # Hook para resolución de marca y fallback
    │   ├── pages/
    │   │   ├── CheckoutPage.tsx                                 # Actualizada con bloqueo de UI ante clics
    │   │   ├── OrderConfirmationTrackingPage.tsx                # Nueva vista unificada confirmación + tracking
    │   │   └── AdminBrandingPage.tsx                            # Nueva página de "Mi Local"
    │   ├── router/
    │   │   └── AppRoutes.tsx                                    # Registro de rutas de confirmación y Mi Local
    │   ├── types/
    │   │   ├── branding.ts                                      # Interfaces de branding y contrastes
    │   │   └── orderTracking.ts                                 # Interfaces de confirmación y seguimiento
    │   └── utils/
    │       └── colorContrastUtils.ts                            # Cálculo en TypeScript de luminancia WCAG
    └── src/test/
        ├── admin/AdminBrandingPage.test.tsx
        ├── order/OrderConfirmationTracking.test.tsx
        └── tracking/LiveTrackingWidget.test.tsx
```

---

## 5. Complexity Tracking

> **Evaluación frente a la Constitución**: CERO violaciones injustificadas.

| Principio Constitucional | Estado | Justificación de Simplicidad |
|---|---|---|
| Principio IV (KISS) | **PASS** | No se introducen WebSockets, brokers AMQP ni bibliotecas complejas de streaming. Se reutiliza short polling y Spring Mail con `@Async`. |
| Principio V (Alcance Delimitado) | **PASS** | Se circunscribe estrictamente a las tres historias fuente sin inventar pasarelas, canales SMS ni temas gráficos avanzados. |
| Principio X (Integridad Vertical) | **PASS** | Se cubre de punta a punta: Flyway DDL, backend Java, frontend React, contratos OpenAPI y tests automatizados. |

---

## 6. Phase 0 — Research Summary

Resuelto íntegramente en [research.md](./research.md).
- **D-01**: Despacho universal, asíncrono (`@Async`) e idempotente de comprobante formal por correo electrónico con tabla `order_receipts`.
- **D-02**: Short HTTP Polling inteligente (5s/3s) gestionado por `useOrderLiveTrackingPolling` con Page Visibility API y detención en estado terminal.
- **D-03**: Cálculo del tiempo dinámico para retiro inmediato: función de saturación real del KDS y demoras manuales de cocina (`estimatedMinutes`).
- **D-04**: Regla de reservas programadas: horario estático mientras falten más de 15 min; cuenta regresiva automática al ingresar en T-15 min.
- **D-05**: Transición definitiva a `READY_FOR_PICKUP`: supresión de tiempo, destaque visual y número gigante.
- **D-06**: Convivencia unificada y persistente de confirmación y seguimiento en `/checkout/confirmacion/:orderId` ante recargas o reingreso.
- **D-07**: Modelo de branding multi-tenant con tabla `restaurant_brandings` aislada por `establishment_id`.
- **D-08**: Validación `#RRGGBB` y cálculo automático de contraste WCAG 2.1 para textos claros/oscuros.
- **D-09**: Fallback institucional neutro de EasyEat ante ausencia de personalización.
- **D-10**: Almacenamiento ligero de logotipo con validación de tipo y tamaño.
- **D-11**: Bloqueo de UI (`disabled`, spinner animado) y `X-Idempotency-Key` en servidor para evitar cobros dobles.
- **D-12**: Estrategia de pruebas automatizadas por capas y cobertura de todos los escenarios de aceptación.

---

## 7. Phase 1 — Design Artifacts Summary

1. **Modelo de Datos ([data-model.md](./data-model.md))**:
   - DDL Flyway `V20__payment_confirmation_tracking_and_branding.sql`.
   - Entidades JPA: `Order` (extendida), `OrderReceipt`, `RestaurantBranding`, `EstablishmentKdsSettings`.
   - Vista derivada en lectura: `OrderLiveTrackingResponse`.
   - Política de datos no almacenados (PCI-DSS SAQ A).
2. **Contratos de Interfaz ([contracts/](./contracts/))**:
   - `01-payment-confirmation.md`: Pantalla verde, número de orden y detalle de franja (**AS-1**, **AS-3**).
   - `02-email-receipt-dispatch.md`: Evento disparador, payload y semántica de reintentos de comprobante (**AS-2**).
   - `03-ui-interaction-lock.md`: Máquina de estados de UI y prevención de doble cobro (**AS-4**).
   - `04-live-tracking-immediate.md`: Seguimiento dinámico de retiro inmediato y actualización silenciosa (**AS-5**, **AS-7**).
   - `05-live-tracking-scheduled.md`: Horario fijo y cuenta regresiva a T-15 min en reservas (**AS-6**).
   - `06-ready-for-pickup-transition.md`: Finalización en KDS y llamado a mostrador (**AS-8**).
   - `07-branding-admin.md`: Endpoints de administración de branding en "Mi Local" (**AS-9**).
   - `08-branding-diner.md`: Adopción de branding en comensal y fallback neutro (**AS-9**).
   - `openapi.yaml`: Especificación formal OpenAPI 3.0 centralizada.
3. **Guía de Ejecución y Validación ([quickstart.md](./quickstart.md))**:
   - 10 escenarios paso a paso para reproducir interrupciones de navegador, ajustes de cocina, cuenta regresiva a 15 min, finalización y configuración de marca.

---

## 8. Re-evaluación de Constitution Check Post-Diseño

Tras culminar el diseño técnico, la re-evaluación confirma que todos los principios fundamentales se mantienen en estado **PASS**. La solución respeta el stack existente (Java 21, Spring Boot 3.3.4, PostgreSQL 16, React 18, Tailwind CSS), no introduce dependencias superfluas, garantiza cobertura de pruebas en todos los niveles y vertical slice completo de extremo a extremo, quedando lista para la descomposición de tareas en `/speckit-tasks`.
