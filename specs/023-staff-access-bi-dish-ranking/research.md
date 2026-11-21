# Investigacion tecnica: control de acceso de personal, salud financiera y ranking de platos

**Rama**: `023-staff-access-bi-dish-ranking` | **Fecha**: 20/11/2026 | **Especificacion**: [spec.md](./spec.md)

---

## 1. Contexto y objetivos de investigacion

El presente documento consolida las decisiones de diseno e ingenieria para implementar las tres historias de usuario independientes de la especificacion 023:
1. Historia 3.1.3: Baja y suspension de acceso de exempleados.
2. Historia 6.1.1: Panel de salud financiera del local.
3. Historia 6.2.1: Ranking de platos mas vendidos.

Cada seccion registra la decision adoptada, su justificacion tecnica y las alternativas descartadas, resolviendo todas las incognitas del contexto tecnico.

---

## 2. Decisiones de arquitectura y diseno

### D-01: Verificacion de estado de cuenta en inicio de sesion (Historia 3.1.3)

* **Decision**: En `AuthService.login`, verificar el estado del usuario inmediatamente despues de validar la contrasena con BCrypt. Si `user.getStatus()` no es `ACTIVE` ni `PENDING_FIRST_LOGIN`, registrar internamente el evento `ACCOUNT_SUSPENDED` en `SecurityAuditLogger` y lanzar de forma inmediata `InvalidCredentialsException` con el mensaje generico `"The provided email or password is incorrect."` (HTTP 401 Unauthorized).
* **Justificacion**:
  - Cumple estrictamente con FR-002, FR-003 y SC-001, haciendo que el rechazo sea indistinguible de una contrasena incorrecta o un correo inexistente.
  - Al ejecutar la comparacion BCrypt previa, no se introducen diferencias de tiempo de respuesta que permitan a un atacante inferir si una cuenta existe y se encuentra suspendida (mitigacion de ataques de temporizacion).
  - La excepcion `AccountSuspendedException` (HTTP 403 Forbidden) revelaba indebidamente el estado de la cuenta al cliente externo; al sustituirla por `InvalidCredentialsException` en el controlador, la respuesta es identica a un fallo estandar de autenticacion.
* **Alternativas consideradas**:
  - *Verificar el estado antes de comparar la contrasena*: Descartada porque responder de inmediato en cuentas suspendidas ahorra el costo de BCrypt pero filtra por temporizacion la existencia y suspension de la cuenta.
  - *Conservar AccountSuspendedException y remapearla en GlobalExceptionHandler*: Descartada porque mantener dos excepciones con identico ProblemDetail HTTP 401 introduce duplicacion innecesaria en la capa de presentacion.

### D-02: Atomicidad en la proteccion contra orfandad administrativa (Historia 3.1.3)

* **Decision**: Implementar la proteccion contra orfandad administrativa dentro de la transaccion de suspension en `StaffService` utilizando bloqueo pesimista de escritura (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) a nivel de repositorio sobre los usuarios administradores del establecimiento (`StaffUserRepository.findActiveAdminsForUpdate`). Si el conteo de administradores activos es menor o igual a 1, rechazar la operacion lanzando `OrphanedAdminException` (HTTP 409 Conflict).
* **Justificacion**:
  - Cumple con FR-005, SC-002 y el invariante de proteccion administrativa.
  - Garantiza que si dos administradores intentan suspenderse mutuamente en paralelo, una transaccion bloquea a la otra; la segunda transaccion relee el estado real serializado, detecta que solo queda un administrador activo y aborta con 409 Conflict.
* **Alternativas consideradas**:
  - *Nivel de aislamiento SERIALIZABLE a nivel de metodo*: Descartada porque eleva la contencion global de la base de datos y requiere logica de reintentos en caso de errores de serializacion (`could not serialize access`).
  - *Conteo simple sin bloqueo en Read Committed*: Descartada porque genera una condicion de carrera clasica donde dos transacciones concurrentes leen simultaneamente un conteo de 2 y ambas proceden, dejando al local con 0 administradores activos.

### D-03: Contrato REST para suspension e inactivacion de personal (Historia 3.1.3)

* **Decision**: Exponer el endpoint `PATCH /api/v1/admin/staff/{id}/status` con el cuerpo de solicitud `{ "status": "SUSPENDED" }` (o `"ACTIVE"`), retornando `StaffMemberResponse` con HTTP 200 OK.
* **Justificacion**:
  - Sigue la convencion REST de mutacion parcial de recursos (`PATCH`).
  - Aísla la modificacion del estado de la cuenta de la actualizacion de datos personales o rol (`PUT /api/v1/admin/staff/{id}`), evitando errores accidentales de sobreescritura de nombres o correos.
  - Reutiliza la estructura `StaffAccountStatus` (`ACTIVE`, `SUSPENDED`, `PENDING_FIRST_LOGIN`) ya definida en el backend y frontend.
