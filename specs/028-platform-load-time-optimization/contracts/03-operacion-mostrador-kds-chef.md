# Contrato 03 — Operación en tiempo real: Mostrador, KDS y Gestión de Platos del Chef

**Trazabilidad**: US3, FR-004, FR-009, FR-010, SC-002, SC-005, decisiones Q2 y Q4.

---

## 1. Pedidos listos para retiro en Mostrador

- **Method**: `GET`
- **Path**: `/api/v1/counter/orders`
- **Autenticación y roles**: `hasAnyRole('ENCARGADO', 'ADMINISTRADOR')`.
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `Accept-Encoding: gzip`
  - Response:
    - `Cache-Control: no-cache, no-store, must-revalidate` (preservado exacto)
    - `Content-Type: application/json`
    - `Content-Encoding: gzip` (si cuerpo ≥ `1024 bytes`)
- **Request schema**:
  - Query param `status`: `String`, opcional, default `"READY_FOR_PICKUP"`.
  - Query param `establishmentId`: `UUID`, opcional (si se omite, se resuelve desde `user.tenantId`).
- **Response schema (`200 OK` — `List<CounterReadyOrderResponse>`)**:
  ```json
  [
    {
      "ticketId": "uuid",
      "orderId": "uuid",
      "ticketNumber": 104,
      "timeSlot": { "start": "13:00", "end": "13:15" },
      "readyAt": "2026-11-24T16:30:00Z",
      "customer": {
        "userId": "uuid",
        "fullName": "string",
        "photoUrl": "/api/v1/profile/users/{userId}/avatar | null"
      },
      "securityCode": "4821",
      "items": [
        { "quantity": 2, "dishName": "string" }
      ]
    }
  ]
  ```
- **Status codes**: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`.
- **Notas de preservación FR-010**:
  - Ejecución en 4 consultas constantes ($O(1)$): `findCounterReadyTickets`, `findAllByIdWithItems`, `findAllById` y `findUserIdsWithPhoto` (1 sola consulta si no hay comandas listas).
  - Se preserva la firma Java `CounterOrderController.getCounterReadyOrders(String, UUID, User)` verificada por reflexión en `CounterOrderControllerIntegrationTest:120` y el constructor de 5 argumentos.
  - Se preservan el orden determinista (`readyAt ASC`, `ticketNumber ASC`), la jerarquía de fallback del nombre del cliente, `photoUrl` exacto sin query params, validación de `securityCode` de 4 dígitos y exclusión total de campos financieros.

---

## 2. Resumen ejecutivo del Tablero KDS

- **Method**: `GET`
- **Path**: `/api/v1/kitchen/dashboard/summary`
- **Autenticación y roles**: `hasRole('ROLE_CHEF') or hasRole('CHEF') or hasRole('ROLE_ADMIN') or hasRole('ADMIN')`.
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `Accept-Encoding: gzip`
  - Response: `Content-Type: application/json`, `Content-Encoding: gzip` (si cuerpo ≥ `1024 bytes`)
- **Request schema**:
  - Query param `establishmentId`: `UUID`, opcional.
- **Response schema (`200 OK` — `KdsDashboardSummaryResponse`)**:
  ```json
  {
    "queuedOrdersCount": 0,
    "inPreparationOrdersCount": 0,
    "readyForPickupOrdersCount": 0,
    "completedTodayCount": 0
  }
  ```
- **Status codes**: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`.
- **Notas de preservación FR-010**:
  - Resuelto en **1 única consulta SQL agregada** (`SUM(CASE WHEN ...)`), conservando exactamente los predicados originales: los tres contadores activos sin filtro de `pickupDate` y `completedTodayCount` filtrado por `pickupDate = today`.

---

## 3. Listado paginado de platos del Chef

- **Method**: `GET`
- **Path**: `/api/v1/dishes`
- **Autenticación y roles**: `hasRole('CHEF')`.
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `Accept-Encoding: gzip`
  - Response: `Content-Type: application/json`, `Content-Encoding: gzip` (si cuerpo ≥ `1024 bytes`)
