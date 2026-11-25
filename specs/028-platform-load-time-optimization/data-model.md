# Modelo de datos y estructuras de diseño: optimización transversal de latencia (Spec 028)

**Rama**: `028-platform-load-time-optimization` | **Fecha**: 24/11/2026 | **Especificación**: [spec.md](./spec.md) | **Investigación**: [research.md](./research.md)

Este documento modela las cuatro entidades clave definidas en `spec.md`, las proyecciones auxiliares de lectura en lote y validación condicional sin `BYTEA`, y la migración versionada de índices compuestos requerida por FR-009. Ninguna entidad altera tablas existentes ni modifica esquemas JSON públicos (FR-010).

---

## 1. Entidad clave 1 — Lote de catálogo y disponibilidad (`Batched Catalog & Availability Snapshot`)

**Trazabilidad**: FR-001, FR-002, SC-001, D-01, D-02, D-11.

### Name
`BatchedCatalogSnapshot` (estructura inmutable en memoria ensamblada por petición dentro de `DinerCatalogService` y `DishAvailabilityEngine`, nunca almacenada en caché temporal con retardo).

### Fields

| Campo | Tipo lógico | Fuente de datos (consulta O(1)) | Propósito |
|---|---|---|---|
| `establishment` | `Establishment` | `establishmentRepository.findById(tenantId)` (1 consulta) | Metadatos de estado, nombre, zona horaria y apertura |
| `todaySchedule` | `Optional<EstablishmentOperatingSchedule>` | `OperatingScheduleCache.getForDay(tenantId, todayDow)` (0 consultas en hit; 1 en miss) | Determinación de `isOpen` y `nextOpeningSchedule` |
| `publishedDishes` | `List<Dish>` (con `restrictionCodes` precargado vía `LEFT JOIN FETCH`) | `dishRepository.findPublicCatalogDishesWithRestrictions(tenantId)` (1 consulta) | Platos publicados activos y no pausados (para catálogo) o publicados activos incluyendo `pausedByChef` (para mapa de disponibilidad) |
| `recipesByDishId` + `ingredientsById` | `Map<UUID, List<DishRecipeIngredient>>` y `Map<UUID, Ingredient>` | `dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(dishIds, tenantId)` (1 consulta consolidada con `LEFT JOIN Ingredient`) | Líneas de receta de todos los platos del establecimiento junto con el estado (`ACTIVE`/`DISCONTINUED`), nombre y `currentStock` de cada insumo referenciado en un solo viaje SQL |
| `dishIdsWithPhoto` | `Set<UUID>` | `dishPhotoRepository.findDishIdsWithPhoto(dishIds)` (1 consulta; no lee `BYTEA`) | Determinación de `photoUrl` (`/api/v1/dishes/{id}/photo` o `null`) |
| `reservedPortionsByDishId` | `Map<UUID, Integer>` | `temporaryReservationRepository.sumActiveReservedPortionsByDishIds(dishIds, now)` (1 consulta agregada `GROUP BY oi.dishId`) | Porciones bloqueadas por reservas `ACTIVE` con `expiresAt > now` |
| `userRestrictionCodes` | `Set<String>` | `userDietaryRestrictionRepository.findByIdUserId(user.id)` (1 consulta solo si hay usuario autenticado; 0 para invitado) | Cálculo de `isCompliant` y `conflictingRestrictions` |
| `dietaryTaxonomyByCode` | `Map<String, DietaryRestriction>` | `DietaryTaxonomyCache.getAllByCode()` (0 consultas en hit; 1 en miss) | Resolución de nombres de `allergens` y `lifestyles` y clasificación `COMMON_ALLERGEN` / `DIETARY_LIFESTYLE` |

