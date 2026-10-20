# Investigación Técnica y Registro de Decisiones (Phase 0)

**Característica**: Gestión de Personal, RBAC e Inventario Inicial de Ingredientes  
**Rama**: `004-staff-mgmt-rbac-inventory`  
**Fecha**: 2026-10-20  
**Estado**: Completado  

---

## Resumen Ejecutivo

El presente documento consolida la investigación técnica y las decisiones de diseño para la implementación del módulo de personal, control de acceso basado en roles (RBAC), aislamiento multi-tenant y el catálogo de ingredientes con ajustes de stock y trazabilidad inmutable.

El diseño se apoya y extiende las bases tecnológicas definidas en la iteración 001 (`central-auth-recovery`):
- **Lenguaje y Entorno**: Java 21 (LTS).
- **Framework Principal**: Spring Boot 3.3.4 con Spring Security 6.3.
- **Persistencia**: PostgreSQL 16 con Flyway para migraciones evolutivas y Spring Data JPA.
- **Autenticación**: JSON Web Tokens (JJWT 0.12.6) con claims criptográficos de identidad, rol y `tenant_id`.
- **Estrategia de Pruebas**: JUnit 5, Mockito y Testcontainers con PostgreSQL real para pruebas de integración con aislamiento transaccional.

---

## Registro de Decisiones de Arquitectura e Implementación

### D-001 · Estructura Modular y Paquetes de Backend

- **Decisión**: Organizar los nuevos componentes en dos paquetes de dominio vertical dentro de `easyeat/backend/src/main/java/com/easyeat/`:
  1. `com.easyeat.staff`: Gestión de empleados, asignación de roles y control de credenciales iniciales.
  2. `com.easyeat.inventory`: Catálogo de ingredientes, ciclo de vida de insumos, ajustes de stock y auditoría inmutable.
- **Racionalidad**: Mantiene la alta cohesión por contexto delimitado (Bounded Context), desacoplando la gestión administrativa del personal respecto a la operatoria de insumos de cocina, a la vez que reutiliza `com.easyeat.auth` para la infraestructura central de seguridad y emisión de tokens.
- **Alternativas evaluadas**:
  - *Monolito por capas (`controllers`, `services`, `repositories` globales)*: Rechazado por diluir la cohesión de dominio y dificultar el mantenimiento a medida que crecen las historias de usuario.
  - *Módulos Maven separados (`staff-service`, `inventory-service`)*: Rechazado para el alcance del MVP; introduce sobrecarga de compilación y despliegue sin justificación operativa actual.

---

### D-002 · Control de Acceso (RBAC) y Aislamiento Multi-Tenant

- **Decisión**:
  1. Utilizar un filtro de contexto `TenantContextFilter` para extraer y validar el claim `tenant_id` del token JWT verificado, almacenándolo en un `TenantContextHolder` respaldado por `ThreadLocal`.
  2. Aplicar seguridad declarativa de métodos mediante `@PreAuthorize("hasRole('ADMIN')")`, `@PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")`, etc., habilitada vía `@EnableMethodSecurity`.
  3. Aplicar filtrado obligatorio de `tenant_id` en todas las consultas y mutaciones de persistencia (`UserRepository`, `IngredientRepository`, `StockAuditEntryRepository`).
  4. En caso de acceso no autorizado (violación de rol o intento de cruce de tenant), emitir respuesta HTTP `403 Forbidden` estructurada según RFC 9457 (`ProblemDetail`) y registrar un evento de auditoría de seguridad a través de SLF4J (`SecurityAuditLogger`).
- **Racionalidad**: Garantiza aislamiento estricto tanto en la capa de transporte como en la de aplicación y datos. Los tokens son firmados criptográficamente; un cliente no puede falsificar su `tenant_id`. La inyección obligatoria del tenant en las consultas SQL previene cualquier fuga accidental de datos entre locales gastronómicos.
- **Alternativas evaluadas**:
  - *Row-Level Security (RLS) en PostgreSQL*: Muy potente, pero incrementa la complejidad del pool de conexiones (requiere fijar variables de sesión por conexión en HikariCP) y dificulta las pruebas unitarias y de integración livianas en el MVP.

---

### D-003 · Concurrencia y Bloqueo Pesimista en Ajustes de Stock

