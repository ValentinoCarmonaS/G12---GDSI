# Investigación técnica y arquitectura: protocolo antifraude de entrega y cierre de orden

**Especificación**: `022-pin-antifraud-delivery-completion`  
**Rama**: `022-pin-antifraud-delivery-completion`  
**Fecha**: 15/11/2026  
**Estado**: Consolidado  

---

## 1. Hallazgos del relevamiento del repositorio (fuente de verdad técnica)

Antes de definir el diseño técnico, se auditó exhaustivamente la base de código real para contrastar los supuestos iniciales con la realidad del repositorio:

| Componente / Entidad | Estado en el repositorio | Hallazgo técnico concreto |
|---|---|---|
| `Order` (`Order.java` / tabla `orders`) | **Parcialmente existente** | Posee `status` (`OrderStatus` incluye `READY_FOR_PICKUP` y `COMPLETED`), `fulfillmentMode`, fechas y slots. **Actualización 16/11/2026**: la spec 020 ya incorporó `security_code VARCHAR(4)` con `CHECK (security_code IS NULL OR security_code ~ '^[1-9][0-9]{3}$')` y backfill (`V26`), mapeado como `String securityCode` y generado en `@PrePersist` y en los servicios de checkout. **NO posee** `completed_at` (única columna faltante). Tampoco almacena directamente `ticket_number` (se vincula con `KdsTicket`). |
| `KdsTicket` (`KdsTicket.java` / tabla `kds_tickets`) | **Totalmente existente** | Posee `status` (`KdsTicketStatus` incluye `QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP`, `COMPLETED`), `ticket_number`, `ready_at` y **ya posee** `completed_at` (`TIMESTAMPTZ NULL` en `V23` y campo `Instant completedAt` en JPA). |
| Bloqueo pesimista | **Existente** | `OrderRepository.findByIdForUpdate(UUID id)` ya implementa `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`), utilizado transversalmente en `CheckoutConfirmationServiceImpl`. |
| Creación de órdenes (Specs 014/015/016) | **Existente** | Se persisten órdenes en `CheckoutPaymentService.java` y `OrderService.java`. **Actualización 16/11/2026**: ambos asignan `security_code` vía `OrderService.generateSecurityCode()` (`SecureRandom`, [1000, 9999]); `CheckoutConfirmationServiceImpl` y `PaymentWebhookService` lo asignan en la confirmación si la orden heredada carece de él. |
| Endpoints de mostrador (`/counter`) | **Existente (spec 020)** | **Actualización 16/11/2026**: `CounterOrderController.java` y `CounterReadyOrderResponse` ya existen y exponen `GET /api/v1/counter/orders`. Esta spec los **extiende** con `POST /api/v1/counter/orders/{orderId}/complete-delivery`. Defecto detectado: `resolveFourDigitSecurityCode` fabrica un PIN a partir del hash del UUID cuando falta el código, contradiciendo el Caso Borde 10 (ver D-15). |
| Pantalla de mostrador (`/mostrador`) | **Existente (spec 020)** | **Actualización 16/11/2026**: `CounterWorkspacePage.tsx`, `CounterTicketCard.tsx` (con el PIN en texto plano y el botón "ENTREGAR PEDIDO" aún sin acción) y `useCounterOrdersPolling.ts` ya existen. Esta spec incorpora `SecurityPinDisplay` y la acción de entrega. |
| Seguimiento en vivo (`OrderLiveTrackingService.java`) | **Existente con agrupación prematura** | El bloque `isReady` de `OrderLiveTrackingService.java` agrupa `READY_FOR_PICKUP` y `COMPLETED` devolviendo el mensaje de pedido listo y el PIN. Debe bifurcarse para que `COMPLETED` devuelva su mensaje terminal y omita el PIN. La exposición de `securityCode` (`String`) en `READY_FOR_PICKUP` ya fue incorporada por la spec 021. |
| Polling de seguimiento (`useOrderLiveTrackingPolling.ts`) | **Existente con parada prematura** | `isTerminalTracking` incluye actualmente `READY_FOR_PICKUP`, deteniendo el polling antes de que el comensal pueda presenciar el cierre en vivo. Debe ajustarse para continuar hasta `COMPLETED`. |