### Relationships
- `publishedDishes` (1) → (0..N) `recipesByDishId` mediante `dish.id`.
- `recipesByDishId[dishId]` (N) → (1) `ingredientsById` mediante `line.ingredientId` filtrado por `tenantId`.
- `publishedDishes` (1) → (0..1) `dishIdsWithPhoto` mediante membresía de `dish.id`.
- `publishedDishes` (1) → (0..1) `reservedPortionsByDishId` mediante `dish.id` (valor por defecto `0`).

### Validation rules y determinismo
1. **Guarda `IN ()` vacío**: Si `publishedDishes` está vacía, no se ejecuta ninguna consulta de recetas, ingredientes, fotos ni reservas; se devuelven mapas/conjuntos vacíos y lista vacía de categorías. Si el conjunto de `ingredientIds` extraído de recetas está vacío, no se consulta `ingredients`.
2. **Presupuesto estricto de consultas (SC-001)**:
   - Catálogo del comensal con cachés de metadatos cálidas (`DietaryTaxonomyCache`, `OperatingScheduleCache`): `establishment` (1) + `dishes LEFT JOIN FETCH` (1) + `userDietaryRestrictions` si autenticado (0–1) + `recipes` (1) + `ingredients` (1) + `dishPhotos IDs` (1) + `reservedPortions GROUP BY` (1) → si `establishment` se sirve junto a la validación del catálogo y se aprovechan las cachés cálidas, el total queda acotado a **≤ 6 consultas** en invitado y en autenticado (pudiendo consolidar `recipes` + `ingredients` en un único `JOIN` JPQL para garantizar ≤ 6 incluso con `userDietaryRestrictions` y cachés frías: ver nota de optimización de join abajo).
   - **Consolidación `recipes` + `ingredients` (garantía estricta ≤ 6 en todos los casos)**: Una proyección JPQL `SELECT dri.id.dishId, dri.quantity, i FROM DishRecipeIngredient dri JOIN Ingredient i ON i.id = dri.id.ingredientId AND i.tenantId = :tenantId WHERE dri.id.dishId IN :dishIds ORDER BY dri.id.dishId, dri.id.ingredientId` unifica en **1 sola consulta** las líneas de receta y sus ingredientes del tenant (usando `LEFT JOIN` para detectar ingredientes faltantes/inactivos de forma idéntica a la lógica actual). Con esto, el catálogo ejecuta **5 consultas en autenticado** (4 en invitado) en caliente y el sondeo de disponibilidad ejecuta **3 consultas** (`dishes`, `recipes LEFT JOIN ingredients`, `reservedPortions GROUP BY`), cumpliendo SC-001 con margen.
3. **Cálculo en pasada única de disponibilidad y stock (FR-002)**:
   - Si un plato no tiene líneas de receta (`recipes.isEmpty()`) o alguna línea tiene `ingredientId == null` o `quantity == null`, resulta `{ isAvailable = false, availableStock = 0 }`.
   - Para cada línea de receta se evalúa el ingrediente del mapa en memoria: si falta, si `status != ACTIVE`, si `currentStock == null` o `currentStock < 0`, o si `currentStock < line.quantity`, el plato resulta no disponible (`rawAvailable = false`).
   - En la misma iteración, para toda línea con `line.quantity > 0`: si el ingrediente falta, no está `ACTIVE` o `currentStock <= 0`, `maxPortions = 0`; de lo contrario `portions = floor(currentStock / line.quantity)` y `maxPortions = min(maxPortions, portions)`.
   - Al cerrar el bucle: `rawStock = (maxPortions == Integer.MAX_VALUE) ? 0 : maxPortions`; `netStock = max(0, rawStock - reservedPortionsByDishId.getOrDefault(dishId, 0))`; `isAvailable = rawAvailable && netStock > 0` (siempre que exista soporte de reservas, idéntico al motor actual).
