# Behavioral Contracts: Shopping Cart & Diner Demographics

**Feature Branch**: `009-shopping-cart`  
**Fecha**: 31/10/2026  
**Estado**: Completado  
**Referencia de Especificación**: [spec.md](./spec.md)

---

## 1. Operación: `AddToCartFromDishDetail` (Agregar plato al pedido desde detalle)

- **Identificador Funcional**: `AddToCartFromDishDetail`
- **Capa Principal**: Frontend (`CartContext` + `DishDetailModal.tsx`)
- **Entrada**:
  - `dishId`: String / UUID (no nulo, identificador de plato válido).
  - `quantity`: Integer (obligatorio, $1 \le \text{quantity} \le \text{availableStock}$).
  - `riskConfirmed`: Boolean (opcional, true si el comensal aceptó el aviso dietario de spec 003).
- **Salida**:
  - Sesión local de carrito actualizada (`sessionStorage`).
  - Evento de notificación emergente de éxito: `{ type: 'SUCCESS', message: 'Se agregaron {quantity} unidad(es) de {name} al carrito' }`.
- **Invariantes Garantizadas**:
  - **INV-1**: Cantidad total recalculada como suma de cantidades unitarias.
  - **INV-2**: Subtotales y total calculados con precisión monetaria exacta en centavos enteros.
  - **INV-3**: La cantidad acumulada del plato $k + m \le \text{stockRealDisponible}$.
  - **INV-7**: Botón flotante pasa a estado visible si no lo estaba.
- **Errores y Manejo**:
  - `STOCK_EXCEEDED`: Si $k + m > \text{stockRealDisponible}$, la acción se deshabilita visualmente en el selector. Si se fuerza, emite advertencia de inventario sin modificar el carrito.
  - `DISH_OUT_OF_STOCK`: Si $\text{stockRealDisponible} = 0$, botón "Agregar al Carrito" se presenta deshabilitado con etiqueta "Agotado".
- **Precondiciones**:
  - El comensal navega en el catálogo de un establecimiento activo (`tenantId`).
  - El plato pertenece al menú publicado de dicho establecimiento.
- **Postcondiciones**:
  - El ítem se encuentra en `CartContext.items`. Si ya existía, su cantidad es $k + m$; si es nuevo, se incorpora como una nueva fila.
  - La cantidad total y el subtotal monetario del carrito aumentan correspondientemente.
- **ACs Cubiertos**: COM-6.1.1 AC-1.1, AC-1.2, AC-1.3, AC-1.4, AC-1.5; COM-6.1.2 AC-2.1, AC-2.4.
- **Backward Compatibility**: Extiende la firma de `addItem` en `CartContext` preservando compatibilidad con llamadas anteriores que no pasaban `quantity` (default = 1).

---

## 2. Operación: `ModifyCartItemQuantity` (Modificar cantidad o remover en carrito)

- **Identificador Funcional**: `ModifyCartItemQuantity`
- **Capa Principal**: Frontend (`CartDrawer.tsx` / `CartContext`) con sincronización diferida hacia Backend (`/api/v1/establishments/{tenantId}/catalog/availability` o `/api/v1/cart/validate-stock`)
- **Entrada**:
  - `dishId`: UUID (no nulo).
  - `delta`: Integer (`+1` o `-1`).
- **Salida**:
  - Estado optimista actualizado en `CartContext`.
  - Si la nueva cantidad resultante es 0: eliminación total de la fila del ítem.
  - Recálculo en tiempo real de subtotal del ítem, cantidad total y monto acumulado.
- **Invariantes Garantizadas**:
  - **INV-1**: $\text{cantidad\_total} = \sum q_i$.
  - **INV-2**: Aritmética decimal exacta en centavos para subtotales y total general.
  - **INV-3**: Cantidad no supera el stock en cocina.
  - **INV-6**: Si $\text{totalCount} = 0$, el botón flotante se oculta de inmediato.
  - **INV-9**: Sincronización diferida (debounce 300 ms). Ante fallo de red o rechazo de stock, reversión determinista al estado confirmado previo y notificación de error.