---

## 2. Resoluciones técnicas consolidadas (Decisiones, justificación y alternativas)

### D-01: Punto de anclaje y generación del `security_code`
- **Decisión** (*revisada el 16/11/2026 para alinearse con la spec 020 implementada*): se conserva el mecanismo existente. La columna es `security_code VARCHAR(4) NULL` con `CONSTRAINT chk_orders_security_code CHECK (security_code IS NULL OR security_code ~ '^[1-9][0-9]{3}$')` (migración `V26` de la spec 020, con backfill de órdenes previas), mapeada como `String securityCode` en `Order.java`. La generación ocurre en `OrderService.generateSecurityCode()` (`1000 + SecureRandom.nextInt(9000)`), invocada desde el `@PrePersist` de `Order` como red de seguridad y desde los servicios de creación de órdenes.
- **Justificación**: El regex `^[1-9][0-9]{3}$` es equivalente al rango cerrado $[1000, 9999]$ sin ceros a la izquierda (Invariante 1). Mantener el tipo evita una migración de tipo sobre datos ya poblados y no rompe los contratos ya entregados por las specs 020 (`CounterReadyOrderResponse.securityCode: String`) y 021 (`OrderLiveTrackingResponse.securityCode: String`).
- **Alternativas descartadas**:
  - *Migrar a `INTEGER`* (decisión original de este documento): descartada porque exige `ALTER COLUMN TYPE` sobre datos existentes y la reescritura de DTOs, tipos TypeScript, componentes y tests de 020/021 sin requerimiento que lo respalde (Principio V).
  - *Generación dispersa sin red de seguridad en la entidad*: descartada por riesgo de omisión en nuevos caminos de creación.

### D-02: Garantía de inmutabilidad del `security_code`
- **Decisión** (*revisada el 16/11/2026*): inmutabilidad de **escritura única** en el dominio. `Order` expone `assignSecurityCodeIfAbsent(String code)`, que asigna el código solo si la orden aún no lo posee y lanza `IllegalStateException` ante cualquier intento de reemplazar un código ya asignado por otro distinto. *Ampliación 17/11/2026 (T004/T005)*: un argumento nulo, en blanco o fuera de `^[1-9][0-9]{3}$` (la regla de `chk_orders_security_code`) lanza `IllegalArgumentException` antes de la verificación de escritura única, sin modificar la orden; `Order` ya no expone `setSecurityCode`. Los servicios (`OrderService`, `CheckoutPaymentService`, `CheckoutConfirmationServiceImpl`, `PaymentWebhookService`) dejan de invocar `setSecurityCode` y usan este método.
- **Justificación**: `CheckoutConfirmationServiceImpl` y `PaymentWebhookService` asignan el código en un `UPDATE` cuando una orden heredada carece de él. Con `@Column(updatable = false)` Hibernate omitiría la columna en ese `UPDATE` y el código se perdería en silencio, mientras la respuesta en memoria lo expondría al comensal: un PIN inexistente en la base. La escritura única cumple RF-001 (órdenes heredadas) y RF-002 (todas) en todos los caminos.
- **Alternativas descartadas**:
  - *`@Column(updatable = false)`* (decisión original): descartada por la pérdida silenciosa descrita.
  - *Trigger en base de datos `BEFORE UPDATE`*: complejidad procedural innecesaria en PostgreSQL.

### D-03: Atomicidad de `complete-delivery` y concurrencia pesimista
- **Decisión**: Transacción única `@Transactional(isolation = Isolation.READ_COMMITTED)` coordinada bajo bloqueo pesimista de escritura:
  - Se adquiere el bloqueo sobre la orden mediante `orderRepository.findByIdForUpdate(orderId)`.
  - Se verifica el estado: si ya es `COMPLETED`, se ejecuta la ruta idempotente sin mutaciones.
  - Si está en `READY_FOR_PICKUP`:
    1. Se fija `Instant now = Instant.now()`.
    2. `Order.status = COMPLETED` y `Order.completed_at = now`.
    3. Se recupera el ticket asociado `kdsTicketRepository.findByOrderId(order.getId())`:
       - `KdsTicket.status = COMPLETED` y `KdsTicket.completed_at = now`.
       - Se persiste el ticket.
    4. Se persiste la orden.
