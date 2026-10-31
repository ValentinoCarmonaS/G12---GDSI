# Technical Research & Architectural Decisions: Shopping Cart & Diner Demographics

**Feature Branch**: `009-shopping-cart`  
**Fecha**: 31/10/2026  
**Estado**: Completado  
**Referencia de Especificación**: [spec.md](./spec.md)

---

## Resumen de Investigación

Esta fase de investigación resuelve las decisiones de diseño arquitectónico y técnico para la extensión de la funcionalidad de carrito de compras y gestión demográfica del perfil de comensal, garantizando compatibilidad absoluta con las especificaciones previas (`001-central-auth-recovery` a `008-diner-catalog-multi-tenant`), el cumplimiento estricto de las 9 invariantes de negocio (INV-1 a INV-9) y los 15 criterios de aceptación (15/15).

---

## 1. Gestión de Sesión Local del Carrito y Aislamiento Multi-Tenant

### Contexto y Problema
El requerimiento funcional (FR-002, FR-004) y los criterios de aceptación de COM-6.1.1 y COM-6.1.2 exigen que el carrito de compras resida en la sesión local del comensal, manteniéndose reactivo sin recargas de página y estrictamente aislado por establecimiento gastronómico activo (`tenantId`).

### Decisión Técnica (D-001)
Reutilizar y extender la infraestructura existente de `CartContext` (`easyeat/frontend/src/context/CartContext.tsx`) respaldada por `sessionStorage` bajo la clave `'easyeat_cart'`, agregando el atributo `tenantId` en la estructura de almacenamiento de la sesión:
1. `sessionStorage` almacena un objeto serializado estructurado:
   ```typescript
   interface StoredCartSession {
     tenantId: string | null;
     items: CartItem[];
   }
   ```
2. Al cargar o inicializar el contexto, si el `tenantId` del establecimiento activo (obtenido de `TenantContext`) difiere del `tenantId` almacenado y existen ítems, se activa el diálogo de confirmación `EstablishmentSwitchModal` (introducido en la especificación 008, COM-1.5.1 CA 5) antes de purgar la sesión.
3. Se mantiene el aislamiento respecto a otros usuarios en la misma máquina gracias al alcance por pestaña/ventana de `sessionStorage`.

### Justificación
- **Persistencia no invasiva**: `sessionStorage` sobrevive a recargas accidentales de la página (F5) dentro de la misma pestaña, pero se destruye al cerrar la pestaña/navegador, lo que coincide con la semántica de "sesión local de comensal" sin generar basura en bases de datos para pedidos no confirmados.
- **Consistencia con Spec 008**: Se alinea directamente con `TenantContext` y la regla de negocio que previene mezclar platos de distintos restaurantes en un único pedido.

### Alternativas Descartadas
- **Persistencia en base de datos en servidor (`carts` / `cart_items` en PostgreSQL)**: Descartada porque el spec declara expresamente el carrito como sesión local (`sessionStorage`); almacenar carritos abandonados en backend introduce sobrecarga de I/O, locks concurrentes y tareas de limpieza (TTL/cron) innecesarias en esta iteración.
- **`localStorage`**: Descartada porque comparte estado entre múltiples pestañas en segundo plano, lo que generaría conflictos si un comensal explora dos restaurantes diferentes en pestañas simultáneas.

---

## 2. Selector Numérico Previo en Detalle de Plato e Incorporación Acumulativa

### Contexto y Problema
COM-6.1.1 (AC-1.1, FR-001, FR-002, FR-003) exige que la vista de detalle de plato (`DishDetailModal.tsx`) proporcione un selector numérico previo de cantidad (con valor por defecto 1) acotado entre 1 y el stock real disponible en cocina, permitiendo incorporar múltiples unidades en una única acción sin generar filas duplicadas para un mismo plato.

### Decisión Técnica (D-002)
Modificar `DishDetailModal.tsx` (`easyeat/frontend/src/components/catalog/DishDetailModal.tsx`) para:
1. Introducir un estado local `selectedQuantity` inicializado en 1 al abrir el modal para un plato determinado.
2. Determinar la cota superior del selector dinámicamente:
   $$\text{maxSelectable} = \max(0, \text{stockRealDisponible} - \text{cantidadYaEnCarrito})$$
3. Deshabilitar los controles de incremento si `selectedQuantity >= maxSelectable` o si `maxSelectable == 0`.
4. Extender la función `addItem` en `CartContextType` para aceptar la firma:
   ```typescript
   addItem: (dish: DishCatalogItem | EnrichedDish, quantity?: number, riskConfirmed?: boolean) => void;
   ```
   Si el plato ya existe con cantidad $k$, la nueva cantidad pasa a ser $k + m$, verificando estrictamente que $k + m \le \text{stockRealDisponible}$ (INV-3).
