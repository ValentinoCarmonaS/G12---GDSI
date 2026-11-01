# Phase 0 Research: Restaurant Operation Configuration

**Feature**: `010-restaurant-operation-config` | **Date**: 31/10/2026 | **Spec**: [spec.md](./spec.md)

---

## 1. Baseline Verification & Repo Alignment

Se inspeccionó el repositorio para validar las premisas tecnológicas y reutilizar patrones preexistentes:

| Dimensión | Evidencia en el Repositorio | Decisión de Ingeniería |
|---|---|---|
| **Backend Stack** | `easyeat/backend/pom.xml`: Java 21, Spring Boot 3.3.4, Spring Data JPA, Spring Security, Flyway, PostgreSQL Driver 42.7.4, Lombok. | Desarrollar sobre el ecosistema Java 21 / Spring Boot 3.3.4 existente utilizando Java Records para DTOs inmutables. |
| **Frontend Stack** | `easyeat/frontend/package.json`: React 18.3.1, TypeScript 5.6, Vite 5, Tailwind CSS, Axios, Lucide React. | Extender vistas de administración (`/admin`) y selector/checkout de comensal (`/catalog`, `/checkout`) en React 18 con Axios (`apiClient.ts`). |
| **Manejo de Zonas Horarias** | `Establishment.java` ya posee campo `timezone VARCHAR(50) DEFAULT 'America/Argentina/Buenos_Aires'` y métodos de dominio `isOpen(LocalTime now)` y `isOpen()` empleando `java.time.ZoneId` y `java.time.LocalTime`. | **Autoridad horaria exclusiva en servidor**: Todas las evaluaciones temporales se computan contra el reloj del servidor proyectado al `ZoneId` del establecimiento comercial (`Establishment.timezone`). Se ignora por completo la zona horaria reportada por el navegador del cliente. |
| **Multi-Tenancy** | `TenantContextHolder.java` (ThreadLocal con UUID de establecimiento) y `TenantContextFilter.java`. | Aislamiento multi-tenant nativo por `establishment_id` en todas las consultas y mutaciones de configuración y disponibilidad. |
| **Manejo de Errores** | `GlobalExceptionHandler.java` y `ProblemDetail` (RFC 7807, `application/problem+json`). | Usar `ProblemDetail` para todas las respuestas de error de validación (400 Bad Request, 403 Forbidden, 404 Not Found, 409 Conflict). |
| **Migraciones Flyway** | `db/migration/V1` a `V9` presentes en `easyeat/backend/src/main/resources/db/migration/`. | La siguiente migración libre en el repositorio es `V10__restaurant_operation_config.sql`. |

---

## 2. Decisiones Técnicas Fundamentales

### D-001 — Autoridad Horaria del Servidor y Cálculo de Zonas Horarias
- **Decisión**: El servidor es la única fuente de verdad temporal. Toda validación de horarios comerciales y franjas se ejecuta utilizando `ZonedDateTime.now(ZoneId.of(establishment.getTimezone()))` (o `Clock` inyectado para testing determinístico).
- **Justificación**: Cumple con `A-008`, `EC-014` y `EC-018`. La hora del dispositivo cliente (navegador o teléfono) puede estar desfasada o en otra zona geográfica; depender del cliente violaría el criterio de cierre automático y admitiría pedidos inválidos.
- **Alternativas descartadas**:
  - *Confiar en timestamp enviado por el cliente*: Descartado por vulnerabilidad de seguridad e inconsistencia de huso horario.
  - *Almacenar horas en UTC simple sin zona horaria*: Descartado porque los horarios comerciales son fijos en la hora local del restaurante (ej. abre a las 11:00 hora de Buenos Aires todo el año, independientemente de desfasajes solares).

### D-002 — Modelo de Partición de Franjas y Capacidad por Franja (Persistido vs. Derivado)
- **Decisión**: Separar la definición de plantilla de capacidad horaria de la instancia operativa diaria de franjas:
  1. `establishment_operating_schedules`: define el régimen semanal (día de la semana, apertura, cierre, cerrado).
  2. `establishment_slot_capacities`: persiste para cada fecha y bloque de 15 minutos `(establishment_id, slot_date, start_time, end_time)` la `max_capacity` y el contador acumulador `approved_orders_count`.
  3. El estado de la franja se deriva matemáticamente: `isSoldOut = (approved_orders_count >= max_capacity)`.
- **Justificación**: Satisface `INV-2`, `INV-3`, `INV-5`, `FR-005`, `FR-008`, `FR-009`, `AC-2.1`. Permite consultas de disponibilidad ultra-rápidas ($\le 50\text{ ms}$) sin ejecutar `COUNT(*)` costosos sobre millones de órdenes históricas cada vez que un comensal abre el selector.
- **Alternativas descartadas**:
  - *Calcular cupos mediante `SELECT COUNT(*)` en tiempo real sobre órdenes*: Descartado por degradación de rendimiento bajo concurrencia alta y riesgo de lecturas inconsistentes (N+1 queries).
  - *Capacidad única global sin diferenciación de bloques*: Descartado por violar la historia `ADM-2.2.2` que exige control por bloque de 15 minutos.

### D-003 — Atomicidad en la Aprobación que Completa Cupo (Control de Concurrencia en Capacidad)
- **Decisión**: Para la aprobación de pedidos que consumen cupo en una franja, se aplica una actualización condicional atómica a nivel de base de datos:
  ```sql
  UPDATE establishment_slot_capacities
  SET approved_orders_count = approved_orders_count + 1,
      version = version + 1,
      updated_at = NOW()
  WHERE id = :slotId
    AND approved_orders_count < max_capacity;
  ```