- **Errores y Manejo**:
  - `NETWORK_FAILURE`: Pérdida de conectividad o HTTP 5xx durante el debounce → El contador se retrotrae a `lastSyncedQuantity` y se notifica al comensal `"Error de conexión: se restablecieron las cantidades de {name}"`.
  - `CONCURRENT_STOCK_SHORTFALL`: Si el stock en cocina se redujo concurrentemente y no alcanza para cubrir la suma → Se revierte la cantidad y se marca el ítem con advertencia visual de inventario alcanzado.
- **Precondiciones**:
  - El plato se encuentra presente en el carrito con cantidad $\ge 1$.
- **Postcondiciones**:
  - Si $\text{nuevaCantidad} > 0$: el ítem permanece con la nueva cantidad.
  - Si $\text{nuevaCantidad} = 0$: el ítem es purgado de la colección de ítems. Si era el único ítem, el carrito queda vacío y el botón flotante exterior se oculta.
- **ACs Cubiertos**: COM-6.1.3 AC-3.1, AC-3.2, AC-3.3, AC-3.4, AC-3.5; COM-6.1.2 AC-2.2, AC-2.4.
- **Backward Compatibility**: Reutiliza `updateQuantity` y `removeItem` de `CartContext`, introduciendo la capa de debounce y rollback transparente para los componentes visuales.

---

## 3. Operación: `UpdateDinerDemographics` (Actualizar datos demográficos)

- **Identificador Funcional**: `UpdateDinerDemographics`
- **Capa Principal**: Full-stack (`PersonalDataForm.tsx` → `PUT /api/v1/profile/demographics`)
- **Entrada (HTTP Request Body)**:
  - `age`: Integer opcional ($14 \le \text{age} \le 120$).
  - `gender`: String opcional (`"Femenino"`, `"Masculino"`, `"No binario"`, `"Prefiero no responder"` o constantes en inglés).
  - `address`: String opcional (longitud $\le 255$ caracteres).
- **Salida (HTTP 200 OK)**:
  - `ExtendedProfileResponse` con los datos actualizados: `id`, `fullName`, `phone`, `email`, `age`, `gender`, `address`, `avatarUrl`, `dietaryRestrictions`.
- **Invariantes Garantizadas**:
  - **INV-5**: Rango biológico de edad $14 \le \text{edad} \le 120$ garantizado en cliente y forzado por constraint `chk_users_age_range` en PostgreSQL.
  - **INV-8**: El domicilio se almacena exclusivamente como texto de contacto descriptivo; ninguna subrutina ejecuta cálculo de tarifas, ruteo ni llamadas a geocodificadores.
- **Errores y Manejo**:
  - `HTTP 400 Bad Request` con RFC 7807 `ProblemDetail`:
    - `age < 14` o `age > 120` o alfanumérico → `field: "age"`, `message: "La edad debe ser un número entero entre 14 y 120 años."`
    - `address.length > 255` → `field: "address"`, `message: "El domicilio no puede exceder los 255 caracteres."`
    - `gender inválido` → `field: "gender"`, `message: "El género seleccionado no es válido."`
  - `HTTP 401 Unauthorized`: Token ausente o expirado.
- **Precondiciones**:
  - Comensal autenticado con rol `COMENSAL`.
- **Postcondiciones**:
  - Los campos demográficos en la tabla `users` quedan actualizados de manera transaccional.
  - Los datos personales base (nombre, apellido, email, teléfono) y preferencias dietarias de spec 003 se conservan intactos.
- **ACs Cubiertos**: COM-1.4.2 AC-4.4, AC-4.5, AC-4.6; FR-018, FR-026, FR-027, FR-028, FR-029, FR-030, FR-031.
- **Backward Compatibility**: Si el cliente no envía `age`, `gender` o `address`, los valores preexistentes se mantienen o quedan como nulos/no informados.

