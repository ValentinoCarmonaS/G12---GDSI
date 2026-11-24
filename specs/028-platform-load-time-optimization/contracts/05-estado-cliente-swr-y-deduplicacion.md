# Contrato 05 — Coordinación de estado cliente (SWR, deduplicación en vuelo y metadatos cacheados)

**Trazabilidad**: US1, US4, FR-005, FR-007, FR-008, FR-010, SC-003, SC-004, SC-005, D-08, D-09.

---

## 1. Endpoints REST coordinados por el almacén cliente (`ClientSWRStore`)

Ninguno de estos endpoints modifica su ruta, payload JSON ni códigos de estado HTTP respecto de las especificaciones `001`–`027`.

### 1A. Perfil del usuario autenticado
- **Method / Path**: `GET /api/v1/profile/me`
- **Clave cliente**: `profile:me`
- **Headers relevantes**: `Authorization: Bearer <jwt>`, `Accept-Encoding: gzip` → `Content-Encoding: gzip` (≥ 1024 B).
- **Status codes**: `200 OK`, `401 Unauthorized`, `403 Forbidden`.
- **Coordinación cliente (FR-007 / SC-004)**:
  - Deduplicado en vuelo entre `WorkspaceLayout` (invocado únicamente para rol `COMENSAL`) y `useProfile` (`CatalogPage` con `{ enabled: isAuthenticated, loadTaxonomy: false }`, `ProfilePage` con `{ enabled: true, loadTaxonomy: true }`).
  - Al montar `/catalogo` autenticado: exactamente **1** solicitud `GET /api/v1/profile/me` y **0** solicitudes a `GET /api/v1/dietary-restrictions`. En modo invitado: **0** solicitudes a ambos.

### 1B. Taxonomía maestra de restricciones dietarias
- **Method / Path**: `GET /api/v1/dietary-restrictions`
- **Clave cliente**: `taxonomy:dietary`
- **Respaldo servidor (FR-005)**: Servido desde `DietaryTaxonomyCache` (`maximumSize = 1`, `expireAfterWrite = 60 min`).
- **Status codes**: `200 OK`.
- **Coordinación cliente**: Cargado bajo demanda en `ProfilePage` (o cuando `useProfile()` se invoca sin opciones, preservando `tests/hooks/useProfile.test.ts`) y reutilizado en memoria durante toda la sesión.

### 1C. Identidad visual (branding) del establecimiento
- **Method / Path**:
  - Vista Comensal (`permitAll`): `GET /api/v1/establishments/{establishmentId}/branding` → clave `branding:diner:{establishmentId}`
  - Vista Administrador (`ADMINISTRADOR`): `GET /api/v1/admin/establishments/{establishmentId}/branding` → clave `branding:admin:{establishmentId}`
- **Respaldo servidor (FR-005)**: Servido desde `BrandingCache` con invalidación sincrónica en `PUT /api/v1/admin/establishments/{id}/branding` y `POST /api/v1/admin/establishments/{id}/branding/logo`.
- **Status codes**: `200 OK`, `401 Unauthorized` / `403 Forbidden` (vista Admin).
- **Coordinación cliente (FR-007 / FR-008)**:
  - Deduplicado en vuelo y compartido entre `BrandThemeProvider` en `/catalogo`, `/checkout` y `/pedidos/:orderId/seguimiento` (evitando re-fetches bloqueantes al cambiar de pantalla).
  - En Admin, deduplicado entre `WorkspaceLayout` y `AdminBrandingPage` en `/admin/mi-local`.

### 1D. Descubrimiento de establecimientos
- **Method / Path**: `GET /api/v1/establishments`
- **Clave cliente**: `establishments:all`
- **Autenticación y roles**: `hasRole('COMENSAL')`.
- **Status codes**: `200 OK`, `401 Unauthorized`, `403 Forbidden`.
- **Coordinación cliente (FR-008)**:
  - En primera visita sin datos en memoria: muestra indicador de carga y manejo de error existentes.
  - En visitas repetidas a `/descubrimiento`: muestra al instante (< 100 ms) el listado previo mientras revalida silenciosamente en segundo plano.
  - Las búsquedas filtradas (`GET /api/v1/establishments/search?q=...`) mantienen su comportamiento vigente con debounce de 300 ms.

