# Behavioral Contracts: Umbral de Stock Crítico y Pausa Manual de Platos

**Feature**: `013-critical-stock-chef-pause`  
**Fecha**: 01/11/2026  
**Fuentes primarias**: `specs/013-critical-stock-chef-pause/spec.md §Requirements`, `§Acceptance Scenarios`, `§Edge Cases`, `§Invariantes`.

---

## Contrato 1: Configurar / Editar Umbral de Stock Crítico de un Ingrediente

- **Identificador Funcional**: `SET_INGREDIENT_CRITICAL_STOCK_THRESHOLD`
- **Operación Técnica**: `POST /api/v1/admin/ingredients` (creación) o `PUT /api/v1/admin/ingredients/{id}` (edición)
- **Actor**: Administrador (`ROLE_ADMIN` / `ROLE_ADMINISTRADOR`).
- **Entrada**:
  - `name`: `String` obligatorio (1 a 255 caracteres).
  - `category`: `IngredientCategory` obligatorio.
  - `unitOfMeasure`: `UnitOfMeasure` obligatorio.
  - `unitPurchaseCost`: `BigDecimal >= 0` opcional.
  - `criticalStockThreshold`: `Integer` opcional. Restricción estricta: entero $\ge 0$ o `null`.
- **Salida**: `AdminIngredientDetailResponse`:
  - `id`: `UUID`
  - `tenantId`: `UUID`
  - `name`: `String`
  - `category`: `IngredientCategory`
  - `unitOfMeasure`: `UnitOfMeasure`
  - `unitPurchaseCost`: `BigDecimal`
  - `status`: `IngredientStatus`
  - `currentStock`: `BigDecimal`
  - `criticalStockThreshold`: `Integer` ($\ge 0$ o `null`)
  - `isCritical`: `Boolean` (calculado dinámicamente)
  - `createdAt`, `updatedAt`: `Instant`
- **Errores**:
  - `criticalStockThreshold < 0`, decimal o no numérico: HTTP 400 Bad Request (`RFC 7807 ProblemDetail`), campo `criticalStockThreshold`, mensaje: `"El umbral de stock crítico debe ser un número entero mayor o igual a cero"`.
  - Actor con rol no autorizado (ej. Chef, Comensal): HTTP 403 Forbidden (`RFC 7807 ProblemDetail`).
  - `id` inexistente en el tenant: HTTP 404 Not Found.
- **Precondiciones**:
  - Usuario autenticado con rol Administrador en el tenant activo.
- **Postcondiciones**:
  - El umbral se persiste como atributo individual del insumo sin afectar a otros ingredientes (INV-1, SC-001).
  - El estado de criticidad se actualiza reactivamente para la siguiente consulta (INV-14, FR-010).
- **Concurrencia / Atomicidad**:
  - Transacción ACID estándar sobre `ingredients`. No bloquea ventas de platos asociados (INV-6, FR-009).
- **Backward Compatibility**:
  - Campo opcional/nullable. Insumos existentes sin umbral asumen `null` y `isCritical = false` (INV-7, EC-002).
- **Cobertura**: US1 AC-1.1, AC-1.2, AC-1.3, AC-1.4, FR-001, FR-002, FR-003, FR-017, INV-1, INV-2, INV-7, INV-16, SC-001, SC-002, SC-009, EC-001, EC-002, EC-018, EC-022, C-3, C-6.

---

## Contrato 2: Consultar Estado de Criticidad Individual de un Ingrediente

- **Identificador Funcional**: `GET_INGREDIENT_CRITICALITY_STATE`
- **Operación Técnica**: `GET /api/v1/admin/ingredients/{id}`
- **Actor**: Administrador (`ROLE_ADMIN` / `ROLE_ADMINISTRADOR`).
- **Entrada**:
  - Parámetro de ruta: `id` (`UUID`).
