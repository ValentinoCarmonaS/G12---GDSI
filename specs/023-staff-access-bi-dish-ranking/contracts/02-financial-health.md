# Contrato de API: panel de salud financiera (`financial-health`)

**Especificacion**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md)  
**Endpoint**: `GET /api/v1/admin/analytics/financial-health`  
**Seguridad**: `@PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('ADMIN')")`  

---

## 1. Descripcion operativa

Endpoint administrativo para consultar las metricas cuantitativas consolidadas de salud financiera del establecimiento (ingresos totales acumulados y cantidad de pedidos concretados).

Permite consultar por tres granularidades temporales en curso:
- `day` (defecto): Dia actual en curso (00:00:00 a 23:59:59 segun el huso horario del local).
- `week`: Semana calendario ISO 8601 en curso (Lunes 00:00:00 a Domingo 23:59:59).
- `month`: Mes calendario en curso (dia 1 00:00:00 al ultimo instante del mes).

Solo computa ordenes con pago efectivamente acreditado (`PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`), excluyendo pedidos cancelados, expirados o con cobro pendiente.

---

## 2. Peticion HTTP

- **Metodo**: `GET`
- **Ruta**: `/api/v1/admin/analytics/financial-health`
- **Parametros de consulta (Query Params)**:
  - `granularity` (String, opcional, por defecto `"day"`): Valores admitidos: `"day"`, `"week"`, `"month"`.
- **Cabeceras obligatorias**:
  - `Authorization: Bearer <JWT_ADMIN>`
- **Cabeceras de respuesta**:
  - `Cache-Control: no-cache, no-store, must-revalidate`

---

## 3. Respuestas HTTP

### 3.1. Exito: `200 OK` (Con ventas en el periodo)

Retornado cuando la consulta se ejecuta correctamente y existen ordenes en el periodo.

```json
{
  "granularity": "day",
  "periodStart": "2026-11-20T00:00:00-03:00",
  "periodEnd": "2026-11-20T23:59:59.999999999-03:00",
  "revenue": 24500.50,
  "orderCount": 14
}
```

### 3.2. Exito: `200 OK` (Estado vacio - Sin pedidos en el periodo)

Retornado cuando en el periodo seleccionado no se registraron ventas con pago acreditado. Conforme a FR-011, emite explicitamente cero sin errores ni valores nulos.

```json
{
  "granularity": "week",
  "periodStart": "2026-11-16T00:00:00-03:00",
  "periodEnd": "2026-11-22T23:59:59.999999999-03:00",
  "revenue": 0.00,
  "orderCount": 0
}
```

### 3.3. Parametro invalido: `400 Bad Request`

Retornado si el valor de `granularity` no corresponde a ninguna de las opciones permitidas.

```json
{
  "type": "https://easyeat.com/errors/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid granularity 'year'. Supported values are: day, week, month",
  "instance": "/api/v1/admin/analytics/financial-health"
}
```

### 3.4. No autorizado / Prohibido: `401 Unauthorized` / `403 Forbidden`

- `401`: Token JWT ausente, invalido o expirado.
- `403`: Usuario autenticado sin rol de `ADMINISTRADOR`.
- `403`: Intento de consulta sin un contexto de establecimiento activo (`TenantViolationException`).

---

## 4. Garantias de calculo y limites

1. **Aislamiento Multi-Tenant**: Las metricas filtran estrictamente por el `tenantId` del administrador autenticado (`orders.establishment_id = tenantId`).
2. **Semi-intervalo temporal**: Se evalua `orders.paid_at >= periodStart AND orders.paid_at < periodEnd`.
3. **Escala monetaria**: `revenue` se presenta con precision decimal de 2 digitos, sin errores de coma flotante.
4. **Conmutacion reactiva**: La consulta no mantiene acumuladores intermedios en memoria; cada peticion recalcula los valores desde el indice de la base de datos en menos de 2 segundos.