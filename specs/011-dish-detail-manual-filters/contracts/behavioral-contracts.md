# Contratos de Comportamiento: Detalle de Plato, Precios, Nutrición y Filtros Manuales

**Feature Branch**: `011-dish-detail-manual-filters`  
**Fecha**: 01/11/2026  
**Referencia de Especificación**: [spec.md](../spec.md) · [research.md](../research.md) · [data-model.md](../data-model.md)

---

## 1. Catálogo de Contratos de Comportamiento

Este documento define la especificación rigurosa de las 7 operaciones requeridas para la feature 011, detallando actores, entradas, salidas, invariantes, condiciones de error, pre/postcondiciones y garantías de compatibilidad y concurrencia.

---

### Contrato 1: Obtener Detalle de Plato para Panel Superpuesto

- **Identificador Funcional**: `OP-011-01: ObtenerDetallePlato`
- **Actor**: Comensal autenticado (`ROLE_COMENSAL`).
- **Entrada**:
  - `tenantId`: UUID obligatorio del establecimiento activo.
  - `dishId`: UUID obligatorio del plato seleccionado.
- **Salida**:
  - `id`: UUID del plato.
  - `name`: Nombre comercial (cadena no vacía, hasta 100 caracteres).
  - `description`: Descripción comercial detallada (cadena o null).
  - `category`: Nombre de la categoría gastronómica asignada.
  - `retailPrice`: Valor monetario final con impuestos incluidos (decimal exacto con 2 decimales).
  - `photoUrl`: Ruta de descarga `/api/v1/dishes/{dishId}/photo` o `null` si no posee fotografía registrada.
  - `isAvailable`: Booleano calculado a partir del inventario de insumos en cocina y activación del plato.
  - `recipeIngredients`: Lista de cadenas con los nombres de ingredientes de la receta activa en cocina. Si no hay receta, lista vacía.
  - `allergens`: Lista de etiquetas identificadoras neutras de alérgenos configurados (ej. `["Gluten", "Lactosa"]`).
