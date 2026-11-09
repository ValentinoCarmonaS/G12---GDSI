# Phase 0: Research & Architectural Decisions — Mercado Pago Checkout

**Feature Branch**: `014-mercadopago-checkout`  
**Fecha**: 09/11/2026  
**Especificación**: [spec.md](./spec.md)  
**Estado**: Completado  

---

## 1. Resumen de Decisiones Arquitectónicas

| Área Técnica | Decisión Seleccionada | Alternativas Descartadas | Rationale Principal |
|---|---|---|---|
| **Modo de Integración de Pasarela** | **Mercado Pago Checkout Pro (Preference API)** | Checkout Bricks, API de Pagos Directos (v1/payments con token), API de Órdenes | Máxima seguridad (aislamiento de datos de tarjeta FR-013 / INV-11), cumplimiento nativo de PCI-DSS SAQ A, soporte nativo de Sandbox (`sandbox_init_point`) y todos los medios de pago argentinos. |
| **Modelo de Reserva Temporal Conjunta** | **Entidad explícita `TemporaryReservation` vinculada a `Order`** con cómputo de stock comprometido en `DishAvailabilityEngine` y cupo en `orders` | Embeber únicamente flags en `Order`, tabla separada de reserva física de ingredientes con deducción prematura | Permite atomicidad e indivisibilidad (INV-01), auditoría de motivos de liberación (rechazo, cancelación voluntaria, expiración), soporte de reintentos sin corromper la orden y unicidad estricta (INV-05) mediante restricción de unicidad (`UNIQUE(order_id)`). |
| **TTL de Vigencia y Expiración** | **Ventana de 15 minutos con mecanismo híbrido:** Lazy evaluation en consulta + `@Scheduled` cada 60 s | Solo cron/scheduler, solo evaluación perezosa en lectura | Alineación estricta con Spec 012 (A1, A6). Cero retención indefinida (INV-04, SC-004): la expiración se aplica de inmediato si el comensal o el webhook consultan, y el barredor limpia sesiones abandonadas sin retorno. |
| **Idempotencia End-to-End** | **Doble capa de idempotencia:** `X-Idempotency-Key` en llamadas salientes hacia Mercado Pago y `idempotency_key` con restricción `UNIQUE` en base de datos para procesamiento de webhooks y pagos | Reintentos sin cabecera, verificación optimista sin bloqueo | Cumple con INV-06 y CB-07: garantiza que notificaciones repetidas, fallas de red o reingresos del comensal no alteren dos veces el inventario ni el estado de la reserva. |
| **Verificación de Origen de Webhooks** | **HMAC-SHA256 sobre header `x-signature` (`ts`, `v1`) con validación de manifest + reconciliación contra API oficial** | Confianza ciega en payload, verificación solo por IP | Garantiza la autoridad externa (INV-08): ninguna petición manipulada puede confirmar una orden. Se compara el hash en tiempo constante y se revalida el estado llamando a `GET /v1/payments/{id}` con Access Token seguro. |
| **Estrategia Sandbox y Reproducibilidad** | **Credenciales oficiales de Sandbox de Mercado Pago, `sandbox_init_point`, tarjetas de prueba y simulación controlada de fallos de red** | Transacciones con dinero real en pruebas, simulación manual no reproducible | Satisface RT-1 (costo cero), RT-2 (equivalencia funcional 1:1) y RT-3 (determinismo reproducible en aprobación `APRO`, fondos `FUND`, fraude `OTHE` y fallos de enlace con WireMock/MockRestServiceServer). |
| **Actualización de Estado en Frontend** | **Estrategia reactiva por redirección al retorno (`/checkout/payment-return`) complementada con polling liviano en Checkout con pago en curso** | WebSockets persistentes, Server-Sent Events (SSE) | Coherencia con el stack actual del frontend (React + Axios, sin infraestructura de sockets innecesaria); permite que el comensal retome o cancele con un solo clic. |
| **Manejo de Secretos y Configuración** | **Spring Boot `@ConfigurationProperties` tipado (`MercadoPagoProperties`) alimentado por variables de entorno** | Hardcoding en YAML, inyección manual `@Value` dispersa | Segregación limpia entre ambientes (`application-dev.yml`, `application-test.yml`, `application-local.yml`), desacople de credenciales sensibles y aislamiento total de producción. |

---

## 2. Investigación Detallada por Área Técnica

### 2.1. Modo de Integración con Mercado Pago: Checkout Pro

