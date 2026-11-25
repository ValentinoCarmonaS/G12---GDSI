# Guia de validacion rapida (Quickstart): control de acceso de personal, salud financiera y ranking de platos

**Rama**: `023-staff-access-bi-dish-ranking` | **Fecha**: 20/11/2026 | **Especificacion**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

---

## 1. Proposito y alcance

Esta guia establece el procedimiento reproducible paso a paso para verificar el comportamiento observable de las tres historias de usuario de la especificacion 023:
1. Historia 3.1.3: Baja y suspension de acceso de personal con proteccion de orfandad administrativa y rechazo generico en login.
2. Historia 6.1.1: Panel de salud financiera con agregaciones por dia, semana calendario y mes calendario.
3. Historia 6.2.1: Ranking de platos mas vendidos ordenado descendentemente con desempate alfabetico A-Z.

Para detalles de esquemas y contratos, consultar [data-model.md](./data-model.md) y los contratos en [contracts/](./contracts/).

---

## 2. Prerrequisitos y configuracion del entorno

1. **Servicios backend**:
   - PostgreSQL en ejecucion en el puerto 5432 con extension `pgcrypto`.
   - Backend EasyEat ejecutando con perfil de pruebas o desarrollo (`./mvnw spring-boot:run` o suite de integracion).
   - Migracion Flyway `V29__add_paid_at_to_orders.sql` aplicada exitosamente.
2. **Servicios frontend**:
   - Dependencias instaladas y servidor de desarrollo activo (`npm run dev` en `easyeat/frontend`).
3. **Variables y credenciales de prueba**:
   - `BASE_URL`: `http://localhost:8080/api/v1`
   - Tenant / Establecimiento de prueba: `7c9e6679-7425-40de-944b-e07fc1f90ae7` (Zona horaria: `America/Argentina/Buenos_Aires`).
   - Admin principal: `admin1@easyeat.com` / `AdminPass123!`
   - Empleado operativo de prueba: `cocinero1@easyeat.com` / `ChefPass123!` (Rol: `CHEF`).

---

## 3. Escenarios de validacion reproducible

### Escenario A: Inicio de sesion de cuenta activa

1. **Accion**: Enviar peticion de login con credenciales validas de `cocinero1@easyeat.com`.
   ```bash
   curl -s -X POST "$BASE_URL/auth/login" \
     -H "Content-Type: application/json" \
     -d '{"email":"cocinero1@easyeat.com","password":"ChefPass123!"}'
   ```
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - Cuerpo con token JWT emitido y rol `CHEF`.

---

### Escenario B: Administrador suspende cuenta de personal

1. **Accion**: Con el token de `admin1@easyeat.com`, suspender la cuenta de `cocinero1@easyeat.com` (referencia: [01-staff-access-status.md](./contracts/01-staff-access-status.md)).
   ```bash
   curl -s -X PATCH "$BASE_URL/admin/staff/<ID_COCINERO>/status" \
     -H "Authorization: Bearer $JWT_ADMIN" \
     -H "Content-Type: application/json" \
     -d '{"status":"SUSPENDED"}'
   ```
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - `status` actualizado a `"SUSPENDED"`.

---

### Escenario C: Intento de login de cuenta suspendida con contrasena correcta

1. **Accion**: Intentar autenticar nuevamente con `cocinero1@easyeat.com` ingresando su contrasena valida (`ChefPass123!`).
   ```bash
   curl -s -X POST "$BASE_URL/auth/login" \
     -H "Content-Type: application/json" \
     -d '{"email":"cocinero1@easyeat.com","password":"ChefPass123!"}'
   ```
2. **Resultado esperado**:
   - Codigo `401 Unauthorized`.
   - Mensaje generico `"The provided email or password is incorrect."` (100% indistinguible de contrasena erronea, FR-003, SC-001).
   - Ninguna referencia a cuenta suspendida en la respuesta HTTP.

---

### Escenario D: Bloqueo de orfandad administrativa (unico administrador activo)

1. **Accion**: Estando `admin1@easyeat.com` como el unico administrador en estado `ACTIVE` del establecimiento, intentar suspender su cuenta.
   ```bash
   curl -s -X PATCH "$BASE_URL/admin/staff/<ID_ADMIN1>/status" \
     -H "Authorization: Bearer $JWT_ADMIN" \
     -H "Content-Type: application/json" \
     -d '{"status":"SUSPENDED"}'
   ```
2. **Resultado esperado**:
   - Codigo `409 Conflict`.
   - `type`: `https://easyeat.com/errors/orphaned-admin`.
   - Mensaje indicando la imposibilidad de suspender al unico administrador activo del establecimiento (FR-005, SC-002).
   - El estado de la cuenta se mantiene en `ACTIVE`.

---

### Escenario E: Suspension con dos administradores activos

1. **Accion**: Registrar y activar un segundo administrador (`admin2@easyeat.com`). Luego, ejecutar la suspension de `admin2` desde la sesion de `admin1`.
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - Como queda `admin1` activo (conteo > 1 al momento de iniciar la transaccion), la operacion se autoriza y `admin2` pasa a `SUSPENDED`.

---

### Escenario F: Preservacion de auditoria historica

1. **Accion**: Consultar las comandas de cocina (`KdsTicket`), pedidos despachados o auditoria de stock previamente creados o manipulados por `cocinero1`.
2. **Resultado esperado**:
   - Todos los registros conservan `user_id` o `created_by` apuntando a `cocinero1`.
   - Ningun pedido o comanda es eliminado ni anonimizado tras la suspension (FR-004, SC-006).

---

### Escenario G: Panel financiero con granularidad diaria (por defecto)