4. **Ordenamiento canónico y determinismo**:
   - Categorías emitidas en el orden estricto `CANONICAL_CATEGORIES`: `Entradas`, `Platos principales`, `Postres`, `Bebidas`, `Ensaladas` (omitiendo categorías vacías y platos con `category == null`).
   - Platos dentro de cada categoría en el mismo orden retornado por `DishRepository`.
   - `allergens` y `lifestyles` resueltos vía `DietaryTaxonomyCache` (con `FALLBACK_ALLERGEN_NAMES` y `FALLBACK_LIFESTYLE_NAMES` intactos) y ordenados mediante `Collections.sort`.
   - Firma `ETag` en `DishAvailabilityEngine`: si el mapa está vacío → SHA-256 hex minúsculas de `"empty"`; en caso contrario, claves ordenadas por `Comparator.comparing(UUID::toString)`, concatenando `<uuid>=<available>:paused=<Boolean.TRUE.equals(dish.getPausedByChef())>;` (omitiendo `:paused=...` solo si la entidad no existe en el mapa de platos consultados) y aplicando SHA-256 hexadecimal en minúsculas de 64 caracteres, bit a bit idéntico a la implementación previa.

### State transitions
No aplica persistencia de estado: el snapshot vive estrictamente durante el ciclo de una petición HTTP de lectura y se descarta al serializar la respuesta (garantizando SC-005: cero stock cacheado).

---

## 2. Entidad clave 2 — Agregado de ocupación por franja horaria (`Time-Slot Occupancy Aggregate`)

**Trazabilidad**: FR-003, SC-002, SC-003, D-03.

### Name
`TimeSlotOccupancyAggregate` (proyección de consulta agrupada en `OrderRepository` consumida por `PickupSlotService`).

### Fields

| Campo | Tipo lógico | Columna origen | Regla / descripción |
|---|---|---|---|
| `timeSlotStart` | `LocalTime` | `orders.time_slot_start` | Hora de inicio de la franja de 15 minutos (`HH:mm:ss`) |
| `bookedCount` | `long` | `COUNT(o.id)` | Cantidad total de órdenes activas computables en la franja |

### Relationships
- Agregado agrupado por `timeSlotStart` filtrado por `(establishment_id, pickup_date)` sobre la tabla `orders`.
- Consumido por `PickupSlotService.getPickupSlots(establishmentId, requestedDate)` junto con `OperatingScheduleCache` (para el día solicitado y el día actual) y la entidad `Establishment`.

### Validation rules
1. **Estados computables (idénticos a `countActiveOrdersBySlot`)**:
   - `status IN ('CONFIRMED', 'PAID', 'IN_PROGRESS', 'PENDING_ACCREDITATION', 'READY_FOR_PICKUP')`
   - O `(status = 'PENDING_PAYMENT' AND (expires_at IS NULL OR expires_at > :now))`
2. **Franjas sin órdenes**: Ausencia de fila en la proyección equivale a `bookedCount = 0`.
3. **Reglas de negocio invariantes**:
   - Fecha solicitada válida solo entre `today` y `today.plusDays(7)` en la zona horaria del establecimiento (defecto `America/Argentina/Buenos_Aires`).
   - Generación de intervalos de 15 minutos desde `openingTime` mientras `slotStart + 15 min <= closingTime`.
   - `occupancyPercentage = (maxCapacity > 0) ? ((double) bookedCount / maxCapacity) * 100.0 : 0.0`.
   - Clasificación:
     - `date == today && slotStart < nowTime + leadTimeMinutes` → `EXPIRED_OR_LEAD_TIME` (`isSelectable = false`, excluido de `slots`).
     - `bookedCount >= maxCapacity || occupancyPercentage >= 100.0` → `SOLD_OUT` (`isSelectable = false`, excluido de `slots`).
     - `occupancyPercentage >= 80.0` → `HIGH_DEMAND` (`isSelectable = true`).
     - Caso contrario → `AVAILABLE` (`isSelectable = true`).

### Indexes
- Reutiliza directamente el índice compuesto existente de `V16`: `idx_orders_slot_capacity ON orders (establishment_id, pickup_date, time_slot_start, status, expires_at)`. No requiere índice adicional sobre `orders`.

