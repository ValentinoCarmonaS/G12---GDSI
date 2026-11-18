# Investigación y decisiones de diseño técnico: despacho a mostrador y sincronización de pedidos listos

**Identificador de especificación**: `020-counter-dispatch-ready-orders`  
**Rama de trabajo**: `020-counter-dispatch-ready-orders`  
**Fecha de elaboración**: 15/11/2026  
**Estado**: Completo  

---

## Resumen ejecutivo

Este documento consolida el análisis de arquitectura, persistencia, contratos de red y patrones de interfaz para materializar la funcionalidad de despacho a mostrador y sincronización reactiva de pedidos listos, cubriendo las historias de usuario `CHF-4.1.3`, `CHF-4.2.3`, `ENC-2.1.1` y `ENC-2.1.2`. Resuelve formalmente las ambigüedades técnicas y vacíos identificados entre la especificación funcional y el estado del repositorio, alineado con los acuerdos alcanzados en la sesión de ingeniería con el responsable del proyecto.

---

## Resoluciones de investigación (Fase 0)

### 1. Atomicidad e idempotencia en la finalización de comanda (`ready-for-pickup`)

- **Contexto**: El chef en la Columna 2 de cocina pulsa "LISTO" para finalizar la bandeja. La acción debe transicionar simultáneamente el ticket de cocina (`KdsTicket`) y la orden del comensal (`Order`) a `READY_FOR_PICKUP`. Ante ráfagas de clics en pantallas táctiles o reintentos de red automáticos, la operación no debe duplicar registros, no debe alterar el instante de finalización original y no debe producir inconsistencias entre entidades.
- **Decisión**:
  1. Bloqueo pesimista: invocar `kdsTicketRepository.findByIdForUpdate(ticketId)` anotado con `@Lock(LockModeType.PESSIMISTIC_WRITE)` dentro de un método `@Transactional` en `OrderLiveTrackingService` (o `KdsQueueService`).
  2. Verificación de idempotencia estricta: si `ticket.getStatus() == KdsTicketStatus.READY_FOR_PICKUP`, retornar inmediatamente respuesta exitosa (`200 OK`) reutilizando el valor de `ticket.getReadyAt()` persistido originalmente, sin reescribir la marca temporal ni mutar nuevamente la orden.
  3. Transición conjunta atómica: si el estado es `IN_PREPARATION` (o `QUEUED`), fijar `Instant now = clock.instant()`, actualizar `ticket.setStatus(READY_FOR_PICKUP)`, `ticket.setReadyAt(now)`, `order.setStatus(READY_FOR_PICKUP)`, `order.setReadyAt(now)`, `order.setUpdatedAt(now)` y persistir ambas entidades dentro de la misma transacción física de base de datos.
- **Justificación**: Garantiza la indivisibilidad exigida por el `RF-003` e `Invariante 1`, inmune a condiciones de carrera por concurrencia multi-terminal o reintentos HTTP, preservando la inmutabilidad de la marca temporal para el cálculo exacto del contador de espera.
- **Alternativas descartadas**:
  - *Bloqueo optimista (`@Version`)*: descartado porque exigiría agregar columnas de versión en tablas ya estabilizadas en producción (`orders`, `kds_tickets`), aumentando el riesgo de migraciones sobre datos existentes.
  - *Actualización asíncrona mediante eventos o mensajería*: descartada porque la especificación prohíbe colas de eventos externas y exige consistencia inmediata en la respuesta del endpoint.

---

### 2. Coincidencia temporal estricta de `ready_at` entre `KdsTicket` y `Order`