#### Decisión
Adoptar la integración mediante **Mercado Pago Checkout Pro** utilizando la **API de Preferencias** (`POST https://api.mercadopago.com/checkout/preferences`).  
Documentación oficial consultada:
- Mercado Pago Developers Argentina - Integrar Checkout Pro:  
  `https://www.mercadopago.com.ar/developers/es/docs/checkout-pro/integrate-checkout-pro/web`
- Mercado Pago Developers - Crear preferencia de pago:  
  `https://www.mercadopago.com.ar/developers/es/reference/preferences/_checkout_preferences/post`

#### Rationale
1. **Aislamiento absoluto de datos financieros (FR-013, INV-11, SC-009)**: Al utilizar Checkout Pro, el comensal es redirigido a la pasarela alojada por Mercado Pago (`init_point` en producción o `sandbox_init_point` en sandbox). Los formularios de tarjetas residen íntegramente en los servidores certificados de Mercado Pago. La plataforma EasyEat jamás captura, toca, transfiere ni persiste un número de tarjeta (PAN), fecha de expiración o código de seguridad (CVV).
2. **Cumplimiento de PCI-DSS**: EasyEat queda encuadrado en la categoría más simple y segura (PCI-DSS SAQ A), reduciendo la superficie de ataque y el costo de auditoría a cero.
3. **Soporte nativo de Sandbox y tarjetas de prueba (RT-1, RT-3)**: Mercado Pago provee automáticamente la propiedad `sandbox_init_point` en la respuesta de creación de la preferencia cuando se utiliza un `ACCESS_TOKEN` de pruebas.
4. **Respeto a las historias previas**: Unifica y absorbe formalmente las historias COM-7.1.1 (métodos guardados en la cuenta MP del usuario), COM-7.1.2 (carga de nueva tarjeta en la billetera MP) y COM-7.1.3 (recordar medio de pago), ya que Mercado Pago resuelve estas tres características nativamente dentro de su pantalla.

#### Alternativas descartadas
- **Checkout Bricks**: Requiere embeber componentes web / iframes en el frontend de EasyEat. Aunque reduce la fricción de redirección, introduce complejidad de renderizado en React, dependencias pesadas de scripts de terceros en el bundle del cliente y riesgo de fallas de renderizado en componentes controlados.
- **API de Pagos Directos (`POST /v1/payments`) con tarjeta**: Requiere tokenizar la tarjeta en el frontend mediante el SDK JavaScript de Mercado Pago o recibir datos en backend. Viola el principio de diseño de la feature y arriesga incumplir INV-11.
- **API de Órdenes de Mercado Pago**: Enfocada en terminales de cobro Point y pagos presenciales vía código QR en mostrador físico, no en checkout de e-commerce web.

---

### 2.2. Modelo de Reserva Temporal Conjunta e Indivisible (INV-01, INV-05, INV-07)

#### Decisión
Crear una entidad explícita `TemporaryReservation` (`temporary_reservations`) asociada a la entidad `Order`, complementada con una entidad `Payment` (`payments`).  
Para garantizar que la reserva de **franja horaria** y la reserva de **stock de cocina** sean indivisibles y atómicas:
1. **Franja horaria**: Se reutiliza el mecanismo de serialización pesimista probado en Spec 012 (`EstablishmentRepository.findByIdForUpdate`), que bloquea la fila del restaurante durante la evaluación de cupo.
2. **Stock de cocina**: Se extiende `DishAvailabilityEngine` para que, al evaluar si un plato tiene stock suficiente para el pedido, reste la cantidad comprometida por las órdenes que poseen una `TemporaryReservation` en estado `ACTIVE` y no expirada (`expires_at > NOW()`).
3. **Atomicidad e indivisibilidad (INV-01)**: Ambas validaciones (cupo de franja y stock remanente no reservado) se ejecutan dentro de la misma transacción de base de datos (`@Transactional`). Si la franja está llena O si cualquiera de los platos no tiene stock remanente, se lanza una excepción de negocio (`SlotCapacityExceededException` o `InsufficientStockException`), realizando un rollback inmediato que no reserva ningún recurso.