---

## 3. Entidad clave 3 — Política de caché coherente de baja volatilidad (`Coherent Low-Volatility Cache Entry`)

**Trazabilidad**: FR-005, SC-005, SC-006, D-05, D-06.

### Name
Conjunto de cuatro componentes Caffeine fuertemente tipados y acotados en memoria de servidor:
1. `UserSessionCache`
2. `DietaryTaxonomyCache`
3. `BrandingCache`
4. `OperatingScheduleCache`

### Fields y presupuesto de capacidad (límite 512 MB)

| Caché | Clave | Valor almacenado | `maximumSize` | `expireAfterWrite` | Consumo estimado máximo |
|---|---|---|---|---|---|
| `UserSessionCache` | `UUID userId` | `User` (campos escalares; cero asociaciones lazy) | `2000` entradas | `5 min` | ~1,6 MB |
| `DietaryTaxonomyCache` | `String` (`"ALL"`) | `List<DietaryRestriction>` inmutable | `1` entrada | `60 min` | < 10 KB |
| `BrandingCache` | `BrandingCacheKey(BrandingViewType view, UUID establishmentId)` | `RestaurantBrandingResponse` (vista `DINER` o `ADMIN`) | `200` entradas | `15 min` | ~200 KB |
| `OperatingScheduleCache` | `UUID establishmentId` | `Map<DayOfWeek, EstablishmentOperatingSchedule>` inmutable | `200` entradas | `15 min` | ~400 KB |

**Presupuesto total máximo en Heap**: `< 2,5 MB` (< 0,5 % de los 512 MB de Render Free).

### Relationships y matriz de invalidación sincrónica obligatoria

| Caché | Consumidores de lectura | Eventos de escritura que ejecutan invalidación sincrónica (`evict`) | Red de seguridad JPA |
|---|---|---|---|
| `UserSessionCache` | `JwtAuthenticationFilter` | 1. `AuthService.register`, `login`, `logout`<br>2. `PasswordResetService.confirmPasswordReset`<br>3. `FirstLoginPasswordController.changeFirstLoginPassword`<br>4. `ProfileService.updatePersonalData`, `updateDemographics`<br>5. `EmailChangeService.requestEmailChange`, `resendVerificationEmail`, `cancelPendingEmailChange`, `confirmEmailChange`<br>6. `StaffService.registerStaff`, `updateStaff`, `updateStaffStatus` | `@EntityListeners` en `User` (`@PostPersist`, `@PostUpdate`, `@PostRemove` → `evict(user.id)`), cubriendo también escrituras directas en `userRepository.save` como `LoginIntegrationTest` |
| `DietaryTaxonomyCache` | `DietaryRestrictionService`, `CatalogComplianceService`, `DinerCatalogService`, `DishService.updateAllergens` | Sin endpoints de escritura en la plataforma (catálogo maestro sembrado por Flyway); expone `evictAll()` para mantenimiento y pruebas | No requiere |
| `BrandingCache` | `RestaurantBrandingService.getDinerBranding`, `getAdminBranding` | 1. `RestaurantBrandingService.updateBranding` (invalida `DINER` y `ADMIN` para `establishmentId`)<br>2. `BrandingLogoStorageService.storeLogo` (invalida `DINER` y `ADMIN` para `establishmentId`) | `@EntityListeners` en `RestaurantBranding` (`@PostPersist`, `@PostUpdate`, `@PostRemove` → `evict(establishmentId)`) |
| `OperatingScheduleCache` | `OperatingScheduleService.getWeek`, `DinerCatalogService`, `EstablishmentDiscoveryService`, `PickupSlotService`, `DinerTimeSlotService`, `SlotCapacityService`, `OrderService` | 1. `OperatingScheduleService.saveWeek` (invalida `establishmentId`) | `@EntityListeners` en `EstablishmentOperatingSchedule` (`@PostPersist`, `@PostUpdate`, `@PostRemove` → `evict(establishmentId)`) |