- **Invariantes Garantizadas**:
  - El precio devuelto es estrictamente el PVP unitario con impuestos incluidos (INV-9).
  - Las etiquetas de alérgenos son objetivas y neutrales para todos los comensales, sin dependencias del perfil dietario (Clarification #4, INV-6).
  - Si el plato carece de foto, `photoUrl` es `null`, indicando al panel que no debe reservar espacio ni mostrar ícono roto (INV-2, FR-003).
- **Errores**:
  - `tenantId` no existe → HTTP 404 `EstablishmentNotFoundException`.
  - Establecimiento inactivo/pausado → HTTP 403 `EstablishmentInactiveException`.
  - `dishId` no pertenece al `tenantId` o no existe → HTTP 404 `DishNotFoundException`.
  - Comensal no autenticado → HTTP 401 `Unauthorized` (ProblemDetail).
- **Precondiciones**:
  - Comensal autenticado navegando en el catálogo del establecimiento `tenantId`.
- **Postcondiciones**:
  - El panel superpuesto recibe los datos completos para renderizado sin abandonar la vista del catálogo ni alterar la coordenada de scroll vertical.
- **Cubre**: AC-1.1, AC-1.2, AC-2.1, AC-2.3, AC-3.1, AC-3.2, INV-1, INV-2, INV-6, INV-8, INV-9, INV-10, FR-001, FR-002, FR-003, FR-008, FR-010, FR-011, FR-012, SC-001, SC-003, SC-006, SC-007, EC-001, EC-007, EC-008.
- **Backward Compatibility**:
  - Extiende el DTO `DishCatalogItemDto` existente incorporando el campo neutral `allergens` sin romper consumidores previos.
- **Concurrencia / Atomicidad**:
  - Lectura consistente aislada por transacción (`@Transactional(readOnly = true)`).

---

### Contrato 2: Suscribirse a Cambios de Disponibilidad del Plato en Panel Abierto

- **Identificador Funcional**: `OP-011-02: SincronizarDisponibilidadPlato`
- **Actor**: Sistema / Cliente Frontend del Comensal.
- **Entrada**:
  - `tenantId`: UUID del restaurante activo.
  - `If-None-Match`: Cabecera HTTP opcional con el valor ETag previamente recibido.
- **Salida**:
  - Si el inventario o publicación no variaron: HTTP 304 `Not Modified` con cabecera `ETag` idéntica.
  - Si hubo variación: HTTP 200 `OK` con cuerpo `AvailabilityMapResponse` (`availability: Map<UUID, Boolean>`) y cabecera `ETag` SHA-256 actualizado.
- **Invariantes Garantizadas**:
  - Latencia máxima de detección y reflejo en pantalla $\le 3$ segundos (SLA cumplido mediante polling cada 2.500 ms) (SC-010).
  - Si el plato evaluado pasa a no disponible (stock=0, cancelación administrativa o despublicación), el modal no se cierra forzosamente; actualiza su interfaz a "Plato no disponible" / badge "Agotado" y desactiva controles de compra (Clarification #5, INV-5, FR-007, EC-004).
  - Si se reabastece el insumo en cocina, el modal reactiva de inmediato los botones de compra (EC-005).
- **Errores**:
  - Falla transitoria de red → El cliente retiene el estado previo y reintenta en el siguiente ciclo sin romper la UI (isConnectionLost warning tras 3 fallos consecutivos).
- **Precondiciones**:
  - Panel superpuesto abierto o catálogo visible en pestaña activa (Page Visibility API activa).
- **Postcondiciones**:
  - El estado reactivo de disponibilidad del plato en el modal y en la grilla queda sincronizado con el stock de cocina.
- **Cubre**: AC-1.6, INV-5, FR-007, SC-010, EC-004, EC-005, Clarification #5.
- **Backward Compatibility**:
  - Reutiliza 100% el endpoint `GET /api/v1/establishments/{tenantId}/catalog/availability` de spec 008.
- **Concurrencia / Atomicidad**:
  - Idempotente y libre de locks; utiliza hashing determinista SHA-256 de las existencias.

---

### Contrato 3: Suscribirse a Cambios de Precio Administrativo del Plato

- **Identificador Funcional**: `OP-011-03: SincronizarPrecioPlato`
- **Actor**: Sistema / Cliente Frontend del Comensal.
- **Entrada**:
  - `tenantId`: UUID del restaurante activo.
  - Evento de recarga de catálogo o reapertura de modal superpuesto.
- **Salida**:
  - `DinerCatalogResponse`: Carta del menú con precios unitarios vigentes (`retailPrice`).
- **Invariantes Garantizadas**:
  - Toda modificación de precio realizada por la administración en el back-office (spec 007) se refleja en el catálogo y en el panel en la siguiente recarga o reapertura sin requerir un nuevo inicio de sesión del comensal (INV-11, FR-013, SC-008, EC-009).
  - No altera la sesión activa ni invalida el token JWT del comensal.
- **Errores**:
  - Conexión caída → Retiene el último precio cacheado en memoria hasta reanudar conectividad.
- **Precondiciones**:
  - Comensal autenticado con sesión válida.
- **Postcondiciones**:
  - El precio final y los cálculos de subtotales provisorios aplican el nuevo valor comercial en pantalla.
- **Cubre**: AC-3.3, INV-11, FR-013, SC-008, EC-009.
- **Backward Compatibility**:
  - Consistente con el contrato de catálogo de spec 008 y la entidad `Dish.retail_price` de spec 007.

---

### Contrato 4: Agregar al Carrito con Validación de Inventario contra Stock Remanente

- **Identificador Funcional**: `OP-011-04: AgregarAlCarritoValidandoStock`
- **Actor**: Comensal autenticado.
- **Entrada**:
  - `dishId`: UUID del plato.
  - `requestedQuantity`: Número entero $N \ge 1$ seleccionado en el modal.
  - `currentCartQuantity`: Cantidad acumulada de unidades de ese mismo plato que ya residen en el carrito de compras.
  - `tenantId`: UUID del restaurante activo.
- **Salida**:
  - Estado de éxito: Adición consolidada en `sessionStorage` (`items` actualizados con cantidad acumulada $N + q_{\text{en\_carrito}}$), subtotal provisorio liquidado y notificación emergente visible de confirmación.
  - Estado de rechazo: Bloqueo de la acción de incorporación, notificación explícita de disponibilidad insuficiente ("La cantidad solicitada supera el stock disponible en cocina. Disponibles: X") y preservación intacta del carrito previo.
- **Invariantes Garantizadas**:
  - El selector del modal permite estimar libremente sin tope artificial previo (Clarification #2, INV-13).
  - La validación estricta de stock se ejecuta al accionar "Agregar al Carrito", contrastando $N + q_{\text{en\_carrito}} \le \text{stockDisponible}$ (FR-015, EC-011).
  - Si el comensal cierra el modal sin agregar, el subtotal calculado se descarta y el selector se reinicia en 1 unidad para la siguiente apertura (EC-011).
- **Errores**:
  - $N < 1$ o no entero → Error de validación local; impide presionar el botón de agregar.
  - $N + q_{\text{en\_carrito}} > \text{stockDisponible}$ → Mensaje de advertencia de stock insuficiente.
- **Precondiciones**:
  - Plato con `isAvailable = true` y establecimiento en horario operativo (`isOpen = true`).
- **Postcondiciones**:
  - Si es exitoso: el carrito incrementa su `totalCount` en $N$ y su `totalAmount` en $N \times \text{retailPrice}$.
- **Cubre**: AC-3.5, INV-13, FR-015, SC-011, EC-011, Clarification #2.
- **Backward Compatibility**:
  - Extiende la interfaz `addItem(dish, quantity)` de `CartContext` compatible con spec 009.
- **Concurrencia / Atomicidad**:
  - Atómico en la sesión del cliente; la validación final definitiva contra cocina se reafirma durante el envío de la orden.

---

### Contrato 5: Consultar Grilla Filtrada (Categorías OR + Rango AND + Búsqueda AND)

- **Identificador Funcional**: `OP-011-05: ConsultarGrillaFiltrada`
- **Actor**: Comensal navegando en el catálogo.
- **Entrada**:
  - `selectedCategories`: Arreglo de cadenas con las categorías marcadas en la barra lateral.
  - `minPrice`: Límite numérico inferior opcional ($P_{\min}$).
  - `maxPrice`: Límite numérico superior opcional ($P_{\max}$).
  - `searchQuery`: Texto ingresado en la barra de búsqueda rápida (COM-4.2.3).
  - `dishes`: Lista completa de platos del catálogo cargado para el restaurante activo.
- **Salida**:
  - `filteredDishes`: Arreglo de platos que satisfacen simultáneamente:
    $$\left(C_{\text{sel}} = \emptyset \lor p.\text{category} \in C_{\text{sel}}\right) \land \left(P_{\min} \le p.\text{retailPrice} \le P_{\max}\right) \land \text{matchesDishQuery}(p, T_{\text{busq}})$$
- **Invariantes Garantizadas**:
  - Semántica disyuntiva (OR) entre múltiples categorías seleccionadas (AC-4.1, INV-14, FR-016, EC-012).
  - Semántica cerrada e inclusiva en ambos límites del rango de precios (AC-4.2, INV-14, FR-017, EC-016).
  - Intersección conjuntiva (AND) estricta entre todas las dimensiones activas (AC-4.3, INV-14, FR-018, EC-013, Clarification #1).
  - Platos sin stock pero que cumplan las condiciones del filtro permanecen visibles en estado atenuado y deshabilitado (AC-4.6, INV-17, FR-021, SC-014, EC-019).
  - Si no existen coincidencias ($\text{resultados} = \emptyset$), se exhibe aviso explicativo y botón para limpiar filtros (AC-4.4, INV-15, FR-019, SC-013, EC-017).
- **Errores**:
  - $P_{\min} > P_{\max}$ → Muestra mensaje de validación "El precio mínimo no puede superar al precio máximo" y bloquea la aplicación del filtro de rango (FR-022, EC-014).
- **Precondiciones**:
  - Catálogo del establecimiento cargado en el cliente.
- **Postcondiciones**:
  - Grilla de platos re-renderizada inmediatamente con las tarjetas filtradas.
- **Cubre**: AC-4.1, AC-4.2, AC-4.3, AC-4.4, AC-4.6, INV-14, INV-15, INV-17, FR-016, FR-017, FR-018, FR-019, FR-021, FR-022, SC-012, SC-013, SC-014, EC-012, EC-013, EC-014, EC-015, EC-016, EC-017, EC-019.

---

### Contrato 6: Limpiar Filtros de Forma Unificada

- **Identificador Funcional**: `OP-011-06: LimpiarFiltrosUnificado`
- **Actor**: Comensal.
- **Entrada**:
  - Acción de presionar el botón "Limpiar filtros" (en la barra lateral de filtros o en el estado de grilla vacía).
- **Salida**:
  - `selectedCategories = []`
  - `minPrice = ''`
  - `maxPrice = ''`
  - `searchQuery = ''`
  - Catálogo restaurado al 100% de su estado original con todas las categorías y platos disponibles visibles.
  - Clave de sesión `easyeat_catalog_filters_{tenantId}` purgada en `sessionStorage`.
- **Invariantes Garantizadas**:
  - La limpieza restablece de forma unificada y simultánea las categorías marcadas, el rango de precios y la barra de búsqueda de texto del catálogo (Clarification #1, INV-16, FR-020, EC-018).
- **Precondiciones**:
  - Al menos una dimensión de filtrado activa (categorías, precio o término de búsqueda).
- **Postcondiciones**:
  - El comensal observa la totalidad del menú sin restricciones aplicadas.
- **Cubre**: AC-4.5, INV-16, FR-020, SC-013, EC-018, Clarification #1.

---

### Contrato 7: Persistir y Restaurar Filtros por Sesión de Establecimiento

- **Identificador Funcional**: `OP-011-07: PersistirFiltrosPorEstablecimiento`
- **Actor**: Sistema Frontend.
- **Entrada**:
  - Transición de navegación del comensal hacia `/perfil`, `/carrito` o recarga de pestaña.
  - Evento de retorno a `/catalogo`.
  - Evento de conmutación de establecimiento en `TenantContext`.
- **Salida**:
  - Si navega dentro del mismo local: los filtros aplicados (categorías, rango y texto) se restauran de forma transparente desde `sessionStorage`.
  - Si cambia de establecimiento (spec 008): los filtros se restablecen a su estado inicial limpio para el nuevo local.
- **Invariantes Garantizadas**:
  - Los filtros manuales sobreviven la navegación interna en el mismo establecimiento (Clarification #3, INV-16, FR-024).
  - Los filtros nunca se mezclan ni contaminan la vista de otro restaurante diferente (aislamiento multi-tenant en cliente).
- **Precondiciones**:
  - `TenantContext` activo con `tenant.id` válido.
- **Postcondiciones**:
  - Estado de filtros sincronizado en `sessionStorage` bajo la clave correspondiente.
- **Cubre**: INV-16, FR-024, Clarification #3.

