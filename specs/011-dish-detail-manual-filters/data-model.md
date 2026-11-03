# Modelo de Datos Conceptual: Detalle de Plato, Información Nutricional, Precios y Filtros Manuales

**Feature Branch**: `011-dish-detail-manual-filters`  
**Fecha**: 01/11/2026  
**Estado**: Diseñado  
**Referencia de Especificación**: [spec.md](./spec.md)

---

## 1. Visión General del Modelo

El modelo de datos conceptual para la feature 011 define las entidades de dominio, sus relaciones, atributos funcionales e invariantes operativas involucradas en la presentación detallada del plato, la consulta nutricional y de alérgenos, el formateo monetario regional, el cálculo reactivo de subtotales provisorios y el filtrado multidimensional del catálogo.

En estricta conformidad con el Principio V de la Constitución (alcance acotado) y las directivas anti-alucinación, este modelo **no prescribe nuevas tablas relacionales en la base de datos**. Reutiliza y proyecta las entidades ya persistidas en PostgreSQL (`dishes`, `dish_photos`, `dish_recipe_ingredients`, `ingredients`, `dish_dietary_restrictions`, `establishments`), complementándolas con entidades conceptuales y de sesión del cliente para el estado de filtros manuales.

---

## 2. Entidades Conceptuales de Dominio

### 2.1 Plato

Representa el producto gastronómico publicado y disponible para exploración, consulta detallada e incorporación al pedido por parte del comensal.

- **Atributos de Dominio**:
  - `id`: Identificador único universal (UUID) del plato.
  - `tenantId`: Identificador del establecimiento gastronómico al que pertenece (aislamiento multi-tenant).
  - `name`: Nombre comercial del plato (cadena de hasta 100 caracteres).
  - `description`: Descripción gastronómica detallada del plato (texto libre).
  - `category`: Categoría temática asignada (`DishCategory`).
  - `retailPrice`: Precio final de venta al público fijado por la administración con impuestos incluidos (valor decimal exacto en escala 2).
  - `status`: Estado del ciclo de vida del plato (`PUBLISHED`, `DRAFT`, `PENDING_PRICE`, `CANCELLED`).
  - `isActive`: Indicador booleano de activación comercial.
  - `photoUrl`: Enlace de entrega del archivo binario multimedia (`/api/v1/dishes/{id}/photo`) o `null` si no posee fotografía.
  - `recipeIngredients`: Lista ordenada de nombres de ingredientes que componen la receta activa en cocina.
  - `allergens`: Conjunto de etiquetas neutrales de advertencias de alérgenos asociados al plato.
  - `isAvailable`: Estado derivado en tiempo real de disponibilidad para la venta.

- **Invariantes Locales**:
  - Solo los platos con `status = 'PUBLISHED'` y `isActive = true` forman parte del catálogo público del comensal.
  - Si un plato no posee fotografía registrada (`photoUrl == null`), no se reserva espacio visual en blanco ni se emiten imágenes rotas (INV-2, FR-003).
  - El precio exhibido es estrictamente el valor final unitario con impuestos incluidos, idéntico al computado en el total del pedido (INV-9).

- **Relaciones**:
  - Pertenece a exactamente un `Establecimiento` (`tenantId`).
  - Clasificado en exactamente una `Categoría Gastronómica`.
  - Vinculado a cero o más `Ingredientes` mediante su receta activa.
  - Vinculado a cero o más `Alérgenos`.

---

### 2.2 Categoría Gastronómica

Clasificación temática estándar que agrupa platos dentro del menú del restaurante para estructurar la carta y gobernar el filtrado manual disyuntivo.

- **Atributos de Dominio**:
  - `code`: Clave canónica cerrada (`ENTRADAS`, `PLATOS_PRINCIPALES`, `POSTRES`, `BEBIDAS`, `ENSALADAS`).
  - `name`: Etiqueta descriptiva para visualización ("Entradas", "Platos principales", "Postres", "Bebidas", "Ensaladas").
  - `displayOrder`: Orden canónico de presentación en la carta.

- **Invariantes Locales**:
  - El menú opera sobre la taxonomía cerrada de 5 categorías canónicas aprobadas en el proyecto.
  - La selección de múltiples categorías en el filtro manual opera bajo semántica disyuntiva de unión lógica (OR): un plato coincide si pertenece a *cualquiera* de las categorías seleccionadas (INV-14, FR-016).

---

