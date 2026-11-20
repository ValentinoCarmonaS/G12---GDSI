# Contrato de API: ranking de platos mas vendidos (`dish-ranking`)

**Especificacion**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md)  
**Endpoint**: `GET /api/v1/admin/analytics/dish-ranking`  
**Seguridad**: `@PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('ADMIN')")`  

---

## 1. Descripcion operativa

Endpoint administrativo para consultar el ranking ordenado de platos gastronómicos comercializados a partir de pedidos concretados (con pago acreditado).

Caracteristicas clave:
- **Ordenamiento principal**: Estrictamente descendente por cantidad acumulada de unidades vendidas (`unitsSold`).
- **Desempate secundario**: Criterio alfabetico ascendente A-Z determinista por nombre del plato (`LOWER(dishName)`).
- **Alcance por defecto**: Historico global acumulado del establecimiento (`granularity=all`).
- **Alcance opcional**: Acotado al periodo en curso (`day`, `week`, `month`).
- **Exclusion de platos sin ventas**: Platos del catalogo con cero unidades vendidas en el periodo son omitidos del resultado.
- **Sin datos monetarios**: Expone exclusivamente unidades fisicas comercializadas; no revela precios ni importes.

---

## 2. Peticion HTTP

- **Metodo**: `GET`
- **Ruta**: `/api/v1/admin/analytics/dish-ranking`
- **Parametros de consulta (Query Params)**:
  - `granularity` (String, opcional, por defecto `"all"`): Valores permitidos: `"all"`, `"day"`, `"week"`, `"month"`.
- **Cabeceras obligatorias**:
  - `Authorization: Bearer <JWT_ADMIN>`
- **Cabeceras de respuesta**:
  - `Cache-Control: no-cache, no-store, must-revalidate`

---

## 3. Respuestas HTTP

### 3.1. Exito: `200 OK` (Con platos vendidos)

Retornado cuando existen ventas concretadas en el periodo.

```json
[
  {
    "dishId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "dishName": "Empanada de Carne Suave",
    "unitsSold": 85
  },
  {
    "dishId": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
    "dishName": "Hamburguesa Clasica",
    "unitsSold": 42
  },
  {
    "dishId": "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
    "dishName": "Pizza Muzzarella",
    "unitsSold": 42
  }
]
```

*Nota sobre el desempate*: Ante igualdad de unidades (42 unidades en "Hamburguesa Clasica" y "Pizza Muzzarella"), la precedencia se define alfabeticamente A-Z de forma determinista y estable.

### 3.2. Exito: `200 OK` (Estado vacio - Sin ventas en el periodo)

Retornado cuando el establecimiento no registra ordenes concretadas en el periodo. Emite un arreglo JSON vacio sin errores.

```json
[]
```

### 3.3. Parametro invalido: `400 Bad Request`

Retornado si el valor de `granularity` no es reconocido.

```json
{
  "type": "https://easyeat.com/errors/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid granularity 'quarter'. Supported values are: all, day, week, month",
  "instance": "/api/v1/admin/analytics/dish-ranking"
}
```

### 3.4. No autorizado / Prohibido: `401 Unauthorized` / `403 Forbidden`

- `401`: Token JWT ausente, invalido o expirado.
- `403`: Usuario autenticado sin rol de `ADMINISTRADOR`.
- `403`: Peticion sin contexto de establecimiento activo (`TenantViolationException`).

---

## 4. Garantias transaccionales y de consulta

1. **Aislamiento Multi-Tenant**: La agregacion filtra por `orders.establishment_id = tenantId`.
2. **Definicion de venta concretada**: Solo computan lineas de pedidos con `orders.status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')`.
3. **Consolidacion inmediata**: Una vez acreditado el pago de una nueva orden, el ranking refleja de inmediato las nuevas unidades vendidas al consultar o refrescar el reporte.