5. Desplegar una señal visual de notificación de éxito (toast temporal con auto-cierre de 3 segundos) informando: `"Se agregaron {m} unidad(es) de {plato.name} al carrito"`.

### Justificación
- **Cumplimiento estricto del acuerdo**: Resuelve directamente la aclaración de la sesión de grill-me del 31/10/2026.
- **Protección de stock en el origen**: Previene que el comensal intente añadir más unidades de las disponibles antes de enviar la orden al carrito.

### Alternativas Descartadas
- **Adición fija de 1 unidad por pulsación sin selector**: Descartada porque contradice explícitamente la aclaración aprobada de COM-6.1.1 en `spec.md`.

---

## 3. Botón Flotante Reactivo, Modal de Carrito y Precisión Monetaria Exacta

### Contexto y Problema
COM-6.1.2 (AC-2.1 a AC-2.4, FR-005 a FR-010) e INV-6/INV-7 exigen:
1. Visibilidad contextual biunívoca: visible si y solo si el carrito tiene $\ge 1$ ítem y el comensal se encuentra en las pantallas de catálogo (`/catalogo`) o detalle de plato. Oculto inmediatamente si el carrito queda vacío o al navegar a `/perfil`.
2. Exhibición continua de la cantidad total ($\text{cantidad\_total} = \sum q_i$) y subtotal acumulado ($\text{subtotal} = \sum p_i \times q_i$) sin recarga de página.
3. Precisión decimal monetaria exacta (INV-2) libre de anomalías de punto flotante.

### Decisión Técnica (D-003)
1. **Componente `FloatingCartButton.tsx`**:
   Crear `easyeat/frontend/src/components/cart/FloatingCartButton.tsx` ubicado en la esquina inferior derecha (`fixed bottom-6 right-6 z-40`), animado con transiciones suaves (`transition-all duration-300 transform`).
   - Se renderiza condicionalmente: `totalCount > 0 && isCatalogView`.
   - Muestra badge con `totalCount` e importe formateado con precisión de 2 decimales.
   - Al hacer click, dispara la apertura del drawer/modal del carrito (`setIsCartOpen(true)`).
2. **Aritmética Decimal de Punto Fijo (INV-2)**:
   Implementar una utilidad de cálculo monetario `currencyUtils.ts`:
   ```typescript
   export const toCents = (amount: number): number => Math.round((amount + Number.EPSILON) * 100);
   export const fromCents = (cents: number): number => cents / 100;
   export const calculateLineSubtotal = (price: number, quantity: number): number =>
     fromCents(toCents(price) * quantity);
   export const calculateCartTotal = (items: CartItem[]): number =>
     fromCents(items.reduce((acc, item) => acc + toCents(item.price) * item.quantity, 0));
   ```
   En `CartContext`, `totalAmount` se computa usando esta aritmética entera basada en centavos, garantizando coincidencia estricta y ausencia de drift (`0.1 + 0.2` o sumas de centavos fraccionarios como \$12,33 × 3 = \$36,99).
3. **Modal/Drawer Detallado**:
   Extender `CartDrawer.tsx` (`easyeat/frontend/src/components/cart/CartDrawer.tsx`) para incorporar controles de incremento/decremento unitario, cálculo de subtotales por fila en tiempo real y botón de vaciado rápido.

### Justificación
- **Cumplimiento de INV-1 e INV-2**: La conversión a centavos enteros elimina cualquier imprecisión de IEEE 754.
- **Ergonomía de usuario**: El botón flotante no obstruye formularios de perfil y se mantiene accesible en todo el recorrido de compra en el catálogo.

### Alternativas Descartadas
- **Uso directo de `number` con sumas nativas en JavaScript (`price * qty`)**: Descartada porque produce errores de punto flotante conocidos (ej. `36.990000000000006`), violando INV-2.
- **Botón estático exclusivamente en cabecera**: Descartada porque el spec exige explícitamente botón flotante en pantalla (`COM-6.1.2`, FR-005).

---

## 4. Modificación de Cantidades, Bloqueo de Stock y Reversión Determinista con Debounce

### Contexto y Problema
COM-6.1.3 (AC-3.1 a AC-3.5, FR-011 a FR-016) e INV-3/INV-9 requieren:
1. Suma/resta unitaria en el modal. Restar sobre cantidad 1 elimina el producto íntegramente.
2. Deshabilitar suma cuando la cantidad alcance el stock real disponible en cocina.
3. Ante modificaciones rápidas, actualizar optimistamente la interfaz y consolidar con un debounce de 300 ms contra el backend. Si la red falla o el stock es insuficiente, retrotraer de inmediato al último valor confirmado y notificar al usuario.

### Decisión Técnica (D-004)
1. **Actualización Optimista y Debounce**:
   En `CartContext` o un hook dedicado `useCartModifier`, cada pulsación de `+` o `-` actualiza el estado local de forma inmediata. Se mantiene para cada ítem una referencia a `lastSyncedQuantity`.