- **Justificación**: Garantiza la indivisibilidad transaccional requerida por `RF-010`, `RF-011` y `RF-012`. Si ocurre un fallo en cualquiera de las entidades, la transacción realiza rollback completo. El bloqueo `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) garantiza que dos operadores pulsando simultáneamente en dos terminales distintas sobre el mismo pedido se encolen a nivel de fila de base de datos; la segunda terminal esperará el bloqueo, leerá el estado `COMPLETED` y responderá inmediatamente de forma idempotente sin duplicar escrituras ni alterar `completed_at`.
- **Alternativas descartadas**:
  - *Optimistic Locking (`@Version`)*: Descartada porque requeriría agregar columnas de versión en tablas existentes ya estabilizadas y generaría excepciones de concurrencia al segundo operador en lugar de una respuesta limpia e idempotente.

### D-04: Idempotencia estricta de `POST complete-delivery`
- **Decisión**: Contrato idempotente absoluto. Si una orden ya se encuentra en estado `COMPLETED`:
  - Retorna HTTP `200 OK`.
  - Payload con `orderId`, `status: "COMPLETED"`, `completedAt` (el instante original registrado en la primera entrega) y `ticketNumber`.
  - No altera ninguna columna, no recalcula timestamps, no emite eventos de dominio duplicados.
- **Justificación**: Cumple con `RF-015`, `Invariante 8` e `Invariante 9`. Permite absorber reintentos de red, microcortes o pulsaciones concurrentes de forma transparente para la terminal de mostrador.
- **Alternativas descartadas**:
  - *Responder HTTP 409 Conflict ante orden ya completada*: Descartada porque causaría que reintentos legítimos de red muestren advertencias erróneas de conflicto al operador de mostrador en lugar de confirmar el éxito de la operación.

### D-05: Validación de estados previos en `complete-delivery`
- **Decisión**: Validación estricta de estado previo:
  - Si la orden no existe: `404 Not Found`.
  - Si la orden se encuentra en `READY_FOR_PICKUP`: procede la transición atómica a `COMPLETED` (`200 OK`).
  - Si la orden se encuentra en `COMPLETED`: responde con éxito idempotente (`200 OK`).
  - Si la orden se encuentra en cualquier otro estado (`PENDING_PAYMENT`, `PENDING_ACCREDITATION`, `IN_PROGRESS`, `CANCELLED`, `EXPIRED`): retorna `409 Conflict` con código de error de negocio `INVALID_ORDER_STATE` y mensaje descriptivo.
- **Justificación**: Protege la integridad del flujo operativo de ventanilla. Un pedido que aún está cocinándose en cocina o cuyo pago no fue acreditado no puede entregarse físicamente en mostrador.
- **Alternativas descartadas**:
  - *HTTP 400 Bad Request*: Descartada porque el cuerpo de la petición es semánticamente válido; se trata de un conflicto con el estado actual del recurso en el servidor (RFC 9110 §15.5.10).

### D-06: Contrato y filtro de mostrador (`GET /api/v1/counter/orders`)
- **Decisión**: El listado de mostrador filtra estrictamente por `status = READY_FOR_PICKUP` y fecha operativa de hoy (`pickupDate = hoy`).
- **Justificación**: Al transicionar una orden a `COMPLETED`, el siguiente ciclo de polling de la terminal de mostrador (cada 3 a 5 segundos) ya no recibe la comanda entregada, garantizando su desaparición automática del tablero activo tanto por la remoción optimista de la UI como por el filtrado del backend.
- **Alternativas descartadas**:
  - *Retornar órdenes completadas con una bandera de archivado*: Descartada porque sobrecargaría el ancho de banda del polling en un tablero operativo que solo requiere visualizar órdenes pendientes de retiro.

### D-07: Contrato de seguimiento del comensal y omisión de PIN en `COMPLETED`
- **Decisión**: Modificar `OrderLiveTrackingService.java` y `OrderLiveTrackingResponse`:
  - Para `status == OrderStatus.COMPLETED`:
    - `message`: `"Pedido Entregado — ¡Que disfrutes tu comida!"`.
    - `isReadyForPickup`: `false`.
    - `isCountdownActive`: `false`.
    - `progressPercentage`: `100`.
    - `securityCode`: `null` (campo omitido en el payload JSON o nulo).
  - En la respuesta de `GET /api/v1/orders/{orderId}/confirmation-tracking`:
    - Se preserva el detalle completo de compra (`items`, `totalAmount`, establecimiento).
- **Justificación**: Cumple con `RF-016` y el acuerdo de clarificación. Omitir el código en la respuesta HTTP previene que usuarios maliciosos inspeccionen el PIN en la pestaña de red del navegador una vez que la comida fue entregada.
- **Alternativas descartadas**:
  - *Enviar el PIN en el JSON pero ocultarlo solo con CSS*: Descartada porque el código continuaría expuesto en el tráfico de red del cliente.

### D-08: Protección anti doble clic en cliente
- **Decisión**: Estado reactivo síncrono `isSubmitting: boolean`:
  - Al primer evento `onClick`, antes de cualquier instrucción asíncrona o llamada `fetch`, se ejecuta síncronamente `setIsSubmitting(true)`.
  - El botón pasa de inmediato al atributo HTML `disabled={isSubmitting}` y clases visuales de inhabilitación (`cursor-not-allowed opacity-60`).
  - Cualquier clic posterior es ignorado instantáneamente por el navegador.
- **Justificación**: Cumple estrictamente con `RF-008`, `Invariante 10` y `Caso Borde 4` en menos de 10 milisegundos sin depender de la latencia de red.
- **Alternativas descartadas**:
  - *Debounce temporal con `setTimeout`*: Descartada porque un debounce permite clics adicionales si no se inhabilita de inmediato el nodo del DOM.

### D-09: Feedback verde inmediato en mostrador
- **Decisión**: Renderizado optimista síncrono del distintivo verde "Entrega Confirmada" en la misma tarjeta apenas se acciona el botón:
  - Estado local de la tarjeta: `isDelivered: boolean = true` al hacer clic.
  - Se sustituye el botón por una insignia verde destacada con texto "Entrega Confirmada" y un ícono de verificación.
  - Si la llamada de red falla posteriormente: se revierte el estado `isDelivered = false`, se reestablece `isSubmitting = false` y se despliega una notificación no intrusiva.
- **Justificación**: Brinda certeza sensorial inmediata al operador (`RF-009`, `SC-004`) sin ralentizar el despacho en picos de alta demanda.
- **Alternativas descartadas**:
  - *Esperar el `200 OK` del backend para pintar el distintivo verde*: Descartada porque la latencia de red introduciría una demora perceptual inadmisible en mostrador.

### D-10: Remoción de tarjeta sin layout shift ni colapso de tarjetas adyacentes
- **Decisión**: Gestión de grilla con identidades estables:
  - Las tarjetas en la pantalla de mostrador se mapean utilizando un identificador unívoco y estable como clave (`key={order.orderId}`).
  - El estado de tarjetas desplegadas se administra en un conjunto inmutable `expandedOrderIds: Set<string>`.
  - Tras la respuesta exitosa de `complete-delivery`: la tarjeta entregada se filtra del array local de órdenes activas (`orders.filter(o => o.orderId !== deliveredOrderId)`).
  - La remoción de un elemento del array no recrea los componentes adyacentes ni reinicia el `Set` de tarjetas expandidas.
- **Justificación**: Cumple con `RF-013`, `RF-014` y `Caso Borde 7`. Preserva intactas las posiciones relativas y el contenido abierto de las restantes tarjetas en ventanilla.
- **Alternativas descartadas**:
  - *Recargar la lista completa reiniciando el estado local*: Descartada porque colapsaría todas las tarjetas que el encargado esté consultando en ese momento.

### D-11: Cese definitivo del sondeo en seguimiento del comensal
- **Decisión**: Modificación de `useOrderLiveTrackingPolling.ts`:
  - Se remueve `READY_FOR_PICKUP` de `TERMINAL_TRACKING_STATUSES`.
  - `TERMINAL_TRACKING_STATUSES` queda compuesto exclusivamente por `{'COMPLETED', 'CANCELLED', 'EXPIRED'}`.
  - Al recibir una respuesta donde `status === 'COMPLETED'`, `isTerminalTracking` evalúa a `true`, cancela el `timerRef.current`, aborta cualquier petición en vuelo y no vuelve a programar `scheduleNext()`.
- **Justificación**: Resuelve la discrepancia detectada con la spec 021. Permite que el comensal conectado en ventanilla detecte en vivo la conmutación al mensaje "Pedido Entregado — ¡Que disfrutes tu comida!" y, en ese preciso instante, se inhiban permanentemente futuros sondeos, liberando recursos del cliente y del servidor (`RF-017`, `Invariante 12`).
- **Alternativas descartadas**:
  - *Detener el polling en `READY_FOR_PICKUP`*: Descartada porque obligaría al cliente a refrescar manualmente la página para enterarse de que el pedido fue archivado.

### D-12: Componente compartido para visualización monospace del PIN
- **Decisión**: Creación del componente compartido `components/common/SecurityPinDisplay.tsx`:
  - Recibe `pin: string | null | undefined` (tipo alineado con el `securityCode: String` de las specs 020/021) y clases personalizables.
  - Aplica tipografía monoespaciada destacada (`font-mono font-bold tracking-[0.25em]`).
  - Formatea los dígitos separándolos con espacios: `pin.split('').join(' ')` (ejemplo: `4 8 2 1`).
  - Si el PIN no está disponible (caso borde 10), renderiza un marcador neutral `- - - -`.
  - Se reutiliza tanto en la tarjeta de mostrador como en la llamada de retiro del comensal (`ReadyForPickupCallout.tsx`).
- **Justificación**: Garantiza identidad visual idéntica entre la pantalla del comensal y la terminal de ventanilla (`RF-003`, `RF-004`, `Invariante 3`).
- **Alternativas descartadas**:
  - *Formatear con cadenas dispersas en cada archivo*: Descartada por riesgo de divergencias en estilos tipográficos o tracking.

### D-13: Frontera con la spec 019 (KDS de cocina)
- **Decisión**: Aislamiento estricto de la cocina:
  - Al ejecutar `complete-delivery`, `KdsTicket.status` pasa a `COMPLETED`.
  - El KDS de cocina (spec 019) solo lista comandas en `QUEUED` e `IN_PREPARATION` (y cuenta métricas globales).
  - Ninguna comanda en `COMPLETED` ingresa ni es visualizada en las columnas operativas de cocina.
  - No se modifica ninguna lógica, endpoint ni interfaz del KDS culinario.
- **Justificación**: Preserva la estabilidad de la cocina y cumple con el principio de mínima alteración y aislamiento modular.

### D-14: Manejo de error de comunicación en mostrador
- **Decisión**: Resiliencia y aviso no intrusivo ante fallas de red:
  - Si el `fetch` de `complete-delivery` arroja excepción de red o HTTP 5xx:
    1. Se cancela el distintivo verde y se reactiva el botón "ENTREGAR PEDIDO" (`isSubmitting = false`, `isDelivered = false`).
    2. La tarjeta permanece visible y desplegada en la pantalla de mostrador.
    3. Se despliega una notificación flotante sutil (toast / banner no intrusivo) en la tarjeta con el texto: "Error de comunicación al registrar la entrega. Por favor, reintentá."
    4. El operador puede volver a pulsar el botón inmediatamente.
- **Justificación**: Cumple con `RF-018` y `Invariante 11`. Evita que un fallo transitorio de conectividad descarte físicamente la orden de la pantalla sin haberse confirmado en el servidor.
- **Alternativas descartadas**:
  - *Alert modal bloqueante*: Descartada porque interrumpe la fluidez operativa de la ventanilla y bloquea la atención de otros pedidos.

### D-15: Caso borde de orden sin PIN (Caso Borde 10)
- **Decisión**: Tolerancia a inconsistencias de datos preexistentes:
  - Si una orden en `READY_FOR_PICKUP` posee `security_code == null` (o un valor que no cumple el formato), `GET /api/v1/counter/orders` devuelve `securityCode: null` y `SecurityPinDisplay` renderiza `- - - -`.
  - El backend **no fabrica** un código sustituto: el fallback actual de `CounterOrderController.resolveFourDigitSecurityCode` (PIN derivado del hash del UUID, introducido por la spec 020) se elimina en la Fase 3 (tarea T016b), porque exhibiría en mostrador un código que el comensal nunca recibió.
  - La tarjeta de mostrador se renderiza normalmente y el botón "ENTREGAR PEDIDO" permanece habilitado.
  - Si el operador de ventanilla decide proceder con la entrega física manual, puede accionar el botón y el cierre se procesará atómicamente en el servidor.
- **Justificación**: Alineado con la regla de negocio explícita del spec: "el contenedor del código se presenta con un indicador neutral de ausencia de código sin bloquear el renderizado de la tarjeta ni impedir la pulsación de entrega si el operador decide proceder manualmente".
- **Alternativas descartadas**:
  - *Bloquear el botón de entrega si falta el PIN*: Descartada porque impediría despachar bandejas físicas legítimas ante inconsistencias heredadas de órdenes previas.

---

## 3. Registro de alineación con las specs 019, 020 y 021 implementadas (sesión grill-me 16/11/2026)

Los artefactos técnicos de esta spec se redactaron el 15/11/2026 contra el código previo a la implementación de las specs 019, 020 y 021. Al iniciar la implementación se contrastaron con `develop/sprint-5` y se resolvieron con el usuario las siguientes decisiones:

| # | Decisión | Justificación |
|---|---|---|
| A-01 | La rama `022-pin-antifraud-delivery-completion` se avanza (*fast-forward*) a `develop/sprint-5` y cada fase se ramifica desde ella. | 022 depende del código de 019/020/021, ausente en la rama de la spec y en `010-restaurant-operation-config`. |
| A-02 | `security_code` se mantiene como `VARCHAR(4)` / `String` (D-01 revisada). | Ya implementado y consumido por 020/021; el regex equivale al rango [1000, 9999]. |
| A-03 | La única migración de esta spec es `V28__add_completed_at_to_orders.sql`, que agrega solo `orders.completed_at` sin índice. | `V25` ya está consumida (índice KDS de 019) y las migraciones llegan a `V27`; ninguna consulta de 022 filtra por `completed_at`, por lo que un índice no se justifica (Principio IV). |
| A-04 | El RED de T001 es un test del script en el classpath, que ubica la migración por su descripción y no por su versión. | Patrón de las specs 020/021 para migraciones y regla 5 de `.specify/memory/migrations.md`. La aplicación real contra PostgreSQL queda en T006/T007. |
| A-05 | T002 se limita a `securityCode: string \| null` en `CounterReadyOrder` y al nuevo tipo `CompleteDeliveryResponse`. | Lo demás (`CounterReadyOrder`, `securityCode` y `COMPLETED` en seguimiento) ya existe. |
| A-06 | T003 se cierra como satisfecha por la spec 020 sin cambios de código; `completeDelivery` permanece en T030. | `counterApi.ts` ya existe con cobertura en `tests/api/counterApi.test.ts`; el endpoint de entrega nace en T025. |
| A-07 | El PIN fabricado por `resolveFourDigitSecurityCode` se corrige en la Fase 3 mediante la nueva tarea T016b. | Contradice el Caso Borde 10 y el contrato 02; no corresponde a Setup (Principio V). |
| A-08 | La inmutabilidad del PIN es de escritura única en el dominio (D-02 revisada). | `updatable = false` perdería en silencio el PIN asignado en la confirmación de órdenes heredadas. |
| A-09 | Los contratos 02 y 03 se alinean con las formas ya entregadas por 020/021 (`securityCode` como cadena, `customer.photoUrl`, cadencia de sondeo de 5 a 8 s). | 022 no redefine esos endpoints salvo en el PIN nulo y el estado `COMPLETED`. |