### Validation rules
- Ninguna caché almacena stock de ingredientes, reservas temporales activas, disponibilidad de platos ni estados de comandas/órdenes (prohibición explícita de Edge Case y SC-005).
- Las entradas de respuesta negativa (`Optional.empty()` en `UserSessionCache` cuando un `userId` no existe) no se almacenan en caché para evitar bloquear registros concurrentes o cachear usuarios inexistentes.
- La invalidación en escritura ocurre de forma sincrónica en el mismo hilo de la transacción/operación (y tras la persistencia en JPA) antes de retornar la respuesta HTTP al cliente.

### State transitions (`Coherent Low-Volatility Cache Entry`)

```text
ABSENT
  │ lectura (cache miss) → consulta a PostgreSQL
  ▼
POPULATED (entrada válida con timestamp de carga)
  ├── lectura (cache hit, edad < TTL) ──► POPULATED (0 consultas SQL)
  ├── escritura / evento JPA @PostUpdate / @PostRemove ──► EVICTED_SYNC (desalojo inmediato) ──► ABSENT
  ├── expiración TTL (expireAfterWrite) ──► EXPIRED ──► ABSENT
  └── presión de capacidad (> maximumSize, W-TinyLFU) ──► EVICTED_LRU ──► ABSENT
```

---

## 4. Entidad clave 4 — Almacén de estado compartido con revalidación en cliente (`Client SWR & In-Flight Deduplication Store`)

**Trazabilidad**: FR-007, FR-008, FR-010, SC-003, SC-004, SC-005, D-08, D-09.

### Name
`ClientSWRStore` (encapsulado por `ClientCacheProvider` en React Context dentro de `easyeat/frontend/src/context/ClientCacheContext.tsx`).

### Fields

| Campo | Tipo TypeScript | Descripción |
|---|---|---|
| `entries` | `Map<string, CacheEntry<unknown>>` | Última copia válida recibida del servidor (`{ data, updatedAt, status }`) por clave de recurso |
| `inFlight` | `Map<string, Promise<unknown>>` | Promesas HTTP activas indexadas por clave; evita solicitudes duplicadas concurrentes |
| `subscribers` | `Map<string, Set<() => void>>` | Oyentes reactivos por clave para propagar revalidaciones o mutaciones a todos los componentes montados |
| `mediaVersions` | `Map<string, number>` | Versión de refresco en memoria por identificador de activo (`dish:{id}`, `avatar:me`) tras subidas de imagen en la sesión actual (decisión Q3) |

### Claves canónicas de recursos

| Clave | Recurso HTTP respaldado | Consumidores compartidos |
|---|---|---|
| `profile:me` | `GET /api/v1/profile/me` | `WorkspaceLayout` (solo `COMENSAL`), `useProfile` (`CatalogPage`, `ProfilePage`) |
| `taxonomy:dietary` | `GET /api/v1/dietary-restrictions` | `useProfile` (`ProfilePage` cuando `loadTaxonomy: true`) |
| `branding:diner:{establishmentId}` | `GET /api/v1/establishments/{id}/branding` | `useRestaurantBranding` / `BrandThemeProvider` (`CatalogPage`, `CheckoutPage`, `OrderConfirmationTrackingPage`) |
| `branding:admin:{establishmentId}` | `GET /api/v1/admin/establishments/{id}/branding` | `WorkspaceLayout` (rol `ADMINISTRADOR`), `AdminBrandingPage` |
| `catalog:{tenantId}:{userScope}` | `GET /api/v1/establishments/{tenantId}/catalog` | `CatalogPage` (`userScope = user.id` o `'guest'`) |
| `establishments:all` | `GET /api/v1/establishments` | `EstablishmentSearchBar` en `EstablishmentDiscoveryPage` |
| `analytics:financial-health:{granularity}` | `GET /api/v1/admin/analytics/financial-health?granularity={day\|week\|month}` | `FinancialHealthPanel` en `AdminWorkspacePage` (`/admin`) y `AdminAnalyticsPage` (`/admin/analytics`) |
| `analytics:dish-ranking:{granularity}` | `GET /api/v1/admin/analytics/dish-ranking?granularity={all\|day\|week\|month}` | `DishRankingTable` en `AdminWorkspacePage` (`/admin`) y `AdminAnalyticsPage` (`/admin/analytics`) |

