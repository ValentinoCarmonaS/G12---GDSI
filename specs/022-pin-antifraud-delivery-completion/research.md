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
| `Order` (`Order.java` / tabla `orders`) | **Parcialmente existente** | Posee `status` (`OrderStatus` incluye `READY_FOR_PICKUP` y `COMPLETED`), `fulfillmentMode`, fechas y slots. **NO posee** `security_code` ni `completed_at` (ni en JPA ni en migraciones Flyway `V16`, `V18`, `V23`). Tampoco almacena directamente `ticket_number` (se vincula con `KdsTicket`). |
| `KdsTicket` (`KdsTicket.java` / tabla `kds_tickets`) | **Totalmente existente** | Posee `status` (`KdsTicketStatus` incluye `QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP`, `COMPLETED`), `ticket_number`, `ready_at` y **ya posee** `completed_at` (`TIMESTAMPTZ NULL` en `V23` y campo `Instant completedAt` en JPA). |
| Bloqueo pesimista | **Existente** | `OrderRepository.findByIdForUpdate(UUID id)` ya implementa `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`), utilizado transversalmente en `CheckoutConfirmationServiceImpl`. |
| Creación de órdenes (Specs 014/015/016) | **Existente** | Se persisten órdenes en `CheckoutPaymentService.java` y `OrderService.java`. Ninguno asigna `security_code` actualmente. |
| Endpoints de mostrador (`/counter`) | **Inexistente en backend** | No existe `CounterOrderController.java` ni endpoints bajo `/api/v1/counter/`. Se introducirá `CounterOrderController` para alojar `POST /api/v1/counter/orders/{orderId}/complete-delivery` y la consulta de pedidos listos. |
| Pantalla de mostrador (`/mostrador`) | **Placeholder inicial** | `CounterWorkspacePage.tsx` renderiza un tablero estático inicial. Requiere incorporar el tablero de tarjetas expandibles de mostrador con el componente de PIN y el botón de entrega. |
| Seguimiento en vivo (`OrderLiveTrackingService.java`) | **Existente con agrupación prematura** | La línea 120 de `OrderLiveTrackingService.java` agrupa `READY_FOR_PICKUP` y `COMPLETED` devolviendo el mensaje de pedido listo. Debe bifurcarse para que `COMPLETED` devuelva su mensaje terminal y omita el PIN. |
| Polling de seguimiento (`useOrderLiveTrackingPolling.ts`) | **Existente con parada prematura** | `isTerminalTracking` incluye actualmente `READY_FOR_PICKUP`, deteniendo el polling antes de que el comensal pueda presenciar el cierre en vivo. Debe ajustarse para continuar hasta `COMPLETED`. |

---

## 2. Resoluciones técnicas consolidadas (Decisiones, justificación y alternativas)

### D-01: Punto de anclaje y generación del `security_code`
- **Decisión**: Generación automática determinista en el hook de ciclo de vida de persistencia `@PrePersist` dentro de `Order.java`:
  ```java
  if (this.securityCode == null) {
      this.securityCode = ThreadLocalRandom.current().nextInt(1000, 10000); // Rango cerrado [1000, 9999]
  }
  ```
  Se complementa con una columna en base de datos: `security_code INTEGER` con constraint `CONSTRAINT chk_order_security_code CHECK (security_code IS NULL OR (security_code >= 1000 AND security_code <= 9999))`.
- **Justificación**: Centraliza la garantía en un único punto del modelo de dominio. Toda orden creada en el sistema (checkout estándar de Mercado Pago, orden con monto cero, órdenes de prueba o flujos directos) nace automáticamente con su PIN válido sin riesgo de omisión por parte de servicios externos. La distribución es uniforme y por construcción matemática ($1000 \le x < 10000$) no genera ceros a la izquierda ni requiere relleno de cadenas.
- **Alternativas descartadas**:
  - *Generación dispersa en servicios de aplicación (`CheckoutPaymentService` y `OrderService`)*: Descartada por riesgo de duplicación y fallos de consistencia en nuevos paths de creación.
  - *Generación tipo String*: Descartada porque el repo maneja códigos y secuencias numéricas correlativas como enteros (`Integer`), optimizando el almacenamiento y las restricciones de rango relacionales.

### D-02: Garantía de inmutabilidad del `security_code`
- **Decisión**: Inmutabilidad a nivel de entidad JPA y arquitectura de servicios:
  - En `Order.java`, la columna se mapea con `@Column(name = "security_code", updatable = false)`.
  - No se exponen métodos de mutación pública (`setSecurityCode` queda restringido o protegido para tests).
  - Ningún endpoint o servicio de actualización (`update`, `patch`, o transiciones de estado) modifica dicho campo.
- **Justificación**: Previene mutaciones no autorizadas tanto a nivel de Hibernate como a nivel de capas superiores.
- **Alternativas descartadas**:
  - *Trigger en base de datos `BEFORE UPDATE`*: Añadiría complejidad procedural innecesaria en PostgreSQL sin aportar valor sobre la protección JPA `updatable = false` y la disciplina de servicios ya estandarizada en el proyecto.

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
  - Recibe `pin: number | null | undefined` y clases personalizables.
  - Aplica tipografía monoespaciada destacada (`font-mono font-bold tracking-[0.25em]`).
  - Formatea los dígitos separándolos con espacios: `pin.toString().split('').join(' ')` (ejemplo: `4 8 2 1`).
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
  - Si una orden en `READY_FOR_PICKUP` posee `security_code == null`, `SecurityPinDisplay` renderiza `- - - -`.
  - La tarjeta de mostrador se renderiza normalmente y el botón "ENTREGAR PEDIDO" permanece habilitado.
  - Si el operador de ventanilla decide proceder con la entrega física manual, puede accionar el botón y el cierre se procesará atómicamente en el servidor.
- **Justificación**: Alineado con la regla de negocio explícita del spec: "el contenedor del código se presenta con un indicador neutral de ausencia de código sin bloquear el renderizado de la tarjeta ni impedir la pulsación de entrega si el operador decide proceder manualmente".
- **Alternativas descartadas**:
  - *Bloquear el botón de entrega si falta el PIN*: Descartada porque impediría despachar bandejas físicas legítimas ante inconsistencias heredadas de órdenes previas.