- **Contexto**: La especificación establece que el instante de finalización registrado en el ticket de cocina y en la orden debe ser exactamente idéntico (`Invariante 2`). Actualmente, `kds_tickets.ready_at` existe en la base de datos (migraciones `V20` y `V23`), pero la tabla `orders` solo posee `created_at` y `updated_at`.
- **Decisión**:
  1. Incorporar la columna `ready_at TIMESTAMPTZ NULL` en la tabla `orders` mediante la migración Flyway incremental `V26__counter_dispatch_and_security_code.sql`.
  2. Mapear el atributo `private Instant readyAt` en la entidad `Order.java` con sus correspondientes *getters* y *setters*.
  3. Asignar la misma referencia temporal `Instant now` a `ticket.setReadyAt(now)` y `order.setReadyAt(now)` en la capa de servicio al ejecutar la transición.
- **Justificación**: Cumple al 100% el invariante de coincidencia temporal inmutable sin depender de lecturas secundarias ni de joins costosos para auditar cuándo estuvo lista la orden desde la perspectiva del pedido general.
- **Alternativas descartadas**:
  - *Mantener `ready_at` solo en `kds_tickets` y resolverlo por Join en `orders`*: descartado porque viola el principio de trazabilidad simétrica y obligaría a la aplicación del comensal a consultar tablas internas del KDS para conocer el momento exacto en que su comida estuvo lista.

---

### 3. Persistencia y entrega del código numérico de seguridad de 4 dígitos

- **Contexto**: El `RF-012` exige que la tarjeta desplegada en mostrador exhiba un código numérico de seguridad de 4 dígitos para cotejo visual y oral con el comensal. Dicho código es un requerimiento compartido que se completa en la especificación `022-pin-antifraud-delivery-completion`, pero actualmente no se encuentra modelado en la tabla `orders`.
- **Decisión**:
  1. Incorporar la columna `security_code VARCHAR(4) NULL` en la tabla `orders` en la migración `V26__counter_dispatch_and_security_code.sql`.
  2. Generar un código aleatorio criptográficamente seguro en el rango `1000` a `9999` (ambos inclusive) en `OrderService` al momento de crearse o confirmarse la orden pagada.
  3. Para órdenes existentes en base de datos sin código asignado, la migración `V26` ejecutará un backfill asignando `security_code = LPAD(FLOOR(1000 + RANDOM() * 9000)::TEXT, 4, '0')`.
  4. Exponer `securityCode` como cadena de texto de 4 caracteres en el DTO de mostrador `CounterReadyOrderResponse`.
- **Justificación**: Provee una base sólida y compatible con la futura spec 022, asegurando que la interfaz de mostrador cuente inmediatamente con el dato real de base de datos sin requerir simulaciones ni *mocking* volátil.
- **Alternativas descartadas**:
  - *Generar el código en memoria en el DTO a partir del hash del ID*: descartado porque produciría desincronización con la pantalla del comensal y rompería la inmutabilidad requerida en el protocolo antifraude.
  - *Guardar el código en `kds_tickets`*: descartado porque el código es un atributo de la orden del cliente, no de la comanda operativa interna de la cocina.

---

### 4. Entrega de fotografía del comensal y omisión limpia

- **Contexto**: La tarjeta desplegada en mostrador debe presentar la foto de perfil del comensal si está disponible, o realizar una omisión limpia sin contenedores vacíos ni avatares genéricos de sustitución (`RF-012`, `AC-2`, caso borde 3). En el backend existe la tabla `user_photos`, pero el controlador actual `ProfileAvatarController` restringe la lectura al propio usuario autenticado.
- **Decisión**:
  1. Exponer en backend un endpoint de solo lectura para el personal de servicio: `GET /api/v1/profile/users/{userId}/avatar`, protegido mediante `@PreAuthorize("hasAnyRole('ENCARGADO', 'CHEF', 'ADMINISTRADOR')")`.
  2. El endpoint retorna el flujo de bytes de la imagen con la cabecera `Content-Type` correspondiente y `Cache-Control: private, max-age=3600`. Si el usuario no posee foto en `user_photos`, responde con `404 Not Found`.
  3. En la consulta de pedidos de mostrador, el servicio verifica la existencia de foto mediante `userPhotoRepository.existsByUserId(order.getUserId())`. Si existe, asigna en el DTO `photoUrl: "/api/v1/profile/users/" + userId + "/avatar"`; si no existe, asigna `photoUrl: null`.
  4. En el frontend, el componente de tarjeta renderiza el elemento `<img>` únicamente cuando `photoUrl` no es nulo; de lo contrario, el contenedor de avatar no se genera en el DOM y el nombre del comensal se alinea limpiamente a la izquierda.