- **Request schema**:
  - Query params: `status` (`ALL | DRAFT | PENDING_PRICE | PUBLISHED | CANCELLED`, default `ALL`), parámetros estándar `Pageable` (`page`, `size`, `sort`).
- **Response schema (`200 OK` — `DishPageResponse`)**:
  - Campos exactos: `content` (`List<DishSummaryResponse>` con `id`, `name`, `category`, `status`, `hasPhoto`, `photoUrl`, `ingredientCount`, `missingConditions`, `retailPrice`, `pausedByChef`, `isActive`, `updatedAt`, etc.), `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `isLast`.
- **Status codes**: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`.
- **Notas de preservación FR-010**:
  - Resuelto en **≤ 4 consultas constantes** ($O(1)$) por página mediante proyección JPQL liviana sin `restrictionCodes` (página + conteo de Spring Data), consulta consolidada `recipes LEFT JOIN ingredients` en lote e IDs con foto en lote, eliminando tanto `existsByDishId`/`countByIdDishId`/`findByIdDishId` por fila como el `N+1` oculto de `Dish.restrictionCodes`.

---

## 4. Estado operativo de platos del Chef con validación condicional `ETag`

- **Method**: `GET`
- **Path**: `/api/v1/dishes/operational-status`
- **Autenticación y roles**: `hasRole('CHEF')`.
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>`, `If-None-Match: "<hex>"` (opcional), `Accept-Encoding: gzip`
  - Response:
    - `ETag: "<hex>"` (emitido en `200` y `304`)
    - `Access-Control-Expose-Headers: ETag` (habilitado en `SecurityConfig`)
    - `Content-Type: application/json` (en `200`)
    - `Content-Encoding: gzip` (en `200` si cuerpo ≥ `1024 bytes`)
- **Request schema**: Sin parámetros de consulta ni cuerpo.
- **Response schema**:
  - `200 OK` (`ChefDishesOperationalStatusResponse`):
    ```json
    {
      "items": [
        {
          "dishId": "uuid",
          "pausedByChef": false,
          "hasStock": true,
          "isActiveAdmin": true,
          "status": "PUBLISHED"
        }
      ]
    }
    ```
  - `304 Not Modified`: sin cuerpo, preservando cabecera `ETag`.
- **Status codes**: `200 OK`, `304 Not Modified`, `401 Unauthorized`, `403 Forbidden`.
- **Notas de preservación FR-010 (decisión Q2 + F1 + C1 + E2)**:
  - `DishService.getOperationalStatus` se resuelve en **≤ 2–3 consultas constantes** ($O(1)$): proyección JPQL liviana de los platos del tenant sin `restrictionCodes` (`id`, `pausedByChef`, `isActive`, `status`) y consulta consolidada `recipes LEFT JOIN ingredients` en lote para los platos `PUBLISHED`.
  - Se preserva la semántica de `hasStock` (`true` para platos no `PUBLISHED` o sin líneas de receta; `false` si algún insumo falta, no está `ACTIVE`, tiene `currentStock == null` o `currentStock < quantity`).
  - Se preserva exactamente la fórmula de `ETag` validada por `DishChefPauseOpenApiContractTest.java:136-143` (`"\"" + Integer.toHexString(sb.toString().hashCode()) + "\""` sobre `dishId=pausedByChef:hasStock;`), admitiendo comparación tolerante a comillas en `If-None-Match`.
  - En el cliente (`src/api/dishApi.ts`), `getOperationalStatus` agrega `validateStatus: (status) => (status >= 200 && status < 300) || status === 304` y devuelve `{ etag: resolvedEtag ?? ifNoneMatch, items: [], notModified: true }` ante `304`, mientras `useChefDishesSync.ts` ignora `response.notModified` para garantizar que `items` nunca se vacíe ante un `304` sin alterar `useChefDishesSync.test.ts`.
  - **Bandeja de Revisión del Administrador (`DishReviewInboxService`)**: complementariamente, `@BatchSize(size = 100)` en `Dish.restrictionCodes` protege las consultas paginadas de revisión del Administrador contra lecturas `N+1` ocultas sin alterar contratos ni pruebas existentes.
