# Contrato 04 — Medios binarios condicionales (`ETag` / `304`) y compresión de transporte

**Trazabilidad**: US4, FR-006, FR-009, FR-010, SC-005, SC-006, decisión Q3.

---

## Regla transversal de validación condicional sin lectura `BYTEA`

En todos los endpoints binarios descritos a continuación:
1. Cuando la petición incluye cabecera `If-None-Match`, el controlador consulta únicamente la proyección liviana de metadatos (`BinaryMediaMetadataProjection`: `fileName`, `contentType`, `fileSize`, `updatedAt`) sin leer la columna `data (BYTEA)` desde PostgreSQL.
2. El `ETag` determinista se calcula como `"\"" + sha256Hex(resourceId + ":" + updatedAt.toEpochMilli() + ":" + fileSize + ":" + fileName) + "\""`.
3. Si `If-None-Match` coincide con el `ETag` calculado (tolerando formato con o sin comillas dobles), el servidor responde **`304 Not Modified` sin cuerpo**, incluyendo `ETag` y la cabecera `Cache-Control` exacta del recurso, con cero bytes de `BYTEA` cargados en el Heap de Java ni transferidos sobre el enlace intercontinental.
4. Cuando `If-None-Match` no está presente o no coincide, el servidor ejecuta `getPhoto`/`getLogo`, emite el mismo `ETag`, preserva `Cache-Control` y `Content-Type`, y devuelve `200 OK` con el contenido binario.
5. Todas las operaciones de subida (`uploadPhoto` / `storeLogo`) asignan explícitamente `updatedAt = Instant.now()` para garantizar un nuevo `ETag` ante cualquier reemplazo de archivo.

---

## 1. Fotografía de plato

- **Method**: `GET`
- **Path**: `/api/v1/dishes/{id}/photo`
- **Autenticación y roles**: `permitAll`.
- **Headers relevantes**:
  - Request: `If-None-Match: "<sha256-hex>"` (opcional).
  - Response:
    - `Cache-Control: private, max-age=86400` (**exacto**, verificado por `DishPhotoControllerTest:146`).
    - `ETag: "<sha256-hex>"` (emitido en `200` y `304`).
    - `Content-Type: image/jpeg | image/png | image/webp` (en `200`).
    - `Access-Control-Expose-Headers: ETag`.
- **Request schema**: Path param `id` (`UUID`, obligatorio).
- **Response schema**:
  - `200 OK`: cuerpo binario `byte[]` de `dish_photos.data`.
  - `304 Not Modified`: cuerpo vacío.
- **Status codes**: `200 OK`, `304 Not Modified`, `404 Not Found` (`application/problem+json`).
- **Notas de preservación FR-010**:
  - `DishPhotoControllerTest` (que mockea solo `getPhoto` sin cabecera `If-None-Match`) permanece 100 % intacto y en verde.
  - En las tarjetas del catálogo (`DishCard.tsx`), la etiqueta `<img>` incluye `loading="lazy"` y `decoding="async"` (FR-009) sin modificar estilos ni contrato `comensal.DESIGN.md`.
  - El refresco inmediato tras subida o eliminación de fotografía por el Chef se coordina en la sesión del navegador mediante `mediaVersions['dish:{id}']` en `ClientSWRStore` (anexando `?v=<timestamp>` solo cuando existe una versión registrada en memoria para no alterar `photoUrl` JSON ni pruebas standalone como `DishCard.test.tsx`); en otros navegadores rige el `Cache-Control: private, max-age=86400` contractual con revalidación condicional `ETag`/`304` al expirar o recargar (decisión Q3 + E1).

---

## 2. Logotipo de establecimiento

- **Method**: `GET`
- **Path**: `/api/v1/establishments/{establishmentId}/logo`
- **Autenticación y roles**: `permitAll`.
- **Headers relevantes**:
  - Request: `If-None-Match: "<sha256-hex>"` (opcional).
  - Response:
    - `Cache-Control: public, max-age=86400` (**exacto**, verificado por `DinerLogoEndpointTest:63`).
    - `ETag: "<sha256-hex>"` (emitido en `200` y `304`).
    - `Content-Type: image/jpeg | image/png | image/webp | image/jpg | image/svg+xml` (en `200`).
    - `Access-Control-Expose-Headers: ETag`.
