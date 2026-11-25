# Contrato 01 — Catálogo del comensal y disponibilidad en tiempo real

**Trazabilidad**: US1, FR-001, FR-002, FR-007, FR-009, FR-010, SC-001, SC-003, SC-004.

---

## 1. Catálogo categorizado del establecimiento

- **Method**: `GET`
- **Path**: `/api/v1/establishments/{tenantId}/catalog`
- **Autenticación y roles**: `hasRole('COMENSAL')` (Bearer JWT; validado vía `UserSessionCache`).
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `Accept-Encoding: gzip`
  - Response: `Content-Type: application/json`, `Content-Encoding: gzip` (cuando el cuerpo supera `1024 bytes` y el cliente acepta `gzip`)
- **Request schema**:
  - Path param: `tenantId` (`UUID`, obligatorio).
- **Response schema (`200 OK` — `DinerCatalogResponse`)**:
  ```json
  {
    "establishment": {
      "id": "uuid",
      "name": "string",
      "status": "ACTIVE | PAUSED | DISABLED",
      "isOpen": true,
      "nextOpeningSchedule": "string | null"
    },
    "categories": [
      {
        "category": "Entradas | Platos principales | Postres | Bebidas | Ensaladas",
        "dishes": [
          {
            "id": "uuid",
            "name": "string",
            "description": "string | null",
            "category": "string",
            "price": 0.0,
            "photoUrl": "/api/v1/dishes/{dishId}/photo | null",
            "isAvailable": true,
            "availableStock": 0,
            "isCompliant": true,
            "conflictingRestrictions": ["string"],
            "recipeIngredients": ["string"],
            "allergens": ["string"],
            "lifestyles": ["string"]
          }
        ]
      }
    ]
  }
  ```
- **Status codes**:
  - `200 OK`: catálogo ensamblado en lote ($O(1)$, ≤ 6 consultas SQL) con orden canónico determinista.
  - `401 Unauthorized` (`application/problem+json`): JWT inválido, expirado o `tokenVersion` revocado.
  - `403 Forbidden` (`application/problem+json`): rol no autorizado o cuenta suspendida.
  - `404 Not Found` (`application/problem+json`): establecimiento inexistente.
- **Notas de preservación FR-010**:
  - No se añaden, renombran ni eliminan propiedades JSON.
  - `photoUrl` mantiene el formato exacto `"/api/v1/dishes/" + dishId + "/photo"` o `null` (sin query params en JSON).
  - El stock (`availableStock`) y la disponibilidad (`isAvailable`) siempre reflejan estado transaccional vigente sin caché con retardo en servidor.
  - El orden de categorías (`Entradas`, `Platos principales`, `Postres`, `Bebidas`, `Ensaladas`), platos, ingredientes y listas ordenadas alfabéticamente de `allergens`/`lifestyles` es bit a bit idéntico al actual.

---

## 2. Listado plano heredado del establecimiento (respaldo explícito)

- **Method**: `GET`
- **Path**: `/api/v1/establishments/{tenantId}/dishes` (`@Deprecated`, `permitAll`)
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>` (opcional), `Accept-Encoding: gzip`
  - Response: `Content-Type: application/json`, `Content-Encoding: gzip` (si cuerpo ≥ `1024 bytes`)
- **Request schema**:
  - Path param: `tenantId` (`UUID`, obligatorio).
- **Response schema (`200 OK` — `List<EnrichedDishResponse>`)**:
  - Idéntico al contrato vigente de Spec 003 (`id`, `tenantId`, `name`, `description`, `price`, `isActive`, `isCompliant`, `conflictingRestrictions`).
- **Status codes**: `200 OK`, `400 Bad Request`.
- **Notas de preservación FR-010**:
  - Se beneficia automáticamente de la optimización $O(1)$ de `DinerCatalogService.getCatalog` al delegar en él, manteniendo el fallback a `catalogComplianceService.getEnrichedCatalog` ante `EstablishmentNotFoundException`.
  - En el cliente (`CatalogPage`), solo se invoca como respaldo explícito cuando no existe `tenant.id` activo en contexto (preservando tests legacy de Spec 003 y acceso invitado sin tenant).

---

## 3. Mapa de disponibilidad en tiempo real con validación condicional `ETag`

- **Method**: `GET`
- **Path**: `/api/v1/establishments/{tenantId}/catalog/availability`
- **Autenticación y roles**: `hasRole('COMENSAL')`.
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `If-None-Match: "<sha256-hex>"` (opcional), `Accept-Encoding: gzip`
  - Response:
    - `ETag: "<sha256-hex-64>"` (siempre emitido en `200` y `304`)
    - `Access-Control-Expose-Headers: ETag` (habilitado en `SecurityConfig` para despliegues cross-origin)
    - `Content-Type: application/json` (en `200`)
    - `Content-Encoding: gzip` (en `200` si cuerpo ≥ `1024 bytes`)
- **Request schema**:
  - Path param: `tenantId` (`UUID`, obligatorio).
- **Response schema**:
  - `200 OK` (`AvailabilityMapResponse`):
    ```json
    {
      "availability": {
        "dish-uuid-1": true,
        "dish-uuid-2": false
      }
    }
    ```
  - `304 Not Modified`: sin cuerpo, preservando cabecera `ETag`.
- **Status codes**:
  - `200 OK`: mapa calculado en pasada única y lote ($O(1)$, ≤ 4 consultas SQL).
  - `304 Not Modified`: `If-None-Match` coincide con el `ETag` calculado (con o sin comillas dobles).
  - `401 Unauthorized` / `403 Forbidden`: fallo de sesión o rol.
- **Notas de preservación FR-010**:
  - El algoritmo de firma `ETag` SHA-256 es bit a bit idéntico al actual (`<uuid>=<available>:paused=<pausedByChef>;` ordenado por `UUID::toString`, o SHA-256 de `"empty"`).
  - Un cambio en `pausedByChef` sobre un plato ya agotado (`false`) sigue generando un `ETag` distinto.
  - `DinerCatalogController` mantiene compatibilidad total con los stubs de `DinerAvailabilityControllerTest`.
  - El cliente (`CatalogPage`) inicializa `useRealtimeAvailability` con la disponibilidad recién recibida en `/catalog` y difiere el primer sondeo periódico al primer intervalo (`2500 ms`), eliminando el sondeo duplicado en `t = 0 ms`, mientras `useRealtimeAvailability` conserva su firma y comportamiento por defecto (`deferInitialPoll = false`) para todos los tests existentes.