- **Justificación**: Reutiliza la arquitectura existente de almacenamiento en base de datos (`BYTEA` en `user_photos`), respeta la seguridad basada en roles sin exponer avatares públicamente a usuarios anónimos, y cumple estrictamente el criterio de omisión limpia sin placeholders.
- **Alternativas descartadas**:
  - *Embeber imágenes en Base64 en el payload de órdenes*: descartado porque sobrecargaría el polling cada 3–5 segundos con transferencias innecesarias de bytes pesados.
  - *Hacer público el endpoint de avatares sin autenticación*: descartado por razones de privacidad y protección de datos personales de los clientes.

---

### 5. Estrategia de sincronización y resiliencia en mostrador (Polling HTTP)

- **Contexto**: La pantalla de mostrador requiere sincronización reactiva en tiempo cuasi-real (3–5 s) sin recargas completas, con inmunidad a pestañas en segundo plano y detección de cortes prolongados de conectividad (`RF-014`, `RF-018`, `SC-002`).
- **Decisión**:
  1. Ciclo de sondeo: intervalo nominal de 4000 ms (4 segundos), configurable mediante variable de entorno o constante tipada.
  2. Cabeceras HTTP: peticiones periódicas emitidas con `Cache-Control: no-cache` y `Pragma: no-cache` para evitar respuestas cacheadas en proxies intermedios o navegadores.
  3. Cancelación al desmontar: control de solicitudes concurrentes mediante `AbortController`. Al desmontar la página de mostrador, se cancela la petición activa y se limpia el temporizador.
  4. Ahorro de recursos (`visibilitychange`): cuando `document.visibilityState === 'hidden'`, el temporizador de polling se pausa; al retornar a `visible`, se reanuda de inmediato con una consulta fresca.
  5. Detección de desconexión (umbral 15 segundos): se registra en estado local la marca de tiempo del último refresco exitoso (`lastSuccessfulSyncTime`). Si `now - lastSuccessfulSyncTime > 15000 ms`, la interfaz exhibe una insignia superior no intrusiva: `"Sin conexión — Reintentando..."`.
  6. Retiro automático: en cuanto una consulta subsiguiente responda `200 OK`, el estado de error se resetea y la insignia se oculta de inmediato sin intervención del usuario.
- **Justificación**: Mantiene una arquitectura stateless de bajo consumo en el servidor, sin la complejidad operacional de WebSockets o SSE, garantizando resiliencia comprobada ante contingencias de red.
- **Alternativas descartadas**:
  - *WebSocket / Server-Sent Events (SSE)*: descartados explícitamente por los requisitos de la especificación y los límites de arquitectura vigentes.
  - *Bloqueo de pantalla ante desconexión*: descartado porque el personal de ventanilla debe poder seguir leyendo las órdenes ya cargadas en pantalla mientras el enlace se restablece.

---

### 6. Gestión de estado local por tarjeta y protección contra colapso