#### Rationale de la entidad `TemporaryReservation` frente a reutilizar solo `Order`
En Spec 012, la reserva de franja se representaba implícitamente por el estado `Order.status = PENDING_PAYMENT` y `Order.expiresAt`.  
Sin embargo, para la Spec 014:
- El comensal puede realizar reintentos (tras un rechazo o abandono voluntario) sobre el mismo pedido o crear un nuevo intento de pago.
- Es imperativo auditar y distinguir si una reserva fue liberada por: rechazo de pasarela (`REJECTED`), abandono voluntario en checkout (`CANCELLED_BY_USER`), fallo en la comunicación (`GATEWAY_ERROR`) o expiración automática de los 15 minutos (`EXPIRED`).
- Se debe asegurar el invariante INV-05: **máximo una reserva temporal activa y un pago en curso por orden**. Esto se modela a nivel de motor de datos con una restricción condicional o única:
  ```sql
  CREATE UNIQUE INDEX idx_unique_active_reservation_per_order 
  ON temporary_reservations (order_id) 
  WHERE status = 'ACTIVE';
  ```
- Al aislar la reserva temporal en su propia entidad, la orden de compra representa el compromiso de adquisición del cliente, mientras que la reserva representa el bloqueo operativo de recursos sujeto a TTL.

---

### 2.3. Ventana Temporal de 15 Minutos y Expiración (INV-04, FR-009, CB-04)

#### Decisión
Definir un TTL estricto de exactamente 15 minutos (`expires_at = created_at + 15 minutos`), implementado mediante una arquitectura híbrida de doble salvaguarda:
1. **Evaluación reactiva (Lazy Evaluation)**: Cada vez que se consulta el estado de la orden o de la reserva (`GET /api/v1/orders/{id}/payment-status` o en el flujo de retorno), el servicio compara `Instant.now()` con `expiresAt`. Si `now > expiresAt` y el estado sigue siendo `ACTIVE`, ejecuta inmediatamente la transición a `EXPIRED`, marcando la orden como `EXPIRED` y liberando la disponibilidad de la franja y el stock sin depender del scheduler.
2. **Proceso programado en background (`@Scheduled`)**: Se incorpora un método barredor periódico en Spring Boot (`ReservationExpirationScheduler.java`) que se ejecuta cada 60 segundos:
   ```java
   @Scheduled(fixedRate = 60000)
   @Transactional
   public void sweepExpiredReservations() {
       Instant now = clock.instant();
       List<TemporaryReservation> expired = reservationRepository.findExpiredActive(now);
       for (TemporaryReservation res : expired) {
           res.setStatus(ReservationStatus.EXPIRED);
           res.setReleaseReason(ReleaseReason.EXPIRED);
           res.setReleasedAt(now);
           res.getOrder().setStatus(OrderStatus.EXPIRED);
       }
   }
   ```
3. **Liberación instantánea por acción del usuario (Q3, FR-010)**: Si el comensal hace clic en "Cancelar intento" en el panel informativo de pago en curso, se invoca `POST /api/v1/checkout/cancel-payment`, que libera inmediatamente la reserva (`RELEASED` por `CANCELLED_BY_USER`) en `< 100 ms`, re-habilitando el checkout.

#### Rationale
Esta combinación garantiza el criterio **SC-004** (cero reservas retenidas tras el vencimiento de su ventana) tanto para usuarios activos que interactúan con el checkout como para usuarios que cierran el navegador o pierden conexión (CB-04).

---

### 2.4. Idempotencia End-to-End (INV-06, FR-012, CB-07)

#### Decisión
Implementar un esquema de idempotencia en dos niveles:
1. **Llamadas salientes hacia Mercado Pago**:  
   Al invocar `POST /checkout/preferences`, el backend genera y envía el header `X-Idempotency-Key` conteniendo un UUID v4 generado determinísticamente a partir del ID de la reserva temporal (`reservation.getId()`).  
   Documentación oficial de Mercado Pago:  
   `https://www.mercadopago.com.ar/developers/es/docs/your-integrations/advanced-features/idempotency`
2. **Procesamiento entrante de Webhooks y Retornos**:  
   - En la tabla `payments`, el campo `gateway_payment_id` (o la combinación `external_reference` + `gateway_payment_id`) posee una restricción de unicidad (`UNIQUE`).
   - El servicio de procesamiento de pagos valida el estado actual de la entidad antes de ejecutar transiciones:
     ```java
     if (payment.getStatus() == PaymentStatus.APPROVED) {
         // Ya fue procesado exitosamente por una notificación previa: idempotencia total (CB-07)
         return;
     }
     ```
   - Si se reciben múltiples notificaciones de webhook de Mercado Pago para el mismo pago, el primer evento procesa la confirmación de la orden y consolida la reserva en definitiva; las llamadas sucesivas reconocen el estado final y retornan inmediatamente `HTTP 200 OK` sin duplicar deducciones de cupo ni alterar el inventario.

---

### 2.5. Verificación de Origen y Firma Criptográfica de Webhooks (INV-08)