- **Decisión**: Emplear bloqueo pesimista de escritura (`SELECT ... FOR UPDATE`) en la consulta de lectura del ingrediente previo a cualquier ajuste de stock:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Ingredient i WHERE i.id = :id AND i.tenantId = :tenantId")
  Optional<Ingredient> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
  ```
- **Racionalidad**: El requerimiento FR-016 y FR-020 exige garantizar que el stock resultante nunca sea negativo (`resulting_stock >= 0`) y que dos ajustes concurrentes sobre el mismo ingrediente no generen condiciones de carrera ni pérdidas de actualización (lost updates). El bloqueo pesimista serializa a nivel de base de datos la fila del ingrediente durante la transacción `@Transactional`, calculando el nuevo balance y grabando la auditoría de forma atómica.
- **Alternativas evaluadas**:
  - *Bloqueo optimista (`@Version`)*: Si dos administradores ajustan el stock simultáneamente, uno de ellos recibiría una excepción `OptimisticLockException` y su operación fallaría, requiriendo reintentos y potencialmente confundiendo al usuario en conteos físicos. El bloqueo pesimista garantiza ejecución determinista.

---

### D-004 · Precisión Numérica Exacta para Cantidades Físicas y Costos

- **Decisión**:
  1. En Java: Utilizar `BigDecimal` para cantidades físicas continuas y costos monetarios.
  2. En PostgreSQL: Definir tipos de columna `NUMERIC(12, 4)` para cantidades de stock y deltas, y `NUMERIC(12, 4)` para costos de compra unitarios.
  3. Para la unidad discreta unidad (`u`): Validar en la capa de servicios que la escala efectiva sea cero o que el valor represente un número entero (ej. `value.stripTrailingZeros().scale() <= 0`).
  4. Para unidades continuas (`g`, `kg`, `ml`, `l`): Aceptar y almacenar valores decimales con escala de hasta 3 decimales para cantidades físicas (ej. 1.750 kg, 250.500 g).
- **Racionalidad**: Cumple estrictamente con el principio de exactitud contable e inventario (FR-025), eliminando cualquier error de redondeo inherente a la aritmética de coma flotante binaria (`double` o `float`).
- **Alternativas evaluadas**:
  - *Almacenamiento en enteros mínimos (ej. miligramos o centavos)*: Aumenta la complejidad de conversión en las capas de presentación y consulta, y dificulta la lectura directa de auditorías en base de datos. `NUMERIC(12, 4)` ofrece precisión absoluta nativa.

---

### D-005 · Provisión de Credencial Temporal y Flujo de Primer Acceso

- **Decisión**:
  1. Generar una credencial aleatoria criptográficamente segura de 12 caracteres (incluyendo mayúsculas, minúsculas, dígitos y caracteres especiales) mediante `SecureRandom`.
  2. Almacenar el hash de dicha clave mediante `BCryptPasswordEncoder` (costo 12).
  3. Crear al empleado en la tabla `users` con estado `PENDING_FIRST_LOGIN` (o booleano `must_change_password = true`).
  4. Retornar la clave en texto plano únicamente en el cuerpo de la respuesta HTTP 201 Created al Administrador que dio el alta, para su visualización única con acción de copiado.
  5. La credencial no expira por paso del tiempo; permanece activa hasta el primer inicio de sesión o hasta que el Administrador reemita la credencial.
  6. En el primer inicio de sesión exitoso, el sistema detecta `must_change_password = true` y bloquea cualquier otra operación de la plataforma, forzando la invocación al endpoint de cambio de clave inicial (`POST /api/v1/auth/first-login-password-change`), actualizando el hash y pasando el usuario a estado `ACTIVE`.
- **Racionalidad**: Cumple al 100% las decisiones de clarificación (Sesión 2026-10-20) y los requisitos FR-003 y FR-004, protegiendo las credenciales contra filtraciones en logs o persistencia insegura.
- **Alternativas evaluadas**:
  - *Envío por correo electrónico automático*: Descartado por el usuario en la sesión de clarificación en favor de la visualización en pantalla con copia al portapapeles.

---

### D-006 · Prevención de Administrador Huérfano y Auto-Degradación

- **Decisión**: En las operaciones de edición de perfil (`PUT /api/v1/admin/staff/{id}`) y cambio de rol:
  1. Si se intenta modificar el rol de un usuario con rol `ADMIN` hacia otro rol (`CHEF` o `COUNTER_CLERK`):
  2. Ejecutar una consulta de verificación: `SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId AND u.role = 'ADMIN' AND u.status = 'ACTIVE' AND u.id != :targetUserId`.
  3. Si la cuenta de administradores restantes es 0, abortar la transacción y responder HTTP `409 Conflict` (o `422 Unprocessable Entity`) con el mensaje: "No es posible degradar al único Administrador activo del establecimiento".
- **Racionalidad**: Garantiza el invariante de gobernanza FR-006, evitando que un local quede bloqueado administrativamente sin usuarios con privilegios de gestión.
- **Alternativas evaluadas**:
  - *Bloqueo únicamente en el frontend*: Rechazado; la regla de negocio debe ser inviolable a nivel de backend.

---

### D-007 · Máquina de Estados y Ciclo de Vida del Ingrediente

- **Decisión**: Modelar el ciclo de vida del ingrediente mediante tres estados formales:
  - `ACTIVE`: Ingrediente disponible. Visible para el Administrador en la gestión de inventario y para el Chef en el catálogo de cocina. Admite ajustes de stock.
  - `DISCONTINUED`: Ingrediente dado de baja operativa pero con stock remanente mayor a cero. Queda oculto del catálogo del Chef. Sigue visible para el Administrador y **admite ajustes de stock** (ej. mermas, descarte físico) hasta que el stock llegue a 0. Puede ser reactivado a `ACTIVE` por el Administrador.
  - `ARCHIVED`: Archivo permanente. Solo permitido cuando `current_stock == 0`. Queda fuera de las vistas operativas de catálogo y recetas. No admite nuevos ajustes.
- **Transiciones autorizadas**:
  - `ACTIVE` → `DISCONTINUED`: Permitido si `current_stock > 0`.
  - `ACTIVE` → `ARCHIVED`: Permitido únicamente si `current_stock == 0`.
  - `DISCONTINUED` → `ACTIVE`: Permitido en cualquier momento por decisión del Administrador (restaura visibilidad al Chef).
  - `DISCONTINUED` → `ARCHIVED`: Permitido únicamente cuando `current_stock == 0`.
- **Restricción de cambio de unidad de medida**: Solo se permite cambiar la unidad de medida si `current_stock == 0` y no existen ajustes históricos que entren en conflicto conceptual (FR-013).
- **Racionalidad**: Modela con total fidelidad la realidad operativa de una cocina profesional y las decisiones de la sesión de clarificación.
- **Alternativas evaluadas**:
  - *Borrado físico (`DELETE` en SQL)*: Inadmisible; violaría la integridad referencial histórica y la trazabilidad de auditoría.

---

### D-008 · Paginación y Proyecciones Seguras en el Catálogo del Chef

- **Decisión**:
  1. Crear un endpoint exclusivo para cocina: `GET /api/v1/chef/ingredients`.
  2. Parámetros de consulta: `page` (default 0), `size` (default 20, FR-021), `category` (opcional), `search` (opcional, búsqueda case-insensitive por nombre).
  3. Ordenamiento obligatorio por defecto: Alfabético ascendente por nombre (`ORDER BY LOWER(name) ASC`).
  4. Proyección de DTO (`ChefIngredientResponseDto`):
     - `id`: UUID.
     - `name`: String.
     - `category`: String.
     - `unitOfMeasure`: String.
     - `stockStatus`: String (`IN_STOCK` si `current_stock > 0`, `OUT_OF_STOCK` si `current_stock == 0`).
  5. **Omisión deliberada de campos sensibles**: El DTO no expone ni `unitPurchaseCost` ni el historial de auditoría de ajustes, cumpliendo estrictamente con FR-024.
- **Racionalidad**: Optimiza el consumo de red mediante paginación indexada y garantiza el principio de mínimo privilegio en la visualización de información de costos de la empresa.
- **Alternativas evaluadas**:
  - *Reutilizar el endpoint del Administrador filtrando en cliente*: Violación flagrante de seguridad; la API filtraría datos financieros al cliente web del Chef.

---

### D-009 · Inmutabilidad y Trazabilidad del Registro de Auditoría de Stock

- **Decisión**:
  1. Tabla `stock_audit_entries` gestionada exclusivamente como append-only (solo `INSERT`).
  2. No existen métodos de actualización (`UPDATE`) ni de borrado (`DELETE`) en el repositorio JPA ni en los endpoints de la API.
  3. Regla de base de datos o trigger opcional: Denegar comandos `UPDATE` o `DELETE` sobre la tabla `stock_audit_entries` a nivel de DDL/permisos de PostgreSQL para máxima garantía de inmutabilidad (FR-019).
  4. Los campos almacenados son: `id`, `tenant_id`, `ingredient_id`, `user_id` (del administrador que ejecutó el ajuste), `adjustment_mode` (`ABSOLUTE` o `RELATIVE`), `previous_stock`, `new_stock`, `net_delta`, `justification`, y `created_at`.
- **Racionalidad**: Garantiza evidencia forense inalterable requerida para auditorías contables y control de mermas gastronómicas.
- **Alternativas evaluadas**:
  - *Almacenar solo el balance acumulado en la fila del ingrediente*: Rechazado; destruye la trazabilidad y la justificación requerida por FR-018.

---

## Matriz de Cobertura Tecnológica

| Componente | Elección Tecnológica | Justificación |
|---|---|---|
| Plataforma de Ejecución | Java 21 / Spring Boot 3.3.4 | Continuidad de iteración 001, soporte LTS de largo plazo |
| Seguridad | Spring Security 6.3 + JJWT 0.12.6 | Validación criptográfica de claims y RBAC declarativo |
| Motor de Persistencia | PostgreSQL 16 + Hibernate ORM 6.5 | Tipos `NUMERIC` exactos, UUID nativos y bloqueo pesimista |
| Migraciones | Flyway 10.x | Evolución versionada y determinista del esquema SQL |
| Aislamiento Multi-Tenant | `TenantContextHolder` (ThreadLocal) + Filter | Inyección transversal de `tenant_id` en capa de servicio y datos |
| Testing Automatizado | JUnit 5 + Mockito + Testcontainers (PostgreSQL) | Validación de concurrencia y restricciones relacionales en DB real |