- **Salida**: `AdminIngredientDetailResponse`:
  - `currentStock`: `BigDecimal`
  - `criticalStockThreshold`: `Integer` (o `null`)
  - `isCritical`: `Boolean` garantizando:
    $$\text{isCritical} \iff (\text{status} = \text{'ACTIVE'}) \land (\text{threshold} \ne \text{null}) \land (\text{currentStock} < \text{threshold})$$
- **Errores**:
  - `id` no encontrado en el tenant: HTTP 404 Not Found.
  - No autenticado o sin rol Admin: HTTP 401 / 403.
- **Precondiciones**: Usuario con rol Administrador.
- **Postcondiciones**: Idempotente y de solo lectura.
- **Cobertura**: FR-004, FR-006, FR-007, FR-021, INV-3, INV-7, INV-17, SC-003, SC-004, EC-004, EC-024, C-1, C-10.

---

## Contrato 3: Consultar Listado de Inventario con Resaltado y Filtro Rápido "Solo Críticos"

- **Identificador Funcional**: `LIST_INVENTORY_WITH_CRITICAL_ALERTS_AND_FILTER`
- **Operación Técnica**: `GET /api/v1/admin/ingredients`
- **Actor**: Administrador (`ROLE_ADMIN` / `ROLE_ADMINISTRADOR`).
- **Entrada**:
  - Parámetros de consulta opcionales:
    - `category`: `IngredientCategory`
    - `status`: `IngredientStatus`
    - `criticalOnly`: `Boolean` (por defecto `false`)
- **Salida**: `List<AdminIngredientDetailResponse>`:
  - Cada elemento contiene `criticalStockThreshold` y `isCritical`.
  - Si `criticalOnly = true`: el conjunto resultante incluye **exclusivamente** aquellos ítems donde `isCritical = true` (SC-015, US2 AC-6).
  - En la interfaz del Back-Office, todo insumo con `isCritical = true` se presenta resaltado con etiqueta/ícono destacado de reabastecimiento (FR-005, INV-4, SC-003).
  - Cero emisiones de correos, SMS, notificaciones push u órdenes automáticas (FR-008, INV-5, SC-005).
- **Errores**:
  - Rol no autorizado: HTTP 403 Forbidden.
- **Precondiciones**: Insumos pertenecientes al tenant activo del Administrador.
- **Postcondiciones**: Idempotente.
- **Cobertura**: US2 AC-2.1, AC-2.2, AC-2.3, AC-2.4, AC-2.5, AC-2.6, FR-005, FR-006, FR-008, FR-020, INV-4, INV-5, SC-003, SC-004, SC-005, SC-015, EC-003, EC-005, EC-006, EC-007, EC-020, C-2, C-9.

---

## Contrato 4: Alternar Pausa Manual de Plato por el Chef

- **Identificador Funcional**: `TOGGLE_CHEF_DISH_PAUSE`
- **Operación Técnica**: `PATCH /api/v1/dishes/{id}/chef-pause`
- **Actor**: Chef (`ROLE_CHEF`).
- **Entrada**:
  - Parámetro de ruta: `id` (`UUID`).
  - Cuerpo JSON:
    ```json
    {
      "paused": true
    }
    ```
- **Salida**: `DishChefPauseResponse`:
  - `dishId`: `UUID`
  - `pausedByChef`: `Boolean`
  - `updatedAt`: `Instant`
- **Invariantes Garantizadas**:
  - **Operabilidad preventiva permanente (INV-19, FR-023)**: El comando es aceptado y ejecutado exitosamente con independencia de si el plato tiene stock o si está desactivado por el administrador (EC-011, EC-012).
  - **Desacoplamiento total (INV-11, FR-014)**: NO modifica stock físico de insumos, NO modifica precios de venta, NO altera recetas, NO crea asientos de auditoría de inventario ni alertas de reabastecimiento.
- **Errores**:
  - Usuario con rol distinto a Chef (ej. Administrador, Comensal): HTTP 403 Forbidden (`RFC 7807 ProblemDetail`).
  - `id` no encontrado en el tenant: HTTP 404 Not Found.