### Relationships y reglas de validación
1. **Deduplicación en vuelo**: Si `inFlight.has(key)`, cualquier consumidor adicional que solicite `key` recibe exactamente la misma `Promise` activa sin disparar una segunda petición HTTP; al resolverse o rechazarse (`finally`), se elimina de `inFlight`.
2. **Autoridad del backend**: El almacén no recalcula stock, precios ni conformidad dietaria en el navegador; siempre revalida contra la API del backend y reemplaza el valor completo con la respuesta autoritativa.
3. **Aislamiento de pruebas unitarias (FR-010)**: Cuando un componente o hook se monta fuera de `<ClientCacheProvider>` (como en suites Vitest existentes `useProfile.test.ts`, `CatalogPage.test.tsx`, `DinerCatalogBranding.test.tsx`), utiliza un almacén efímero aislado por instancia, garantizando cero contaminación cruzada entre tests sin modificar pruebas existentes.
4. **Reglas de purga sincrónica**:
   - `<ClientCacheProvider>` envuelve a `<AuthProvider>` en `App.tsx`, y `AuthContext` consume `useContext(ClientCacheContext)` de forma opcional (seguro en tests standalone).
   - `logout()`, `clearAuth()`, cambio de `user.id` en `setToken()` o evento `window` `'easyeat:session-expired'` (`401`): `store.clearAll()` (vacía `entries`, `inFlight` y `mediaVersions`).
   - Mutaciones de perfil (`updatePersonalData`, `updateDemographics`, `uploadAvatar`, `removeAvatar`, `requestEmailChange`, `cancelEmailChange`): actualiza o invalida `profile:me`; en `uploadAvatar`/`removeAvatar` actualiza `mediaVersions.set('avatar:me', Date.now())`.
   - `updateDietaryRestrictions`: invalida `profile:me` e invalida por prefijo `catalog:` (recalcula conformidad dietaria en la siguiente lectura).
   - Mutaciones de branding/logo en Admin (`AdminBrandingPage`): invalida `branding:admin:{id}` y `branding:diner:{id}`.

### State transitions (`CacheEntry` por clave)

```text
IDLE (sin entrada en entries)
  │ fetch inicial → muestra esqueleto existente (FR-008)
  ▼
LOADING_INITIAL
  ├── éxito 200 ──► FRESH (guardado en entries, notifica suscriptores)
  └── error red/5xx ──► ERROR_INITIAL (muestra pantalla de error + "Reintentar")

FRESH (visita repetida: render inmediato < 100 ms sin esqueleto)
  │ al montar vista con dato previo → dispara revalidación silenciosa
  ▼
REVALIDATING (datos visibles intactos; scroll, categoría, filtros y modales intactos)
  ├── éxito 200 ──► FRESH (actualiza datos silenciosamente)
  ├── error transitorio (≠ 401) ──► STALE_ON_BG_ERROR (conserva datos visibles, sin vista de error)
  └── 401 / logout / mutación ──► PURGED (entrada eliminada) ──► IDLE
```

---

## 5. Proyecciones auxiliares de lectura en lote y medios binarios