#### Decisión
Validar obligatoriamente la firma criptográfica enviada en el header **`x-signature`** de todas las notificaciones recibidas en el endpoint `/api/v1/payments/mercadopago/webhook`, complementada con reconciliación REST directa.  
Documentación oficial consultada:
- Mercado Pago Developers - Notificaciones Webhooks:  
  `https://www.mercadopago.com.ar/developers/es/docs/your-integrations/notifications/webhooks`

#### Algoritmo de validación de firma
1. Mercado Pago envía la cabecera:
   ```text
   x-signature: ts=1742505638683,v1=ced36ab6d33566bb1e16c125819b8d840d6b8ef136b0b9127c76064466f5229b
   x-request-id: 79c3b8a1-43e9-4e76-8f37-123456789abc
   ```
2. El backend extrae `ts` y el hash `v1` de `x-signature`, el header `x-request-id`, y el identificador `data.id` del payload JSON (o del query param de la notificación).
3. Se ensambla el manifest canónico:
   ```text
   manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
   ```
4. Se calcula el HMAC con algoritmo `HmacSHA256` utilizando la clave secreta de la integración (`mercadopago.webhook-secret`):
   $$\text{computedHash} = \text{Hex}(\text{HMAC-SHA256}(\text{webhookSecret}, \text{manifest}))$$
5. Se compara `computedHash` con `v1` mediante `MessageDigest.isEqual` (tiempo constante para prevenir ataques de timing). Si no coinciden o si el timestamp difiere por más de 5 minutos (prevención de ataques de repetición), se rechaza con `HTTP 401 Unauthorized`.
6. **Reconciliación de autoridad externa (INV-08)**:  
   Una vez validada la firma, el backend no asume ciegamente el cuerpo del webhook. En su lugar, utiliza su `ACCESS_TOKEN` para realizar una petición autenticada a `GET https://api.mercadopago.com/v1/payments/{data.id}`. Con ello, obtiene directamente de la API de Mercado Pago el estado real (`status`: `approved`, `rejected`, `cancelled`) y el `external_reference` asociado a la orden, asegurando que ninguna entidad no autorizada pueda falsear una aprobación.

---

### 2.6. Estrategia de Sandbox y Reproducibilidad Determinística (RT-1, RT-2, RT-3)

#### Decisión
Configurar el entorno de testing y desarrollo local para operar contra el Sandbox oficial de Mercado Pago sin generar costo alguno (RT-1), manteniendo equivalencia funcional exacta con producción (RT-2) y reproducibilidad controlada de todos los escenarios (RT-3).  
Documentación oficial consultada:
- Mercado Pago Developers - Tarjetas de prueba (Argentina):  
  `https://www.mercadopago.com.ar/developers/es/docs/checkout-pro/additional-content/test-cards`

#### Matriz de tarjetas de prueba oficiales de Mercado Pago Argentina

| Escenario de Prueba | Resultado en Mercado Pago | Tarjeta | Titular a ingresar | DNI | Comportamiento esperado en EasyEat |
|---|---|---|---|---|---|
| **AS-2 (Aprobado)** | `approved` (`accredited`) | Visa: `4509 9535 6623 3704` (CVV: `123`, Venc: `11/30`) | `APRO Test` | `12345678` | Reserva definitiva, orden confirmada, vaciado automático de carrito (Q4). |
| **AS-3 (Rechazado por fondos)** | `rejected` (`cc_rejected_insufficient_amount`) | Visa: `4509 9535 6623 3704` (CVV: `123`, Venc: `11/30`) | `FUND Test` | `12345678` | Reserva liberada, orden no pagada, checkout preservado con alerta explicativa (Q2). |
| **AS-3 (Rechazado por fraude)** | `rejected` (`cc_rejected_other_reason`) | Mastercard: `5031 7557 3453 0604` (CVV: `123`, Venc: `11/30`) | `OTHE Test` | `12345678` | Reserva liberada, orden no pagada, alerta en checkout y botón "Pagar" re-habilitado. |
| **AS-4 (Abandono voluntario)** | Cancelación en pasarela | N/A (Clic en "Volver al sitio") | N/A | N/A | Retorno a checkout, reserva liberada inmediatamente, carrito y franja intactos. |
| **AS-4b (Abandono sin retorno)**| Vencimiento de ventana | N/A (Cierre de navegador) | N/A | N/A | Al cumplirse los 15 min, el barredor libera automáticamente cupo y stock. |
| **AS-5 (Pago en curso)** | Estado en proceso | N/A (Reingreso con pago activo) | N/A | N/A | Panel informativo "Pago en curso" sin cuenta regresiva, botón Pagar deshabilitado, opciones Retomar y Cancelar. |
| **AS-6 (Fallo en inicio)** | Error de red / Timeout | Simulación de fallo en API MP | N/A | N/A | Aviso de fallo, no cobro, reversión inmediata de cualquier reserva temporal. |