* **Alternativas consideradas**:
  - *Incorporar el campo status en StaffUpdateRequest (PUT /api/v1/admin/staff/{id})*: Descartada porque acopla la modificacion de datos personales con la operacion critica de seguridad de baja laboral.
  - *Endpoint RPC POST /api/v1/admin/staff/{id}/suspend*: Descartada porque `PATCH` sobre el subrecurso `/status` es mas declarativo y permite tanto suspension como reactivacion con un unico contrato coherente.

### D-04: Inmutabilidad historica y gestion de sesiones concurrentes (Historia 3.1.3)

* **Decision**: La operacion de suspension muta exclusivamente el campo `users.status`. No se eliminan filas ni se ejecutan operaciones en cascada sobre `orders`, `order_items`, `kds_tickets` o registros de auditoria de ingredientes. La revocacion forzada de sesiones o tokens JWT preexistentes queda declarada fuera de alcance conforme a la seccion Fuera de alcance del spec.
* **Justificacion**:
  - Cumple estrictamente con FR-004, SC-006 y el invariante de inmutabilidad historica.
  - Las relaciones foraneas existentes en la base de datos poseen restricciones `ON DELETE RESTRICT`, garantizando la integridad referencial.
* **Alternativas consideradas**:
  - *Invalidar tokens forzadamente incrementando token_version*: Aunque la entidad `User` posee `tokenVersion`, el spec declara explicitamente fuera de alcance la revocacion forzada de tokens vivos; introducirlo violaria el Principio V de la Constitucion (Alcance acotado).

### D-05: Anclaje temporal de acreditacion de orden y persistencia (Historia 6.1.1)

* **Decision**: Incorporar la columna `paid_at TIMESTAMPTZ NULL` en la tabla `orders` mediante la migracion Flyway `V29__add_paid_at_to_orders.sql`, asignando `paid_at = Instant.now()` en `PaymentWebhookService` cuando la orden transiciona a `OrderStatus.PAID`. Para datos existentes, la migracion ejecuta un backfill poblando `paid_at` desde `payments.approved_at`. Se agrega un indice parcial compuesto `idx_orders_analytics` sobre `(establishment_id, paid_at)` para estados concretados.
* **Justificacion**:
  - El repositorio actual no poseia columna `paid_at` en `orders`; el instante solo residia en `payments.approved_at`.
  - En Domain-Driven Design, la orden es la raiz del agregado y su transicion a pagada representa un hito fundamental de su ciclo de vida, identico a `ready_at` y `completed_at` (agregado en `V28` para la spec 022).
  - Permite resolver las consultas del panel financiero y ranking de platos mediante un **Index Scan** directo en PostgreSQL con tiempos de respuesta inferiores a 2 milisegundos, sin necesidad de realizar joins con la tabla `payments`.
* **Alternativas consideradas**:
  - *JOIN dinamico con payments.approved_at*: Descartada porque anade sobrecarga en el planificador de consultas, requiere un triple join para el ranking de platos y acopla la analitica a la existencia obligatoria de una pasarela externa.

### D-06: Precision monetaria y representacion numerica (Historia 6.1.1)

* **Decision**: Utilizar `BigDecimal` en Java y `DECIMAL(12, 2)` en PostgreSQL para todos los acumuladores y respuestas monetarias, aplicando escala fija de 2 decimales y redondeo determinista `RoundingMode.HALF_UP`.
* **Justificacion**:
  - Sigue la representacion exacta de `Order.totalAmount` y `OrderItem.subtotal`.
  - Evita errores de perdida de precision binaria inherentes a tipos de punto flotante (`float`, `double`).
* **Alternativas consideradas**:
  - *Representacion en centavos mediante enteros (Long/BigInteger)*: Descartada porque el repositorio utiliza uniformemente `BigDecimal` en todas sus entidades y contratos.

### D-07: Huso horario y limites de granularidad (Historia 6.1.1)

* **Decision**: Resolver el huso horario a partir de `Establishment.timezone` (con valor por defecto `"America/Argentina/Buenos_Aires"` si es nulo o invalido). Los rangos se calculan de forma determinista:
  - **Dia**: Desde las `00:00:00.000` hasta las `23:59:59.999999999` de la fecha actual en el huso horario local (o formalmente `[inicio_dia, inicio_dia_siguiente)`).
  - **Semana**: Semana calendario ISO 8601 en curso, iniciando el lunes a las `00:00:00` y finalizando el domingo en el ultimo instante.
  - **Mes**: Desde las `00:00:00` del dia 1 del mes calendario actual hasta el ultimo instante del ultimo dia del mes.