### 2.3 Ingrediente

Componente o insumo culinario que integra la receta del plato elaborada en cocina.

- **Atributos de Dominio**:
  - `id`: Identificador único del insumo culinario.
  - `tenantId`: Identificador del restaurante propietario.
  - `name`: Denominación del ingrediente (ej. "Harina de trigo", "Queso mozzarella", "Aceite de oliva").
  - `currentStock`: Existencia física disponible en cocina (valor decimal exacto).
  - `status`: Estado del insumo (`ACTIVE`, `INACTIVE`).

- **Invariantes Locales**:
  - Las modificaciones o sustituciones de ingredientes en cocina se reflejan inmediatamente en la siguiente apertura del detalle del plato o refresco de pantalla (INV-7, FR-009).
  - Si el plato no tiene receta registrada, la sección informativa expone la leyenda explícita `"Lista de componentes no disponible"`, evitando listas vacías o mensajes de fallo (INV-8, FR-010).

---

### 2.4 Alérgeno

Advertencia estándar de salud alimentaria vinculada a los componentes de la receta del plato.

- **Atributos de Dominio**:
  - `code`: Código estandarizado de taxonomía médica/alimentaria (ej. `GLUTEN`, `LACTOSE`, `EGG`, `NUTS`, `FISH`, `SOY`).
  - `name`: Etiqueta legible uniforme (ej. "Gluten", "Lactosa", "Huevo", "Frutos secos", "Pescado", "Soja").
  - `category`: Clasificación fija `COMMON_ALLERGEN`.