2. **Temporizador de Debounce (300 ms)**:
   Al registrar cambios, se activa un temporizador de 300 ms. Al vencer el lapso, el cliente ejecuta una validación de disponibilidad contra el servicio de catálogo (aprovechando `/api/v1/establishments/{tenantId}/catalog/availability` o el endpoint de validación de ítems).
3. **Mecanismo de Rollback Determinista**:
   Si la petición falla por error de red (HTTP 5xx, timeout, pérdida de conexión `TypeError: Failed to fetch`) o por respuesta de stock insuficiente:
   - Se revierte la cantidad del ítem en `CartContext` a su `lastSyncedQuantity`.
   - Se recalcula de inmediato el total del pedido.
   - Se dispara una notificación emergente visible: `"Error de conexión: se restablecieron las cantidades de {plato.name}"`.
4. **Eliminación Total en Cantidad 1**:
   Al pulsar `-` sobre un ítem con cantidad 1, se remueve el ítem del arreglo de `items`. Si el carrito queda vacío, se cierra el modal o se muestra el estado vacío y el botón flotante se oculta automáticamente (INV-6).

### Justificación
- **Experiencia de usuario fluida**: El usuario no experimenta bloqueos ni latencia perceptible en la botonera.
- **Integridad de datos**: El rollback previene que el comensal crea haber reservado productos inexistentes o que se generen pedidos con cantidades discordantes.

### Alternativas Descartadas
- **Llamada de red síncrona bloqueante en cada pulsación**: Descartada porque congelaría la UI o generaría race conditions severas ante ráfagas de clicks rápidos.
- **Ignorar fallo de red y dejar la cantidad modificada localmente**: Descartada porque viola INV-9 y provocaría fallos críticos posteriores en checkout.

---

## 5. Edición de Datos Demográficos, Validación y Manejo de Foto de Perfil

### Contexto y Problema
COM-1.4.2 (AC-4.1 a AC-4.6, FR-018 a FR-032) e INV-4, INV-5, INV-8 requieren:
1. Foto de perfil: Formatos permitidos JPEG, PNG, WEBP. Tamaño estrictamente mayor a 0 y menor o igual a 5 MB (5.242.880 bytes).
2. Rechazo con mensajes específicos ante exceso de peso o formato no soportado.
3. Eliminación de foto con diálogo de confirmación diferido: al confirmar el diálogo, la UI muestra el avatar por defecto provisionalmente, pero la eliminación efectiva en backend y persistencia final se consolida únicamente al presionar "Guardar cambios".
4. Edad: entero entre 14 y 120 años inclusive. Rechazo estricto de negativos, decimales o alfanuméricos.
5. Género: opciones "Femenino", "Masculino", "No binario", "Prefiero no responder" (opcional).
6. Domicilio: texto libre de hasta 255 caracteres, tratado exclusivamente como dato de contacto sin ruteo ni logística.
7. Reflejo inmediato del avatar actualizado en la barra de navegación superior (`WorkspaceLayout.tsx`).

### Decisión Técnica (D-005)
1. **Modelo de Persistencia Relacional (PostgreSQL - Flyway `V10`)**:
   - Crear tabla `user_photos` idéntica al patrón probado de `dish_photos` (`V7`):
     ```sql
     CREATE TABLE IF NOT EXISTS user_photos (
         user_id      UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
         file_name    VARCHAR(255) NOT NULL,
         content_type VARCHAR(50)  NOT NULL,
         file_size    BIGINT       NOT NULL,
         data         BYTEA        NOT NULL,
         created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
         updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
         CONSTRAINT chk_user_photos_content_type
             CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
         CONSTRAINT chk_user_photos_file_size
             CHECK (file_size > 0 AND file_size <= 5242880)
     );
     ```
   - Extender la tabla `users` mediante columnas y constraints de dominio:
     ```sql
     ALTER TABLE users
         ADD COLUMN IF NOT EXISTS age        INTEGER,
         ADD COLUMN IF NOT EXISTS gender     VARCHAR(50),
         ADD COLUMN IF NOT EXISTS address    VARCHAR(255);

     ALTER TABLE users
         ADD CONSTRAINT chk_users_age_range
             CHECK (age IS NULL OR (age >= 14 AND age <= 120)),
         ADD CONSTRAINT chk_users_gender_valid
             CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY', 'Femenino', 'Masculino', 'No binario', 'Prefiero no responder')),
         ADD CONSTRAINT chk_users_address_length
             CHECK (address IS NULL OR length(address) <= 255);
     ```