* **Justificacion**:
  - Cumple con FR-007, el invariante 5 del spec y la resolucion de la sesion Grill-Me.
  - La delimitacion mediante semi-intervalos abiertos `[startInstant, endInstant)` previene de forma absoluta la perdida de pedidos en la transicion de medianoche (pedidos a las 23:59:59.999 pertenecen al dia en curso; pedidos a las 00:00:00.000 pertenecen al dia siguiente).
* **Alternativas consideradas**:
  - *Usar la zona horaria del servidor o UTC*: Descartada porque introduce desfases de hasta varias horas en el cierre del dia comercial respecto a la operacion real del restaurante.

### D-08: Definicion canonica de pedido concretado (Historias 6.1.1 y 6.2.1)

* **Decision**: Definir como pedido concretado a toda orden cuyo estado pertenezca al conjunto `OrderStatus.PAID`, `OrderStatus.IN_PROGRESS`, `OrderStatus.READY_FOR_PICKUP` o `OrderStatus.COMPLETED`. Esta definicion se encapsula en una constante / metodo de evaluacion comun para ser compartida por los servicios de salud financiera y ranking.
* **Justificacion**:
  - Cumple con FR-008, FR-009, FR-012 y el invariante 4.
  - Excluye categoricamente `PENDING_PAYMENT`, `PENDING_ACCREDITATION`, `CANCELLED` y `EXPIRED`.
* **Alternativas consideradas**:
  - *Replicar las listas de estados en consultas independientes*: Descartada para evitar divergencias futuras entre las metricas financieras y el reporte de unidades vendidas.

### D-09: Contrato del panel de salud financiera (Historia 6.1.1)

* **Decision**: Exponer el endpoint `GET /api/v1/admin/analytics/financial-health?granularity={day|week|month}` bajo autorizacion de rol `ADMINISTRADOR`. Retorna HTTP 200 OK con:
  ```json
  {
    "granularity": "day",
    "periodStart": "2026-11-20T00:00:00-03:00",
    "periodEnd": "2026-11-20T23:59:59.999999999-03:00",
    "revenue": 14250.00,
    "orderCount": 18
  }
  ```
  Si no existen pedidos en el rango, devuelve `"revenue": 0.00` y `"orderCount": 0`.
* **Justificacion**:
  - Cumple con FR-006, FR-007, FR-011 y SC-004.
  - Incluye `periodStart` y `periodEnd` con la representacion ISO 8601 del huso local para permitir a la interfaz mostrar al usuario los limites exactos calculados.
* **Alternativas consideradas**:
  - *Retornar arreglos de dias o series temporales*: Descartada porque el spec exige unicamente los agregados consolidados del periodo en curso.

### D-10: Conmutacion de granularidades sin acumuladores residuales (Historia 6.1.1)

* **Decision**: En el frontend, la seleccion de granularidad (`day`, `week`, `month`) actua como clave del estado. Al cambiar de pestaña, se invalida el estado anterior, se muestra un indicador de carga local y se actualiza el componente exclusivamente con la respuesta del nuevo llamado. El endpoint emite cabecera `Cache-Control: no-cache`.
* **Justificacion**:
  - Cumple con SC-003 y el edge case de conmutacion rapida, garantizando que no se mezclen importes ni se retengan valores desactualizados.
* **Alternativas consideradas**:
  - *Calcular las tres granularidades en una sola llamada y almacenarlas en memoria*: Descartada porque calcularia datos no solicitados y aumentaria la latencia innecesariamente.

### D-11: Agregacion de unidades por plato y colacion de desempate (Historia 6.2.1)

* **Decision**: El ranking calcula las unidades vendidas mediante `SUM(oi.quantity)` agrupado por `oi.dish_id, oi.dish_name` sobre pedidos con `o.status IN (...)`. El ordenamiento se ejecuta directamente en la consulta SQL/JPQL:
  ```sql
  ORDER BY SUM(oi.quantity) DESC, LOWER(oi.dish_name) ASC
  ```
* **Justificacion**:
  - Cumple con FR-013, FR-014 y el invariante 6.
  - Utilizar `LOWER(oi.dish_name) ASC` asegura un desempate determinista, case-insensitive y estable independiente de mayusculas y minusculas.
  - La agregacion se realiza sobre `order_items`, preservando el nombre comercial capturado al momento de la venta.