- **Invariantes Locales**:
  - En la vista informativa de detalle de plato, las etiquetas de alérgenos se visualizan de forma neutra, estándar y uniforme para todos los comensales (Clarification #4, INV-6, FR-008).
  - Está estrictamente prohibido aplicar resaltes condicionales, avisos de peligro o alertas preventivas basadas en el perfil dietario del comensal autenticado en este panel (Clarification #4).
  - Si un plato contiene ingredientes pero carece de alérgenos registrados, se exhibe la leyenda informativa indicando que no contiene alérgenos declarados (EC-008).

---

### 2.5 Configuración Regional de Moneda

Parámetros de formato numérico y monetario vigentes para el establecimiento que gobiernan la presentación visual de precios.

- **Atributos de Dominio**:
  - `currencySymbol`: Símbolo monetario (valor por defecto: `"$"`).
  - `thousandSeparator`: Carácter separador de unidades de mil (valor por defecto: `"."`).
  - `decimalSeparator`: Carácter separador de fracciones decimales (valor por defecto: `","`).
  - `decimalPrecision`: Cantidad de cifras decimales significativas (valor por defecto: `2`).
  - `locale`: Identificador regional IETF (valor por defecto: `"es-AR"`).

- **Invariantes Locales**:
  - Todo precio exhibido en el catálogo, panel superpuesto y subtotal provisorio debe formatearse respetando esta configuración (ej. `"$ 12.500,00"`) (INV-10, FR-012, SC-007).
  - El precio formateado presentado en el panel es estrictamente el valor unitario final con impuestos incluidos utilizado en la liquidación del carrito (INV-9).

---

### 2.6 Filtro Manual (Estado de Sesión)

Conjunto de parámetros de selección aplicados voluntariamente por el comensal para acotar la exploración del menú.

- **Atributos de Dominio**:
  - `tenantId`: Identificador del establecimiento gastronómico activo.
  - `selectedCategories`: Lista de nombres o códigos de categorías gastronómicas marcadas ($C_{\text{sel}}$). Si está vacía, no restringe categorías.
  - `minPrice`: Límite inferior del rango de precios ($P_{\min}$), valor monetario no negativo o nulo.
  - `maxPrice`: Límite superior del rango de precios ($P_{\max}$), valor monetario no negativo o nulo.
  - `searchQuery`: Término ingresado en la barra de búsqueda rápida de texto del catálogo (integración con COM-4.2.3).

- **Invariantes Locales**:
  - Validación de rango (FR-022, EC-014): Si $P_{\min} > P_{\max}$, el estado es inválido; el sistema bloquea la aplicación del filtro y presenta advertencia de validación ("El precio mínimo no puede superar al precio máximo").
  - Intervalo cerrado e inclusivo (EC-016): Los platos cuyo precio coincide exactamente con $P_{\min}$ o con $P_{\max}$ se consideran dentro del rango y se exhiben en los resultados.
  - Igualdad estricta (EC-015): Si $P_{\min} = P_{\max}$, se filtran platos con precio exactamente igual a dicho importe.
  - Persistencia en sesión (Clarification #3, INV-16, FR-024): Los filtros manuales se conservan en `sessionStorage` indexados por `tenantId` mientras el comensal navegue dentro del mismo restaurante (incluso yendo a su perfil o al carrito).
  - Limpieza unificada (Clarification #1, INV-16, FR-020, EC-018): La acción "Limpiar filtros" desmarca todas las categorías, vacía el rango de precios y vacía simultáneamente la barra de búsqueda de texto.

---

### 2.7 Resultado Filtrado

Colección dinámica de platos que satisfacen simultáneamente todas las dimensiones de filtrado activas.

- **Predicado de Inclusión**:
  $$\text{cumple\_filtros}(p) \iff \left(C_{\text{sel}} = \emptyset \lor p.\text{category} \in C_{\text{sel}}\right) \land \left(P_{\min} \le p.\text{retailPrice} \le P_{\max}\right) \land \text{matchesDishQuery}(p, T_{\text{busq}})$$
- **Invariantes Locales**:
  - Visibilidad no excluyente de agotados (INV-17, FR-021, SC-014, EC-019): Los platos que carecen de stock pero satisfacen las condiciones del filtro permanecen visibles en la lista filtrada bajo estado visual atenuado y controles de compra deshabilitados.
  - Manejo de conjunto vacío (INV-15, FR-019, SC-013, EC-017): Si el resultado es $\emptyset$, se despliega un mensaje visible de ausencia de resultados junto a un botón interactivo para limpiar los filtros.
  - Reactividad de stock (INV-17, FR-023, EC-020): Las variaciones de inventario en cocina actualizan la disponibilidad en la grilla filtrada en tiempo real ($\le 3$ s) sin remover los filtros activos ni recargar la página.

---

## 3. Estado Derivado: Disponibilidad en Tiempo Real

El estado de disponibilidad de un plato no es una columna estática en la base de datos, sino un cálculo derivado dinámico en memoria y reactivo en cliente:

$$\text{isAvailable}(p) \iff (\text{dish.status} = \text{'PUBLISHED'}) \land (\text{dish.isActive} = \text{true}) \land \left(\forall l \in \text{recipeLines}(p): \text{currentStock}(l.\text{ingredient}) \ge l.\text{quantity}\right)$$

### Tabla de Transición de Disponibilidad en el Panel Superpuesto

| Estado Inicial | Evento en Tiempo Real | Estado Resultante en Panel | Comportamiento de Interfaz |
|---|---|---|---|
| Plato Disponible | Stock de insumo cae a 0 | **Agotado / No Disponible** | Insignia "Agotado", botones `+`/`-` y "Agregar al Carrito" deshabilitados en $\le 3$ s sin cerrar el modal (C-5, INV-5, FR-007). |
| Plato Disponible | Administrador cancela plato (`status = CANCELLED`) | **Plato No Disponible** | Panel conmuta a "Plato no disponible", compra deshabilitada en $\le 3$ s sin cerrar panel. Al cerrar, se remueve de catálogo (C-5). |
| Plato Disponible | Administrador despublica plato (`isActive = false`) | **Plato No Disponible** | Panel conmuta a "Plato no disponible", compra deshabilitada en $\le 3$ s sin cerrar panel. Al cerrar, se remueve de catálogo (C-5). |
| Plato Agotado | Cocina repone insumos | **Plato Disponible** | Remueve insignia de agotado y reactiva automáticamente los controles de compra sin recarga manual (EC-005). |

---

## 4. Notas de Persistencia y Gobernanza

1. **Esquema Relacional**: Las tablas `dishes`, `dish_photos`, `dish_recipe_ingredients`, `ingredients`, `dish_dietary_restrictions` y `establishments` provienen de las migraciones Flyway `V1` a `V10`. Esta feature no agrega tablas ni altera constraints en base de datos.
2. **Sesión Local del Cliente**:
   - `sessionStorage.getItem('easyeat_catalog_filters_' + tenantId)` gestiona el ciclo de vida de los filtros manuales por local gastronómico.
3. **Aislamiento Multi-Tenant**:
   - Toda consulta de catálogo, detalle o stock valida la concordancia estricta con el `tenantId` del restaurante activo.