- **Contexto**: El operador puede desplegar una o más tarjetas para cotejar platos. Los refrescos de fondo periódicos cada 4 s no deben colapsar tarjetas abiertas ni provocar parpadeos visuales (`RF-010`, `RF-011`, `RF-015`, `SC-003`).
- **Decisión**:
  1. Identidad estable: cada tarjeta se renderiza con `key={order.ticketId}` único e invariable.
  2. Estado de expansión autónomo: el componente de vista administra `expandedTicketIds: Set<string>` en el estado local de React.
  3. Toggle bidireccional con protección interna:
     - El manejador `handleToggleCard(ticketId)` se vincula exclusivamente a la cabecera superior y a la zona neutral de la tarjeta.
     - Los contenedores interiores interactivos (lista de ítems y contenedor del botón de entrega) implementan `onClick={(e) => e.stopPropagation()}` para neutralizar cualquier propagación de eventos que cause un cierre involuntario.
  4. Invarianza durante el *merge* de datos: cuando arriba una nueva respuesta de la API, el array de órdenes se reemplaza en estado, pero el conjunto `expandedTicketIds` permanece inalterado, preservando intacto el despliegue de las tarjetas seleccionadas.
- **Justificación**: Brinda una experiencia de usuario robusta y libre de frustraciones operativas en horas pico de atención.
- **Alternativas descartadas**:
  - *Modo acordeón (solo una tarjeta abierta a la vez)*: descartado porque el `RF-010` exige soporte simultáneo para múltiples tarjetas desplegadas.
  - *Persistir el estado desplegado en el backend*: descartado porque el estado de interfaz es volátil y exclusivo de la sesión de cada monitor de mostrador.

---

### 7. Filtro de jornada operativa actual y zona horaria

- **Contexto**: El `RF-005` exige mostrar únicamente pedidos en estado `READY_FOR_PICKUP` pertenecientes a la jornada operativa actual (`hoy`) del establecimiento, excluyendo pedidos de fechas futuras y comandas antiguas no cerradas.
- **Decisión**:
  1. La consulta en backend evalúa `k.pickupDate = :today`, donde `:today` se calcula utilizando la zona horaria oficial del restaurante (`ZoneId.of("America/Argentina/Buenos_Aires")`).
  2. Se excluyen registros con `k.pickupDate > :today` y registros que no posean `k.status = 'READY_FOR_PICKUP'`.
- **Justificación**: Evita que pedidos programados para días posteriores aparezcan prematuramente en el mostrador y previene desfasajes causados por servidores en UTC que operen cruzando la medianoche local.
- **Alternativas descartadas**:
  - *Filtrar fechas en el navegador*: descartado porque transmitiría pedidos no pertinentes a través de la red, desperdiciando ancho de banda.

---

### 8. Ordenamiento determinista y optimización de base de datos

- **Contexto**: Las tarjetas deben mostrarse en orden de mayor tiempo de espera primero (`ready_at ASC`), desempatando por número correlativo de ticket (`ticket_number ASC`) (`RF-017`).
- **Decisión**:
  1. La consulta JPQL / SQL aplica: `ORDER BY k.readyAt ASC, k.ticketNumber ASC`.
  2. Se crea el índice compuesto en PostgreSQL mediante migración `V26`:
     `CREATE INDEX idx_kds_tickets_counter_ready ON kds_tickets(establishment_id, status, pickup_date, ready_at, ticket_number);`
- **Justificación**: El índice compuesto cubre exactamente las condiciones de filtrado (`equality`) y de ordenamiento (`range/sort`), permitiendo una ejecución de lectura *Index-Scan* pura en menos de 10 milisegundos aun con miles de tickets históricos.
- **Alternativas descartadas**:
  - *Ordenar en memoria en el cliente*: descartado porque rompería la paginación o el límite de lote si la cola superase el tamaño de página.

---

### 9. Cálculo del contador Count-Up y manejo de desfasaje de reloj