---

## 4. Operación: `UploadProfileAvatar` (Subir foto de perfil)

- **Identificador Funcional**: `UploadProfileAvatar`
- **Capa Principal**: Full-stack (`POST /api/v1/profile/avatar`, `multipart/form-data`)
- **Entrada**:
  - `file`: MultipartFile (campo obligatorio).
- **Salida (HTTP 200 OK)**:
  - `AvatarUploadResponse`: `{ userId, fileName, contentType, fileSize, avatarUrl: "/api/v1/profile/avatar" }`.
- **Invariantes Garantizadas**:
  - **INV-4**: $0 < \text{fileSize} \le 5.242.880$ bytes (5 MB exactos, límite inclusivo).
  - Formato MIME válido: `image/jpeg`, `image/png`, `image/webp` verificado por magic bytes.
  - Reflejo inmediato del nuevo avatar en la barra de navegación superior (`WorkspaceLayout.tsx`).
- **Errores y Manejo**:
  - `HTTP 400 Bad Request` con RFC 7807 `ProblemDetail`:
    - `file.size == 0` → `message: "El archivo de imagen no puede estar vacío."`
    - `file.size > 5242880` → `message: "El archivo supera el tamaño máximo permitido de 5 MB."`
    - Tipo no admitido (ej. PDF, SVG, GIF, EXE) → `message: "Formato no soportado. Debe ser JPEG, PNG o WebP."`
  - `HTTP 401 Unauthorized`: Usuario no autenticado.
- **Precondiciones**:
  - Comensal autenticado con rol `COMENSAL`.
- **Postcondiciones**:
  - El binario queda almacenado en la tabla `user_photos` (upsert por `user_id`).
  - La URL del avatar queda disponible para descarga.
- **ACs Cubiertos**: COM-1.4.2 AC-4.1, AC-4.2, AC-4.3; FR-019, FR-020, FR-021, FR-022, FR-023.
- **Backward Compatibility**: Si no existía foto previa, inserta; si ya existía una foto previa, reemplaza el registro sin duplicar almacenamiento.

---

## 5. Operación: `RemoveProfileAvatar` (Eliminar foto de perfil)

- **Identificador Funcional**: `RemoveProfileAvatar`
- **Capa Principal**: Full-stack (`DELETE /api/v1/profile/avatar`)
- **Entrada**:
  - Sin cuerpo (la identidad del usuario se extrae del token JWT del comensal autenticado).
- **Salida (HTTP 204 No Content)**:
  - Registro de foto removido de la base de datos.
- **Invariantes Garantizadas**:
  - Confirmación diferida al formulario (FR-024, FR-025, AC-4.5): La llamada HTTP solo se dispara cuando el usuario confirma en el modal de verificación Y presiona "Guardar cambios" en el formulario.
  - Al consolidarse, la barra de navegación y el perfil restablecen de inmediato el avatar institucional por defecto.
- **Errores y Manejo**:
  - `HTTP 401 Unauthorized`: Token ausente o expirado.
  - Idempotencia: Si el usuario ya no poseía foto, la operación responde 204 sin generar error ni inconsistencia.
- **Precondiciones**:
  - Comensal autenticado con rol `COMENSAL`.
  - Usuario confirmó la acción en el diálogo y guardó los cambios del formulario demográfico.
- **Postcondiciones**:
  - El registro en `user_photos` para `user_id` es eliminado.
  - `profile.avatarUrl` pasa a ser `null`, activando el renderizado del fallback institucional en la barra superior.
- **ACs Cubiertos**: COM-1.4.2 AC-4.5; FR-024, FR-025; SC-008.
- **Backward Compatibility**: Si el usuario no tiene foto y llama al endpoint, se comporta de manera segura e idempotente.