### 5.1 Proyección liviana de metadatos binarios (`BinaryMediaMetadataProjection` — FR-006)
- **Tablas fuente**: `dish_photos` (`dish_id`), `restaurant_logos` (`establishment_id`), `user_photos` (`user_id`).
- **Campos proyectados** (sin columna `data BYTEA`): `fileName (String)`, `contentType (String)`, `fileSize (Long)`, `updatedAt (Instant)`.
- **Cálculo determinista de `ETag`**: `"\"" + sha256Hex(resourceId + ":" + updatedAt.toEpochMilli() + ":" + fileSize + ":" + fileName) + "\""` (idéntico tanto al evaluarse desde la proyección liviana en el camino `If-None-Match` → `304` como desde la entidad completa en el camino `200 OK`).

### 5.2 Agregado ejecutivo del tablero KDS (`KdsDashboardSummaryProjection` — FR-004, FR-011)
- **Tabla fuente**: `kds_tickets`.
- **Campos proyectados**: `queuedOrdersCount (long)`, `inPreparationOrdersCount (long)`, `readyForPickupOrdersCount (long)`, `completedTodayCount (long)`.
- **Consulta única**: `SUM(CASE WHEN k.status = 'QUEUED' ...)` + `IN_PREPARATION` + `READY_FOR_PICKUP` + `COMPLETED AND k.pickupDate = :today`, filtrado por `k.establishment.id = :establishmentId`.
- **Determinismo horario y sincronización cliente (FR-011, D-12)**: `KitchenKdsController` resuelve `today` en la zona horaria del establecimiento (`America/Argentina/Buenos_Aires` vía `Clock` inyectable sin consultas SQL extra), y `KitchenWorkspacePage` sincroniza `fetchDashboardSummary` con el ciclo de 4 segundos de la cola activa, en `visibilitychange` e inmediatamente después de cada transición del Chef (`startTicketPreparation`, `markTicketReadyForPickup`, `addTicketDelay`).

### 5.3 Lote operativo de Mostrador, Gestión de Platos del Chef y Bandeja de Revisión Admin (FR-004)
- **Mostrador (`CounterOrderController`)**: a partir de `readyTickets` (`KdsTicketRepository.findCounterReadyTickets`), carga en 3 consultas constantes: `orderRepository.findAllByIdWithItems(orderIds)` (existente), `userRepository.findAllById(customerIds)` y `userPhotoRepository.findUserIdsWithPhoto(customerIds)`. Guarda `IN ()` si `readyTickets` o `customerIds` está vacío.
- **Chef (`DishService.listDishes` y `getOperationalStatus`)**: utiliza proyecciones JPQL livianas sobre `Dish` (`DishOperationalProjection` y `DishSummaryProjection`) que omiten `restrictionCodes` para evitar el `N+1` oculto de `@ElementCollection(fetch = FetchType.EAGER)`, combinadas con la consulta consolidada `dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(dishIds, tenantId)` y (solo `listDishes`) `dishPhotoRepository.findDishIdsWithPhoto(dishIds)` → ≤ 3 consultas SQL en `getOperationalStatus` y ≤ 4 consultas SQL constantes en `listDishes` (`SELECT` página + `COUNT` página + recetas/ingredientes consolidado + fotos).
- **Bandeja de Revisión Admin (`DishReviewInboxService`)**: ya ejecuta rechazos (`countRejectionsByDishIds`) y autores (`findAllById`) por lote; la anotación `@BatchSize(size = 100)` sobre `Dish.restrictionCodes` elimina además el `N+1` oculto al paginar entidades `Dish` sin modificar firmas ni contratos existentes.

---

## 6. Migración versionada de índices compuestos (FR-009)

Conforme a la regla 2 de `.specify/memory/migrations.md`, el número de versión Flyway se confirma al momento de autorar el archivo en la fase de implementación (la versión más alta actual en la rama `028-platform-load-time-optimization` es `V29__add_paid_at_to_orders.sql`, por lo que la candidata actual es **`V30__add_performance_composite_indexes.sql`**). El test de migración debe localizar el script por descripción y no llevar número de versión en el nombre de clase (regla 5), y el PR debe actualizar `.specify/memory/migrations.md` (regla 3).