---

## 2. Contrato de interfaz interna del `ClientSWRStore` y compatibilidad de hooks (FR-010)

### Operaciones del almacén (`ClientCacheContext`)
- `get<T>(key: string): CacheEntry<T> | undefined` — lectura sincrónica del dato en memoria para render inmediato (< 100 ms).
- `fetchDeduped<T>(key: string, fetcher: () => Promise<T>): Promise<T>` — si existe promesa en `inFlight` para `key`, la reutiliza; si no, ejecuta `fetcher()`, actualiza `entries` en éxito, notifica a los suscriptores y limpia `inFlight` en `finally`.
- `set<T>(key: string, data: T): void` — actualiza sincrónicamente una entrada y notifica suscriptores (útil tras mutaciones optimistas respaldadas por respuesta 200 del servidor, como `updatePersonalData` o `updateDemographics`).
- `invalidate(keyOrPrefix: string, options?: { prefix?: boolean }): void` — elimina una clave o prefijo (ej. `catalog:` tras `updateDietaryRestrictions`).
- `clearAll(): void` — purga completa sincrónica en `logout`, `clearAuth`, cambio de `user.id` en `setToken` (invocada vía `useContext(ClientCacheContext)` opcional desde `AuthContext` con `<ClientCacheProvider>` envolviendo a `<AuthProvider>` en `App.tsx`) y evento `easyeat:session-expired` (`401`).
- `setMediaVersion(mediaKey: string, version: number): void` / `getMediaVersion(mediaKey: string): number | undefined` — versionado liviano en memoria por sesión (`dish:{id}`, `avatar:me`) purgado en `clearAll()`.
- `subscribe(key: string, listener: () => void): () => void` — suscripción reactiva para mantener sincronizados `WorkspaceLayout`, `CatalogPage`, `ProfilePage` y `BrandThemeProvider`.

### Reglas de preservación de contratos de hooks y pruebas existentes (FR-010)

| Hook / componente | Extensión opcional (retrocompatible) | Comportamiento por defecto preservado |
|---|---|---|
| `useProfile(options?)` | `options?: { enabled?: boolean; loadTaxonomy?: boolean }` | `enabled = true`, `loadTaxonomy = true` — `tests/hooks/useProfile.test.ts` pasa sin cambios |
| `useCatalog(overrideTenantId?, options?)` | `options?: { enabled?: boolean }` | `enabled = true` — `tests/hooks/useCatalog.test.tsx` y `tests/pages/CatalogPage.test.tsx` pasan sin cambios |
| `useRealtimeAvailability(options)` | `deferInitialPoll?: boolean` | `deferInitialPoll = false` — `src/test/catalog/useRealtimeAvailability.test.ts` y `tests/hooks/useRealtimeAvailability.test.ts` pasan sin cambios |
| `useRestaurantBranding(establishmentId)` | Sin cambio de firma | Usa `ClientCacheContext` cuando existe `<ClientCacheProvider>` y almacén aislado en tests standalone — `DinerCatalogBranding.test.tsx` pasa sin cambios |
| `CatalogPage` | Conserva `data-testid` de esqueletos (`catalog-loading`, `catalog-loading-skeleton`) y errores (`catalog-error`, `catalog-error-state`, `catalog-retry-button`) | En primera carga sin caché muestra exactamente los mismos esqueletos y errores; en revalidación SWR nunca borra datos válidos ni reinicia scroll, categoría activa, filtros ni modales |

### Frontera de autoridad de negocio
- El cliente **nunca** calcula ni cachea como definitivo el balance de stock, disponibilidad de porciones, reservas temporales ni conformidad dietaria de nuevos platos; el backend es siempre la única fuente de autoridad en cada revalidación y en cada sondeo de disponibilidad.