* **Alternativas consideradas**:
  - *Ordenar en memoria en la capa Java*: Descartada porque trasladar miles de filas a la aplicacion para ordenar degrada el rendimiento del backend frente a la capacidad nativa de PostgreSQL.

### D-12: Exclusion de platos con cero ventas (Historia 6.2.1)

* **Decision**: Omitir del ranking de mas vendidos los platos que tengan cero unidades vendidas en el periodo consultado. Solo se listan aquellos platos que registren al menos una venta acreditada (`unitsSold >= 1`). Si un establecimiento no registra ventas en el periodo, el backend retorna una lista vacia `[]` y la interfaz presenta un estado neutral descriptivo.
* **Justificacion**:
  - Cumple con FR-017, la aclaracion acordada en la sesion Grill-Me y el edge case de platos con cero ventas.
  - Evita cruzar costosamente con la tabla de catalogo de platos (`dishes`) mediante un `RIGHT JOIN` o subconsultas pesadas.
* **Alternativas consideradas**:
  - *Incluir todo el catalogo con valor cero al final*: Descartada porque saturaria el reporte con platos inactivos o sin traccion comercial, perdiendo el foco de los mas vendidos.

### D-13: Contrato del ranking de platos (Historia 6.2.1)

* **Decision**: Exponer el endpoint `GET /api/v1/admin/analytics/dish-ranking?granularity={all|day|week|month}` bajo autorizacion de rol `ADMINISTRADOR`. El valor por defecto del parametro es `all` (historico global acumulado). Retorna HTTP 200 OK con:
  ```json
  [
    {
      "dishId": "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f",
      "dishName": "Hamburguesa Clasica",
      "unitsSold": 142
    }
  ]
  ```
* **Justificacion**:
  - Cumple con FR-012, FR-015 y SC-005.
  - No expone datos monetarios ni precios de platos, preservando la separacion con el analisis contable conforme a la especificacion.
* **Alternativas consideradas**:
  - *Requerir rangos de fechas manuales (from / to)*: Descartada porque el spec define granularidades relativas alineadas al panel financiero, priorizando la vista global por defecto.

### D-14: Paquete y controladores en backend (Historias 6.1.1 y 6.2.1)

* **Decision**: Crear el paquete `com.easyeat.analytics` conteniendo:
  - `controller/AdminAnalyticsController.java`
  - `service/FinancialHealthService.java`
  - `service/DishRankingService.java`
  - `dto/FinancialHealthResponse.java`
  - `dto/DishRankingItemResponse.java`
  - `repository/AnalyticsRepository.java`
* **Justificacion**:
  - Mantiene el principio de alta cohesion y bajo acoplamiento (Principio IV de la Constitucion), aislando las consultas analiticas de lectura de los servicios transaccionales de operacion de pedidos (`OrderService`, `PaymentWebhookService`).
* **Alternativas consideradas**:
  - *Incrustar los metodos en OrderController o DishController*: Descartada porque sobrecarga controladores transaccionales con logica de lectura analitica.

### D-15: Arquitectura frontend y navegacion (Historias 3.1.3, 6.1.1 y 6.2.1)

* **Decision**:
  - **Historia 3.1.3**: Extender `StaffRosterTable.tsx` agregando la columna de accion "Suspender / Activar" con un modal de confirmacion explicito que advierte sobre la suspension y captura posibles errores de orfandad administrativa (409 Conflict).
  - **Historias 6.1.1 y 6.2.1**: Crear la pagina unificada `AdminAnalyticsPage.tsx` en la ruta `/admin/analytics`, enlazada desde una nueva tarjeta en `AdminWorkspacePage` titulada *"Metricas y Rendimiento"*. La pagina presenta en la parte superior el selector de granularidad, las tarjetas de ingresos y pedidos y el grafico visual interactivo `FinancialHealthChart.tsx` (utilizando Recharts para ingresos y pedidos), y en la seccion inferior la tabla de ranking de platos con su propio selector de alcance (historico global vs mismo periodo).
* **Justificacion**:
  - Sigue los patrones existentes de `WorkspaceLayout`, TailwindCSS y Axios.
  - Ofrece al administrador un centro consolidado de inteligencia comercial en una unica pantalla, simplificando la navegacion y evitando dispersar informacion.
* **Alternativas consideradas**:
  - *Dividir en dos paginas separadas (/admin/finanzas y /admin/reportes/platos)*: Descartada porque fragmenta la experiencia del administrador para dos reportes complementarios.