### Índices incluidos y verificación de no duplicación

| Índice nuevo | Tabla y definición | Justificación de consulta (FR-009) | Relación con índices previos |
|---|---|---|---|
| `idx_dishes_public_catalog` | `ON dishes (tenant_id, category) WHERE status = 'PUBLISHED' AND is_active = TRUE AND paused_by_chef = FALSE` | Optimiza la lectura del catálogo público de platos publicados por establecimiento con filtrado parcial compacto en disco | Complementa `idx_dishes_catalog_visibility` (V17, índice completo sin predicado parcial) reduciendo tamaño de índice y facilitando escaneo por `(tenant_id, category)` |
| `idx_order_items_dish_order` | `ON order_items (dish_id, order_id)` | Optimiza el join por lote de `sumActiveReservedPortionsByDishIds` (`WHERE oi.dish_id IN (...)`) | `V16` solo creó `idx_order_items_order_id(order_id)`; no existía índice liderado por `dish_id` |
| `idx_temp_reservations_active_expires_order` | `ON temporary_reservations (expires_at, order_id) WHERE status = 'ACTIVE'` | Optimiza el filtro de reservas temporales activas no vencidas (`status = 'ACTIVE' AND expires_at > :now`) unido a `order_items` | `V18` creó `idx_unique_active_reservation_per_order(order_id)` y `idx_reservations_sweep(status, expires_at)`; el nuevo índice parcial permite `Index Only Scan` cubriendo `(expires_at, order_id)` para el join |

**Verificación de otros módulos**:
- `orders`: `idx_orders_slot_capacity` (V16) ya cubre exactamente el `GROUP BY time_slot_start` de FR-003.
- `kds_tickets`: `idx_kds_tickets_establishment_status` (V23), `idx_kds_tickets_active_queue` (V25) e `idx_kds_tickets_counter_ready` (V26) ya cubren KDS y Mostrador.
- `dish_recipe_ingredients`: `pk_dish_recipe_ingredients(dish_id, ingredient_id)` (V7) ya cubre exactamente `WHERE dish_id IN (...)`.

---

## 7. Migración versionada de backfill de `orders.paid_at` (FR-011, D-12)

Conforme a `.specify/memory/migrations.md` (reglas 2, 3 y 5), habiendo consumido `V30__add_performance_composite_indexes.sql` en la Fase 2, la siguiente versión disponible al autorar la reparación histórica de `orders.paid_at` es **`V31__backfill_missing_orders_paid_at.sql`**.

### Regla de reparación e invariante de acreditación
- **Alcance**: Toda fila en `orders` cuyo estado pertenezca al conjunto de órdenes con pago acreditado (`'PAID'`, `'IN_PROGRESS'`, `'READY_FOR_PICKUP'`, `'COMPLETED'`) y tenga `paid_at IS NULL`.
- **Fórmula determinista de backfill**:
  ```sql
  UPDATE orders o
  SET paid_at = COALESCE(
      (SELECT MAX(p.approved_at) FROM payments p WHERE p.order_id = o.id AND p.approved_at IS NOT NULL),
      o.completed_at,
      o.ready_at,
      o.updated_at,
      o.created_at
  )
  WHERE o.status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')
    AND o.paid_at IS NULL;
  ```
- **Invariante hacia adelante en Java**: `CheckoutConfirmationServiceImpl.confirmPayment`, `CheckoutConfirmationServiceImpl.confirmZeroAmountOrder` y `OrderConfirmationTrackingService.confirmAndApprovePayment` asignan `order.setPaidAt(now)` cuando `order.getPaidAt() == null` en la misma transacción que fija `OrderStatus.IN_PROGRESS`, garantizando que `idx_orders_analytics ON orders (establishment_id, paid_at) WHERE status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')` indexe el 100 % de las ventas concretadas.