- **Concurrencia y Atomicidad (EC-015, EC-025)**:
  - Operación atómica a nivel de base de datos (`UPDATE dishes SET paused_by_chef = :paused, updated_at = NOW() WHERE id = :id AND tenant_id = :tenantId`).
  - Ante alternancia rápida o pulsaciones simultáneas entre dos chefs de la misma brigada, el último cambio confirmado prevalece determinísticamente.
- **Cobertura**: US3 AC-3.1, AC-3.2, AC-3.3, FR-011, FR-014, FR-018, FR-023, INV-8, INV-9, INV-11, INV-16, INV-19, SC-006, SC-007, SC-010, EC-011, EC-012, EC-015, EC-016, EC-019, EC-021, EC-025, C-5, C-12.

---

## Contrato 5: Consultar Estado de Pausa del Plato para el Personal de Cocina

- **Identificador Funcional**: `GET_CHEF_DISHES_OPERATIONAL_STATUS`
- **Operación Técnica**: `GET /api/v1/dishes/operational-status`
- **Actor**: Chef (`ROLE_CHEF`).
- **Entrada**:
  - Encabezado opcional: `If-None-Match: "<etag>"`
- **Salida**:
  - Si no hay cambios en el estado operativo del menú: HTTP 304 Not Modified.
  - Si hay cambios: HTTP 200 OK con encabezado `ETag: "<nuevo_etag>"` y cuerpo JSON:
    ```json
    {
      "items": [
        {
          "dishId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "pausedByChef": true,
          "hasStock": true,
          "isActiveAdmin": true,
          "status": "PUBLISHED"
        }
      ]
    }
    ```
- **Invariantes Garantizadas**:
  - Muestra unívocamente la distinción entre las 3 causas (INV-10, SC-008, SC-014).
- **Cobertura**: FR-015, FR-022, INV-10, INV-18, SC-008, SC-014, C-4, C-11.

---

## Contrato 6: Suscripción Colaborativa de Cocina a Cambios de Pausa en Tiempo Real

- **Identificador Funcional**: `SUBSCRIBE_CHEF_COLLABORATIVE_SYNC`
- **Operación Técnica**: Polling condicional recurrente a `GET /api/v1/dishes/operational-status` con cadencia de $2500\text{ ms}$ (en frontend vía hook `useChefDishesSync`).
- **Actor**: Clientes web de usuarios con rol Chef en el mismo establecimiento.
- **Entrada**: Encabezado `If-None-Match` con el último ETag conocido.
- **Salida**:
  - En $\le 3\text{ s}$ tras la conmutación de cualquier chef, todos los paneles de cocina de los chefs reciben el nuevo estado operativo y actualizan la posición del toggle sin recargar la página (INV-18, FR-022, SC-008, EC-025).
- **Cobertura**: FR-022, INV-18, EC-025, Clarification C-11.

---

## Contrato 7: Suscripción del Catálogo del Comensal a Cambios de Disponibilidad y Visibilidad por Pausa

- **Identificador Funcional**: `SUBSCRIBE_DINER_CATALOG_REALTIME_AVAILABILITY`
- **Operación Técnica**: Polling condicional recurrente a `GET /api/v1/establishments/{tenantId}/catalog/availability` con cadencia de $2500\text{ ms}$ (en frontend vía hook `useRealtimeAvailability`).
- **Actor**: Comensal (`ROLE_COMENSAL`).
- **Entrada**: Encabezado `If-None-Match: "<etag>"`.
- **Salida**:
  - Si un plato es pausado por el chef (`paused_by_chef = true`):
    - El ETag del establecimiento cambia inmediatamente.
    - El cliente recibe el nuevo mapa de disponibilidad en $\le 2.5\text{ s}$ ($\le 3\text{ s}$ SLA).
    - En el catálogo del comensal, el plato se oculta de la grilla de platos (INV-8, FR-012, SC-006).
    - Si se despausa, y no hay otras causas de indisponibilidad, reaparece en el catálogo en $\le 3\text{ s}$ (INV-9, FR-013, SC-007).