- **Request schema**: Path param `establishmentId` (`UUID`, obligatorio).
- **Response schema**:
  - `200 OK`: cuerpo binario `byte[]` de `restaurant_logos.data`.
  - `304 Not Modified`: cuerpo vacío.
- **Status codes**: `200 OK`, `304 Not Modified`, `404 Not Found` (`application/problem+json`).
- **Notas de preservación FR-010**:
  - `DinerLogoEndpointTest` permanece 100 % intacto y en verde.
  - La URL JSON `logoUrl` devuelta por los endpoints de branding permanece exactamente `/api/v1/establishments/{establishmentId}/logo` (`AdminBrandingIntegrationTest:167-175`, `BrandingLogoStorageServiceTest:64`). El frontend añade `?v=<updatedAtEpoch>` a partir de `branding.updatedAt` al renderizar `<img>` para asegurar refresco inmediato ante cambios de logotipo (decisión Q3).

---

## 3. Avatar de usuario (acceso de personal) y avatar propio

### 3A. Avatar de cliente consultado por personal (Mostrador / Cocina / Admin)
- **Method**: `GET`
- **Path**: `/api/v1/profile/users/{userId}/avatar`
- **Autenticación y roles**: `hasAnyRole('ENCARGADO', 'CHEF', 'ADMINISTRADOR')` (validado vía `UserSessionCache`).
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>` o query `?token=<jwt>`, `If-None-Match: "<sha256-hex>"` (opcional).
  - Response:
    - `Cache-Control: private, max-age=3600` (**exacto**, verificado por `ProfileAvatarStaffControllerTest:109`).
    - `ETag: "<sha256-hex>"` (emitido en `200` y `304`).
    - `Content-Type: image/jpeg | image/png | image/webp` (en `200`).
    - `Access-Control-Expose-Headers: ETag`.
- **Request schema**: Path param `userId` (`UUID`, obligatorio).
- **Response schema**: `200 OK` (`byte[]`) o `304 Not Modified` (sin cuerpo).
- **Status codes**: `200 OK`, `304 Not Modified`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.
- **Notas de preservación FR-010**:
  - La firma Java `ProfileAvatarController.getUserAvatarById(UUID)` conserva exactamente 1 parámetro (verificada por reflexión en `ProfileAvatarStaffControllerTest:87`); `If-None-Match` se lee desde `RequestContextHolder`.

### 3B. Avatar del usuario autenticado
- **Method**: `GET`
- **Path**: `/api/v1/profile/avatar`
- **Autenticación y roles**: `authenticated` (validado vía `UserSessionCache`).
- **Headers relevantes**:
  - Request: `Authorization: Bearer <jwt>` o query `?token=<jwt>`, `If-None-Match: "<sha256-hex>"` (opcional).
  - Response:
    - `Cache-Control: private, max-age=86400` (preservado).
    - `ETag: "<sha256-hex>"` (emitido en `200` y `304`).
    - `Content-Type: image/jpeg | image/png | image/webp` (en `200`).
    - `Access-Control-Expose-Headers: ETag`.
- **Notas de preservación FR-010 (decisión Q3)**:
  - `getAuthenticatedAvatarUrl(url)` mantiene su contrato exacto (`token=` y `_t=\d+`) exigido por `tests/utils/avatarUtils.test.ts`. En `RoleLayoutShell` y `PersonalDataForm`, la URL autenticada se estabiliza en memoria por ciclo de vida y se regenera únicamente cuando se dispara `easyeat:avatar-changed` o cambia la URL/sesión, evitando romper el caché del navegador en cada re-renderizado.

---

## 4. Política transversal de compresión HTTP (GZIP)

- **Alcance**: Todos los endpoints REST del servidor (`server.compression.*` en `application.yml`).
- **Configuración contractual**:
  - `enabled`: `true`
  - `min-response-size`: `1024` bytes
  - `mime-types`: `application/json,application/problem+json`
- **Headers relevantes**:
  - Request: `Accept-Encoding: gzip`
  - Response (cuando el cuerpo supera `1024 bytes`): `Content-Encoding: gzip`, `Vary: Accept-Encoding`
- **Notas de preservación FR-010**:
  - Las imágenes binarias (`image/jpeg`, `image/png`, `image/webp`) quedan excluidas de `mime-types` para no desperdiciar CPU (0.1 CPU en Render Free) recomprimiendo formatos ya comprimidos.
  - No altera esquemas JSON, códigos HTTP ni cabeceras de seguridad existentes.