2. **Servicio y Controlador Backend**:
   - Extender `ProfileController.java` y `ProfileService.java` en `com.easyeat.profile`:
     - `PUT /api/v1/profile/demographics`: Actualiza `age`, `gender`, `address` y estado de foto de manera transaccional.
     - `POST /api/v1/profile/avatar`: Valida tamaño ($\le 5.242.880$ bytes) y magic bytes (JPEG/PNG/WEBP) vía `UserPhotoService`, persistiendo el binario en `user_photos`.
     - `DELETE /api/v1/profile/avatar`: Remueve el registro de `user_photos`.
     - `GET /api/v1/profile/avatar`: Sirve el stream binario del avatar del usuario autenticado con cabeceras `Content-Type` y `Cache-Control`.
   - Extender `ProfileResponse` para incluir `age`, `gender`, `address`, `avatarUrl`.
3. **Flujo de Eliminación Diferida en Frontend (`PersonalDataForm.tsx`)**:
   - El formulario mantiene estados locales transitorios: `pendingPhotoFile`, `removePhotoPending: boolean`, `previewAvatarUrl`.
   - Si el comensal confirma la eliminación en el modal, se marca `removePhotoPending = true`, `previewAvatarUrl = null` y se exhibe el avatar institucional genérico.
   - Solo al pulsar "Guardar cambios", si `removePhotoPending === true`, se ejecuta la llamada a `DELETE /api/v1/profile/avatar`; si hay un `pendingPhotoFile`, se envía a `POST /api/v1/profile/avatar`; y luego se actualiza `PUT /api/v1/profile/demographics`.
   - Si el comensal navega fuera sin guardar, los estados transitorios se descartan y la foto original permanece intacta.
4. **Actualización de Avatar en `WorkspaceLayout.tsx`**:
   - `WorkspaceLayout.tsx` consume el perfil desde `useProfile()` o `useAuth()`. Si `profile.avatarUrl` existe, renderiza `<img>` circular; si es nulo, renderiza el avatar SVG predeterminado con iniciales o silueta.

### Justificación
- **Coherencia arquitectónica**: Reutiliza exactamente el patrón de `DishPhotoService` y `dish_photos` para almacenamiento y validación de cabeceras/magic bytes.
- **Seguridad en BD**: Las restricciones `CHECK` garantizan que ningún dato corrupto pueda guardarse aún si se esquivaran las validaciones de frontend y Spring Boot.
- **Aislamiento de Domicilio**: `address` es un simple texto de contacto, sin disparar dependencias con APIs de mapas ni servicios de logística (INV-8).

### Alternativas Descartadas
- **Almacenar fotos en un bucket S3 / Cloud Storage externo**: Descartada porque el repositorio EasyEat no cuenta con infraestructura cloud ni credenciales S3 configuradas; el proyecto estandarizó el almacenamiento en PostgreSQL BYTEA (`V7`).
- **Eliminar la foto inmediatamente al pulsar el diálogo**: Descartada porque viola expresamente el requerimiento de confirmación diferida de la especificación (FR-025).

---

## 6. Coexistencia y Compatibilidad con Especificaciones Previas

| Especificación | Interfaz / Componente Reutilizado | Estrategia de Compatibilidad |
|---|---|---|
| **Spec 001 / 002** | `User`, `UserRole`, `RoleRouteGuard`, `SecurityConfig` | El acceso a `/catalogo`, `/perfil`, endpoints de avatar y carrito continúa restringido a usuarios autenticados con rol `COMENSAL`. Sin alteraciones en login ni tokens JWT. |
| **Spec 003** | `CartContext`, `AllergenWarningBadge`, `AddToCartRiskModal`, `ProfileResponse` | Los ítems del carrito preservan los indicadores dietarios (`isCompliant`, `conflictingRestrictions`, `riskConfirmed`). `ProfileResponse` se amplía con campos opcionales sin romper consumidores previos. |
| **Spec 004 / 005 / 006** | `DishRecipeIngredient`, `Ingredient`, `dish_photos` | El cálculo de stock de platos se basa en las recetas y existencias de cocina existentes sin modificar su esquema. |
| **Spec 007** | `Dish.retail_price` (PVP vigente) | El carrito utiliza estrictamente el precio de venta al público publicado (`retail_price`). |
| **Spec 008** | `TenantContext`, `EstablishmentSwitchModal`, `dinerCatalogApi`, `useRealtimeAvailability` | El carrito se asocia al `tenantId` activo. Cambiar de restaurante activa el diálogo de advertencia para vaciar la orden (COM-1.5.1 CA 5). |

---

## Resumen de Resolución de Dudas Técnicas (NEEDS CLARIFICATION)

Todas las áreas técnicas identificadas han sido resueltas a partir de la evidencia observable en el repositorio y la especificación aprobada:
- **Estado de Clarificaciones**: 0 pendientes.
- **Alineación con Constitution 2.0.0**: Total.
- **Próximo Paso**: Avanzar a la Fase 1 (Modelado de datos conceptual, contratos y quickstart).