- **Cobertura**: US3 AC-3.1, AC-3.2, FR-012, FR-013, INV-8, INV-9, INV-15, SC-006, SC-007, C-7.

---

## Contrato 8: Consultar Estado de Disponibilidad Agregado del Plato con Tres Causas Separadas

- **Identificador Funcional**: `GET_DISH_THREE_TIER_AVAILABILITY`
- **Operación Técnica**: `GET /api/v1/dishes/{id}/availability-details`
- **Actor**: Personal autorizado (Admin, Chef).
- **Entrada**: Parámetro de ruta `id` (`UUID`).
- **Salida**: `DishAvailabilityDetailsResponse`:
  ```json
  {
    "dishId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "causes": {
      "sinStock": false,
      "pausaChef": true,
      "desactivacionAdmin": false
    },
    "isOrderable": false,
    "isVisibleToDiner": false
  }
  ```
- **Invariantes Garantizadas**:
  - Las tres causas se informan explícitamente sin colapsar (INV-10, FR-015, SC-014).
  - `isVisibleToDiner = !causes.pausaChef && !causes.desactivacionAdmin && status == PUBLISHED`.
  - `isOrderable = isVisibleToDiner && !causes.sinStock`.
- **Cobertura**: FR-015, INV-10, INV-12, INV-13, SC-008, SC-014, EC-011, EC-012, EC-013, EC-014, C-4.

---

## Contrato 9: Extensión del Carrito ante Pausa del Chef (Preservación con Bloqueo)

- **Identificador Funcional**: `CART_HOLD_AND_LOCKOUT_ON_CHEF_PAUSE`
- **Operación Técnica**: Evaluación reactiva en `CartContext` / `CartDrawer` ante cambios de disponibilidad del tenant.
- **Actor**: Comensal con sesión activa y platos en carrito.
- **Entrada**: Ítems en `sessionStorage` (`'easyeat_cart'`) cruzados contra la respuesta del hook `useRealtimeAvailability`.
- **Comportamiento Garantizado**:
  - Si un plato en el carrito tiene `paused_by_chef = true`:
    1. El ítem **permanece** en el carrito (no se elimina automáticamente de la sesión).
    2. Exhibe insignia de advertencia explícita: `"Pausado por cocina"`.
    3. Controles unitarios de incremento (`+`) inhabilitados (`disabled`).
    4. Botón de confirmación de pedido / checkout bloqueado con aviso: `"Tu orden contiene platos pausados por cocina. Debés retirarlos o aguardar su reactivación para continuar."`
  - Liberación: Al pulsar "Eliminar" sobre el ítem o al reactivarse la disponibilidad por el chef.
- **Cobertura**: FR-019, EC-023, Clarification C-8 (consistencia con Spec 009 FR-014a).

---

## Contrato 10: Extensión del Modal de Detalle del Comensal ante Pausa en Vivo

- **Identificador Funcional**: `DISH_DETAIL_MODAL_CHEF_PAUSE_HANDLING`
- **Operación Técnica**: Actualización reactiva de `DishDetailModal.tsx` al sincronizar `useRealtimeAvailability`.
- **Actor**: Comensal visualizando un plato en modal interactivo.
- **Comportamiento Garantizado**:
  - Si el chef pausa el plato mientras el modal está abierto:
    1. En un lapso $\le 3\text{ s}$, el modal actualiza su vista exhibiendo distintivo: `"No disponible / Pausado por cocina"`.
    2. Los controles de cantidad y el botón "Agregar al Carrito" se inhabilitan.
    3. **El modal NO se cierra automáticamente**; el comensal puede continuar leyendo información técnica o cerrarlo voluntariamente.
    4. Al cerrar el modal, el plato no vuelve a renderizarse en la grilla del catálogo (INV-8).
- **Cobertura**: FR-016, INV-15, EC-017, Clarification C-5, consistencia con Spec 011 Clarification #5.