- **Contexto**: Cada tarjeta debe exhibir el tiempo de espera en minutos enteros transcurridos desde `ready_at`, iniciando en `0 min` e incrementando a `1 min` a los 60 segundos (`RF-007`, `RF-008`).
- **Decisión**:
  1. Función pura de cálculo: `calculateWaitMinutes(readyAtIso: string, serverNowMs: number, clientNowMs: number): number`.
  2. Fórmula: `const elapsedMs = Math.max(0, currentEffectiveTimeMs - readyAtMs);` seguido de `Math.floor(elapsedMs / 60000);` retornando el formato `${minutes} min`.
  3. Desfasaje: en la primera llamada exitosa, se calcula `serverSkewMs = serverTimeHeaderMs - Date.now()`. El reloj local ajusta la hora actual sumando el sesgo del servidor para evitar discrepancias por relojes descalibrados en las terminales cliente.
  4. Temporizador de actualización de reloj: un `setInterval` cada 10 o 30 segundos en el componente de tarjeta recalcula el valor en pantalla sin disparar peticiones de red.
- **Justificación**: Asegura consistencia cross-device sin acoplamiento a llamadas de servidor adicionales.
- **Alternativas descartadas**:
  - *Generar el texto del contador en backend*: descartado porque obligaría a hacer polling cada segundo para que el contador avance fluidamente.

---

### 10. Agnosticismo monetario absoluto en la interfaz de mostrador

- **Contexto**: El `RF-013` e `Invariante 10` prohíben taxativamente la inclusión de importes, precios, subtotales, totales o símbolos de divisa en la pantalla de mostrador.
- **Decisión**:
  1. Diseño del DTO de transporte: `CounterReadyOrderResponse` no contiene ningún campo de tipo `BigDecimal`, `price`, `amount`, `subtotal` ni `total`.
  2. Los ítems de la orden se modelan en el DTO como `CounterOrderItemDto { String dishName; Integer quantity; }`.
  3. En frontend, el desglose se formatea estrictamente como `${item.quantity}x ${item.dishName}`.
- **Justificación**: Protege la confidencialidad operativa, simplifica el modelo de datos expuesto al mostrador y previene que errores de formateo muestren datos no solicitados.

---

### 11. Sincronización cocina → mostrador y remoción en KDS

- **Contexto**: Al presionar "LISTO" en la Columna 2 de cocina, la tarjeta debe removerse inmediatamente de la vista de producción (`RF-004`) y la orden debe ingresar a la cola de mostrador en un máximo de 5 segundos (`SC-002`).
- **Decisión**:
  1. En `KitchenWorkspacePage` / `KitchenKanbanBoard`: al presionar "LISTO", el cliente ejecuta `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`.
  2. Al recibir confirmación exitosa (`200 OK`), la tarjeta se filtra de inmediato del estado local de la Columna 2 de cocina (remoción optimista/confirmada).
  3. En la terminal de mostrador, el siguiente ciclo de polling (que corre cada 4 s) recupera la nueva comanda con `ready_at` persistido y la inserta automáticamente al final o en la posición FIFO correspondiente de la grilla.
- **Justificación**: Proporciona *feedback* instantáneo al cocinero liberando la pantalla sin generar latencias perceptibles y respeta el acoplamiento desacoplado por polling.

---

### 12. Delimitación con especificaciones posteriores (021 y 022)

- **Contexto**: Es crítico no bloquear la futura incorporación del botón `+5` (Spec 021) ni la verificación interactiva del PIN / entrega final (Spec 022).
- **Decisión**:
  1. Spec 021: la pantalla de mostrador exhibe el turno horario pactado `HH:mm - HH:mm`. Si en el futuro una orden acumula demoras, el campo `timeSlot` consumirá el horario ajustado sin requerir cambios en el diseño de tarjetas.
  2. Spec 022: en la tarjeta desplegada se maqueta un contenedor visual con el botón "ENTREGAR PEDIDO". En esta fase 020, este contenedor es un elemento visual pasivo (deshabilitado o sin acción de cierre de orden conectada) que preserva el espacio visual reservado para el protocolo de cierre definitivo.
- **Justificación**: Evita retrabajo en la maquetación de la tarjeta en la spec 022 y asegura cumplimiento de las fronteras de alcance de la spec 020.