#### Simulación de fallo de inicio de pago (AS-6)
En el entorno de integración y tests automatizados, se implementa una prueba con `MockRestServiceServer` o `WireMock` que intercepta la llamada saliente a `https://api.mercadopago.com/checkout/preferences` y devuelve una excepción de timeout de socket (`SocketTimeoutException`) o un código `HTTP 503 Service Unavailable`. Se comprueba que el servicio ejecuta un rollback inmediato, no persiste la reserva temporal como activa y expone al comensal un mensaje claro sin alterar el cupo del restaurante.

---

### 2.7. Experiencia de Usuario y Comportamiento en Frontend

#### Decisión
Extender el componente existente `CheckoutPage.tsx` y su suite de submódulos (`CheckoutSummary.tsx`), incorporando los componentes:
1. **`InProcessPaymentPanel.tsx`**: Desplegado cuando se detecta una orden con un intento de pago en curso (`status == 'IN_PROCESS'`). No muestra cuenta regresiva (Q3) para evitar ansiedad y discrepancias con el servidor; expone dos botones principales:
   - "Retomar pago" (abre la URL `initPoint` de Mercado Pago en una nueva pestaña o redirige).
   - "Cancelar intento" (llama al backend para liberar la reserva y restablecer el formulario de checkout con un clic).
2. **`PaymentAlertBanner.tsx`**: Banner accesible que renderiza la alerta explicativa en la cabecera del checkout tras un retorno fallido o cancelado ("El pago no se concretó. Podés seleccionar otro medio de pago o intentar nuevamente.", Q2).
3. **`PaymentReturnPage.tsx`**: Página de aterrizaje de las `back_urls` (`/checkout/payment-return`).
   - Extrae los parámetros `status` (`success`, `failure`, `pending`) y `orderId`.
   - Consulta el endpoint backend `GET /api/v1/orders/{orderId}/payment-status` para obtener el estado fehaciente (INV-08).
   - Si `status == APPROVED`: ejecuta `clearCart()` (Q4), muestra mensaje de confirmación y permite redirigir al seguimiento.
   - Si `status == REJECTED` o `CANCELLED`: preserva intactos el carrito y la franja en `CartContext` y redirige al comensal a `/checkout` con la alerta correspondiente activa.

---

### 2.8. Concurrencia y Pruebas de Estrés (INV-07, CB-10)

#### Decisión
Reutilizar el patrón de pruebas concurrentes ya validado en el repositorio (`ZeroOverbookingConcurrencyIntegrationTest.java`).  
Se diseña un test de integración específico: `AtomicReservationConcurrencyIntegrationTest.java`:
- Se configuran 20 hilos concurrentes compitiendo mediante `CountDownLatch` y `ExecutorService` por la **última unidad de stock de un plato** y el **último cupo de la franja horaria**.
- Se verifica que **exactamente una (1)** orden logre establecer la `TemporaryReservation` y avanzar a la creación de la preferencia en Mercado Pago.
- Las **19 solicitudes restantes** son rechazadas limpiamente con `HTTP 409 Conflict` antes de contactar a Mercado Pago, garantizando cero sobreventas de cupo y cero sobreventas de stock físico (SC-005).

---

### 2.9. Manejo de Configuración y Secretos

#### Decisión
Centralizar las propiedades de integración en la clase Java `MercadoPagoProperties`:
```java
@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
    String accessToken,
    String publicKey,
    String webhookSecret,
    boolean sandbox,
    String baseUrl,
    String frontendBaseUrl
) {}
```
Mapeo de variables de entorno:
- `MERCADOPAGO_ACCESS_TOKEN`
- `MERCADOPAGO_PUBLIC_KEY`
- `MERCADOPAGO_WEBHOOK_SECRET`
- `MERCADOPAGO_SANDBOX` (default `true` en perfiles `dev`, `local` y `test`)
- `MERCADOPAGO_BASE_URL` (default `https://api.mercadopago.com`)
- `EASYEAT_FRONTEND_URL` (default `http://localhost:5173` en local)
Ningún secreto ni token de acceso se almacena en el control de versiones; se documenta su provisión en `.env.example`.