1. **Accion**: Consultar el panel financiero sin parametros (referencia: [02-financial-health.md](./contracts/02-financial-health.md)).
   ```bash
   curl -s -X GET "$BASE_URL/admin/analytics/financial-health" \
     -H "Authorization: Bearer $JWT_ADMIN"
   ```
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - `"granularity": "day"`.
   - `revenue` y `orderCount` coinciden exactamente con la suma monetaria y conteo de pedidos con `status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')` y `paid_at` dentro del dia actual (00:00:00 a 23:59:59 huso local) (FR-006, SC-004).

---

### Escenario H: Conmutacion a granularidad semanal

1. **Accion**: Consultar con `granularity=week`.
   ```bash
   curl -s -X GET "$BASE_URL/admin/analytics/financial-health?granularity=week" \
     -H "Authorization: Bearer $JWT_ADMIN"
   ```
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - Recalcula los acumuladores sobre la semana calendario ISO 8601 en curso (lunes a domingo).

---

### Escenario I: Conmutacion a granularidad mensual

1. **Accion**: Consultar con `granularity=month`.
   ```bash
   curl -s -X GET "$BASE_URL/admin/analytics/financial-health?granularity=month" \
     -H "Authorization: Bearer $JWT_ADMIN"
   ```
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - Recalcula sobre el mes calendario en curso (dia 1 al ultimo instante del mes).

---

### Escenario J: Exclusion estricta de pedidos no concretados

1. **Accion**: Registrar pedidos en estados `CANCELLED`, `EXPIRED`, `PENDING_PAYMENT` y `PENDING_ACCREDITATION` con fecha y hora dentro del periodo consultado.
2. **Resultado esperado**:
   - Las metricas del panel financiero excluyen absolutamente estos pedidos tanto del importe monetario como del conteo (FR-009).

---

### Escenario K: Estado vacio en panel financiero

1. **Accion**: Consultar el panel para un establecimiento sin ventas registradas en el periodo.
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - `"revenue": 0.00` y `"orderCount": 0` (FR-011).
   - La interfaz muestra `$0,00` y `0 pedidos` sin errores.

---

### Escenario L: Transicion de medianoche

1. **Accion**: Simular dos pedidos concretados:
   - Pedido 1: `paid_at = 2026-11-20T23:59:59-03:00`
   - Pedido 2: `paid_at = 2026-11-21T00:00:00-03:00`
2. **Resultado esperado**:
   - Al consultar el panel del dia `20/11/2026`, se incluye el Pedido 1 y se excluye el Pedido 2.
   - Al consultar el dia `21/11/2026`, se incluye el Pedido 2 y el Pedido 1 queda en el historial del dia previo.

---

### Escenario M: Conmutacion rapida de granularidades en interfaz

1. **Accion**: En `AdminAnalyticsPage`, alternar consecutivamente y con rapidez entre las pestañas "Dia", "Semana" y "Mes".
2. **Resultado esperado**:
   - Cada vista actualiza sus cifras en menos de 2 segundos (SC-003).
   - No se conservan cifras residuales ni se mezclan los acumuladores de una vista con otra.

---

### Escenario N: Ranking de platos por defecto (historico global)

1. **Accion**: Consultar el ranking de platos sin parametros (referencia: [03-dish-ranking.md](./contracts/03-dish-ranking.md)).
   ```bash
   curl -s -X GET "$BASE_URL/admin/analytics/dish-ranking" \
     -H "Authorization: Bearer $JWT_ADMIN"
   ```
2. **Resultado esperado**:
   - Codigo `200 OK`.
   - Listado ordenado de forma estrictamente descendente segun `unitsSold` sobre todas las ordenes con pago acreditado (FR-012, FR-013).

---

### Escenario O: Desempate alfabetico A-Z determinista

1. **Accion**: Generar ventas donde "Empanada de Carne" y "Bebida Cola" registren exactamente 10 unidades vendidas cada una.
2. **Resultado esperado**:
   - En el ranking, "Bebida Cola" figura antes que "Empanada de Carne" debido al ordenamiento secundario alfabetico ascendente A-Z (FR-014, SC-005).

---

### Escenario P: Actualizacion del ranking ante nueva venta acreditada

1. **Accion**: Confirmar el pago de un nuevo pedido que contiene 5 unidades de "Pizza Muzzarella" y refrescar la consulta del ranking.
2. **Resultado esperado**:
   - El ranking refleja de inmediato las 5 nuevas unidades consolidadas (FR-016).

---

### Escenario Q: Estado vacio en ranking de platos

1. **Accion**: Consultar el ranking en un establecimiento sin ventas registradas.
2. **Resultado esperado**:
   - Codigo `200 OK` con arreglo vacio `[]` (FR-017).
   - La interfaz renderiza el mensaje neutral descriptivo *"No se registraron ventas de platos disponibles"*.

---

### Escenario R: Exclusion de platos con cero unidades vendidas

1. **Accion**: Contar con platos dados de alta en el catalogo que nunca fueron comprados en pedidos concretados.
2. **Resultado esperado**:
   - Los platos con 0 unidades no aparecen en el ranking.

---

### Escenario S: Preservacion de fronteras operativas

1. **Accion**: Ejecutar las suites de prueba de KDS (spec 019), despacho en mostrador (spec 020), seguimiento en vivo (spec 021), PIN de entrega (spec 022) y pasarela de pago (specs 014/015/016/018).
2. **Resultado esperado**:
   - 100% de los tests existentes finalizan con exito.
   - La introduccion de `paid_at` y los endpoints de analitica no alteran ningun contrato previo.