- **Justificación**: Resuelve de raíz el caso borde `EC-016` (dos aprobaciones simultáneas para el cupo 50). La condición `approved_orders_count < max_capacity` en el `WHERE` garantiza que solo una transacción logre incrementar el contador; la segunda obtiene 0 filas afectadas, detectando de inmediato la saturación de la franja y lanzando un `SlotCapacityExceededException` (mapeado a HTTP 409 Conflict).
- **Alternativas descartadas**:
  - *Lock pesimista `SELECT ... FOR UPDATE`*: Descartado por riesgo de contención excesiva y bloqueos en cocina bajo picos de demanda.
  - *Chequeo previo en aplicación sin lock*: Descartado por condición de carrera (race condition) clásica donde ambas transacciones leen 49 y ambas escriben 50, permitiendo 51 aprobaciones.

### D-004 — No Retroactividad de Cambios de Configuración (INV-8)
- **Decisión**: Cuando el Administrador actualiza la capacidad máxima (`max_capacity`) o adelanta el cierre del local, la modificación rige exclusivamente para nuevos intentos de reserva/pedido:
  - Si `max_capacity` se reduce a 30 con 45 pedidos aprobados, la franja transiciona inmediatamente a "Agotada" (`45 >= 30`), pero los 45 pedidos conservan su estado `APPROVED` sin ser cancelados ni desaprobados.
  - Si el horario de cierre se adelanta a las 15:00 mientras hay pedidos en estado pendiente ingresados antes de las 15:00, dichos pedidos pueden ser evaluados y aprobados normalmente.
- **Justificación**: Satisface estrictamente `INV-8`, `EC-003`, `EC-004`, `EC-005` y `SC-011`. Previene cancelaciones involuntarias o inconsistencias financieras en órdenes ya comprometidas por el restaurante.
- **Alternativas descartadas**:
  - *Cancelar pedidos aprobados excedentes de forma retroactiva*: Descartado por violar las reglas de negocio y generar pérdidas operativas injustificadas.

### D-005 — Política de Valores por Defecto (Capacidad 0 y Día Cerrado)
- **Decisión**:
  - Si un día de la semana no posee registro en `establishment_operating_schedules`, el sistema lo interpreta como `is_closed = true` (`EC-008`, `FR-016`).
  - Si una franja horaria dentro del horario operativo no tiene configuración explícita de capacidad, el sistema asume `max_capacity = 0` (`EC-007`, `FR-015`), presentándola como "Agotada" en el selector.
- **Justificación**: Principio de resguardo operativo de la cocina: ninguna orden puede ingresar a la cocina en días u horarios no supervisados por el Administrador.
- **Alternativas descartadas**:
  - *Asumir capacidad infinita*: Descartado por violar `ADM-2.2.2` y saturar la cocina.
  - *Rechazar el inicio del sistema si falta configuración*: Descartado por excesiva rigidez operativa.

### D-006 — Métodos de Pago a Costo Cero: Desacoplamiento y Revalidación Dinámica
- **Decisión**:
  - Se modela un catálogo cerrado de métodos de pago (`PaymentMethod` enum: `MERCADO_PAGO`, `STRIPE`).
  - La tabla `establishment_payment_methods` almacena un registro por `(establishment_id, payment_method)` con la columna booleana `is_enabled`.
  - El checkout del comensal consulta únicamente los métodos con `is_enabled = true` (`INV-4`).
  - **Revalidación dinámica en submit (`INV-9`)**: Al procesar la orden en checkout, el backend revalida que el método seleccionado continúe con `is_enabled = true`. Si el Administrador lo apagó entre la visualización y el click de pago (`EC-010`), se rechaza la operación con código `409 Conflict` (o `400 Bad Request`) y detalle explicativo RFC 7807.
  - **Todos deshabilitados (`EC-009`, `FR-013`)**: Si la lista de métodos habilitados es vacía, el checkout bloquea el botón de pago y despliega un mensaje informativo.
  - **Costo Cero / Offline (`A-005`)**: No se consumen SDKs ni APIs externas reales de MercadoPago o Stripe; todo se comprueba mediante aserciones booleanas locales.
- **Justificación**: Resuelve las historias `ADM-2.2.3`, `AC-3.1`, las directrices de costo cero acordadas con el Product Owner y simplifica el testing automatizado.

### D-007 — Concurrencia entre Múltiples Administradores (EC-012)
- **Decisión**: Se implementa control de concurrencia optimista mediante columna `@Version private Long version` en las entidades de configuración (`EstablishmentOperatingSchedule`, `EstablishmentSlotCapacity`, `EstablishmentPaymentMethod`).
- **Justificación**: Si el Administrador A y el Administrador B abren el panel simultáneamente y ambos intentan guardar horarios o capacidades distintas, el segundo en confirmar recibe un `OptimisticLockingFailureException` (transformado a HTTP 409 Conflict con mensaje descriptivo RFC 7807), impidiendo sobreescrituras silenciosas.

### D-008 — Propagación Inmediata de Cambios de Configuración al Comensal (SC-006, FR-017)
- **Decisión**: La consulta de disponibilidad de franjas y métodos de pago en el frontend del comensal no se almacena en caché estática de sesión a largo plazo. Cada apertura del selector de franjas o navegación al checkout realiza una petición GET al backend para obtener el estado vivo.
- **Justificación**: Satisface `SC-006` ($\le 2\text{ s}$ para reflejar cambios) sin necesidad de introducir tecnologías complejas y no presentes en el repo como WebSockets o SSE, preservando la simplicidad arquitectónica (KISS, Principio IV).

