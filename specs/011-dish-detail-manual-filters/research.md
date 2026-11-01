# Investigación Técnica y Decisiones Arquitectónicas: Detalle de Plato, Información Nutricional, Precios y Filtros Manuales de Catálogo

**Feature Branch**: `011-dish-detail-manual-filters`  
**Fecha**: 01/11/2026  
**Estado**: Completado  
**Referencia de Especificación**: [spec.md](./spec.md)

---

## 1. Resumen de Investigación

Esta fase de investigación resuelve las decisiones de diseño arquitectónico y técnico para la extensión de la experiencia del comensal sobre el catálogo gastronómico existente en EasyEat. Cubre las cuatro historias de usuario aprobadas:
1. **COM-5.1.1 (Visualizar multimedia y nombre)**: Panel superpuesto con preservación estricta de scroll, degradación elegante ante conexiones lentas, adaptación de textos largos y deshabilitación en tiempo real ante stock cero o baja administrativa.
2. **COM-5.1.3 (Ver precio final y subtotal provisorio)**: Presentación destacada del precio final con impuestos incluidos, formateo regional homogéneo, actualización tras cambios de precio administrativos, y cálculo reactivo de subtotal provisorio con selector numérico sin tope artificial validando inventario al agregar al carrito.
3. **COM-5.1.2 (Ver ingredientes y alérgenos)**: Desglose completo de ingredientes y sellos identificadores de alérgenos neutros y uniformes para todos los comensales, actualización ante cambios de receta en cocina y mensaje explícito cuando no hay datos disponibles.
4. **COM-4.2.2 (Búsqueda por filtros manuales)**: Barra lateral con selección múltiple de categorías (unión OR) y rango de precios (intervalo cerrado inclusivo), composición conjuntiva (intersección AND) con la búsqueda por texto existente (COM-4.2.3), persistencia en sesión local aislada por establecimiento y limpieza unificada simultánea.

Todas las decisiones se fundamentan estrictamente en la evidencia observable del repositorio, las especificaciones previas integradas (`specs/001` a `specs/010`) y los principios de la Constitución SDD v2.0.0. No existen incógnitas pendientes (`[NEEDS CLARIFICATION]`: 0).

---

## 2. Decisiones Técnicas Fundamentales

### D-001 — Invalidación Reactiva en Panel Abierto y Reutilización del Canal de Tiempo Real

- **Pregunta Técnica**: ¿Cómo reflejar en el panel de detalle abierto el agotamiento de stock, cancelación o despublicación del plato en un tiempo $\le 3$ segundos sin forzar el cierre intempestivo del modal (C-5, INV-5, FR-007, SC-010, EC-004, EC-005)?
- **Decisión**: Reutilizar de forma directa el canal de sincronización en tiempo real provisto por la especificación 008 (`useRealtimeAvailability` en frontend y `DishAvailabilityEngine` en backend vía `GET /api/v1/establishments/{tenantId}/catalog/availability` con ETags y polling condicional cada 2.500 ms).
  1. El hook `useRealtimeAvailability` ya se ejecuta en `CatalogPage.tsx` con un intervalo de 2.500 ms ($\le 3$ s SLA) y pasa el mapa de disponibilidad (`availability: Record<string, boolean>`) a los componentes hijos.
  2. Al abrir el panel `DishDetailModal.tsx`, este evalúa la disponibilidad efectiva:
     $$\text{isAvailable} = (\text{availability}[\text{dish.id}] === \text{true}) \land (\text{dish.status} === \text{'PUBLISHED'}) \land (\text{dish.isActive} === \text{true})$$
  3. Si el stock del insumo principal cae a 0 en cocina, `availability[dish.id]` transiciona a `false`. Si el plato es cancelado (`status = 'CANCELLED'`) o despublicado (`isActive = false`), el plato deja de incluirse en la respuesta de disponibilidad del backend o se marca como no disponible.
  4. En el panel abierto: ante $\neg\text{isAvailable}$, el modal no se cierra; conmuta su estado visual a "Plato no disponible" / badge "Agotado" y deshabilita inmediatamente los botones de cantidad y el botón "Agregar al carrito" en $\le 3$ segundos.
  5. Simetría ante reabastecimiento (EC-005): si el insumo se repone mientras el modal permanece abierto, `availability[dish.id]` vuelve a `true`, reactivando automáticamente los controles de compra.
  6. Al cerrar el modal por el comensal: la grilla del catálogo subyacente refleja el estado actualizado del plato (atenuado y deshabilitado o removido si fue cancelado/despublicado).
- **Justificación**: Cumple estrictamente la Clarification #5 y la restricción de reuso de infraestructura del spec 008, garantizando latencia $\le 3$ s sin introducir WebSockets, SSE ni librerías de mensajería no soportadas en el repositorio.
- **Alternativas descartadas**:
  - *WebSockets / Server-Sent Events (SSE)*: Descartada porque el repositorio EasyEat no implementa infraestructura de sockets ni SSE; violaría las restricciones del prompt y el principio IV (KISS).
  - *Cierre forzado inmediato del modal*: Descartada porque viola expresamente la Clarification #5 y la INV-5.

---

### D-002 — Composición Conjuntiva (AND) de Filtros Manuales y Búsqueda por Texto con Limpieza Unificada

- **Pregunta Técnica**: ¿Cómo componer la selección de categorías y rango de precio con la barra de búsqueda por texto existente (COM-4.2.3), y cómo orquestar la limpieza unificada (C-1, INV-14, INV-15, INV-16, FR-016 a FR-020, EC-012 a EC-018)?
- **Decisión**: Implementar el predicado algebraico exacto en el cliente dentro del pipeline de filtrado del catálogo:
  $$\text{matches}(p) \iff \text{matchesCategory}(p) \land \text{matchesPriceRange}(p) \land \text{matchesDishQuery}(p, T_{\text{busq}})$$
  donde:
  1. $\text{matchesCategory}(p) \equiv (C_{\text{sel}} = \emptyset) \lor (p.\text{category} \in C_{\text{sel}})$ (semántica disyuntiva OR entre categorías seleccionadas; si no hay ninguna marcada, coincide toda categoría).
  2. $\text{matchesPriceRange}(p) \equiv (P_{\min} = \text{null} \lor p.\text{price} \ge P_{\min}) \land (P_{\max} = \text{null} \lor p.\text{price} \le P_{\max})$ con semántica cerrada e inclusiva en ambos límites (EC-016).
     - Validación preventiva: si $P_{\min} > P_{\max}$, se activa un mensaje explícito de error en el formulario de la barra lateral ("El precio mínimo no puede superar al precio máximo") y se bloquea la aplicación del filtro de rango (EC-014, FR-022).
     - Igualdad estricta: si $P_{\min} = P_{\max}$, se filtran platos con precio exacto igual a dicho valor (EC-015).
  3. $\text{matchesDishQuery}(p, T_{\text{busq}})$ reutiliza la función existente `matchesDishQuery` de `CatalogSearchFilter.tsx` (diacritic-insensitive, coincidencia en nombre o ingredientes).
  4. Estado sin resultados (INV-15, FR-019, EC-017, SC-013): si ningún plato satisface el predicado, se despliega el contenedor informativo con el botón destacado "Limpiar filtros".
  5. Limpieza unificada (C-1, INV-16, FR-020, EC-018): la acción "Limpiar filtros" ejecuta atómicamente:
     - `selectedCategories = []` (desmarca todas las categorías).
     - `minPrice = ''`, `maxPrice = ''` (vacía el rango monetario).
     - `searchQuery = ''` (vacía la barra de búsqueda por texto).
     - Limpia la clave de sesión local correspondiente al establecimiento.
- **Justificación**: Mantiene la consistencia matemática requerida por INV-14, respeta la Clarification #1 y reutiliza el motor de búsqueda existente sin duplicar código de normalización de cadenas.
- **Alternativas descartadas**:
  - *Limpiar exclusivamente categorías y precio dejando el texto activo*: Descartada porque contradice formalmente la Clarification #1 y la INV-16.
  - *Filtrado exclusivo en servidor con endpoint con query params*: Descartada para la navegación del comensal porque el catálogo ya reside en memoria del cliente (`CatalogResponse` cargado por tenant), permitiendo filtrado instantáneo sin latencia de red.

---

### D-003 — Persistencia de Filtros Manuales en Sesión Local Aislada por Establecimiento

- **Pregunta Técnica**: ¿Cómo persistir los filtros manuales aplicados para que sobrevivan a la navegación hacia el perfil o carrito y se restauren al volver al catálogo, pero se limpien al cambiar de restaurante (C-3, INV-16, FR-024)?
- **Decisión**: Extender `CatalogFilterContext.tsx` y utilizar `sessionStorage` con clave indexada por tenant:
  $$\text{StorageKey} = \text{'easyeat\_catalog\_filters\_'} + \text{tenantId}$$
  1. Estructura persistida:
     ```typescript
     interface ManualFilterState {
       categories: string[];
       minPrice: string;
       maxPrice: string;
       searchQuery: string;
     }
     ```
  2. Al inicializar `CatalogView` para un `tenant.id` determinado:
     - Si existe en `sessionStorage` la entrada para ese `tenantId`, se restaura el estado de los filtros (categorías, rango y búsqueda).
     - Si el comensal navega a `/perfil`, `/carrito` o a otra ruta y regresa a `/catalogo` del mismo establecimiento, los filtros se encuentran intactos.
  3. Restablecimiento por cambio de establecimiento: al conmutar el restaurante en `TenantContext` (o mediante `EstablishmentSwitchModal` del spec 008), la clave del establecimiento previo permanece aislada y se inicializan los filtros del nuevo local en su estado limpio por defecto.
  4. Restablecimiento voluntario: al presionar "Limpiar filtros", se borra la entrada en `sessionStorage` del establecimiento activo.
- **Justificación**: Reutiliza el patrón de persistencia en `sessionStorage` implementado en `TenantContext` (`TENANT_STORAGE_KEY`) y `CartContext` (`CART_STORAGE_KEY`), asegurando aislamiento estricto por local y por pestaña sin ensuciar la base de datos con estados efímeros de UI.
- **Alternativas descartadas**:
  - *Almacenamiento en `localStorage`*: Descartada porque comparte estado entre pestañas simultáneas de diferentes restaurantes.
  - *Almacenamiento global no particionado por tenant*: Provocaría fuga de categorías entre locales con diferentes cartas gastronómicas.

---

### D-004 — Preservación de Posición de Scroll, Cierre por Fondo y Degradación de Imagen

- **Pregunta Técnica**: ¿Cómo garantizar la preservación milimétrica de la posición de scroll en el catálogo (INV-1, FR-004, SC-002, EC-021), la contención de textos largos (INV-4, FR-006, EC-003), la ausencia de recuadro roto sin foto (INV-2, FR-003, EC-001) y la prioridad de lectura en conexiones lentas (INV-3, FR-005, EC-002)?
- **Decisión**:
  1. **Preservación de scroll (INV-1, FR-004, SC-002)**:
     - Antes de renderizar el modal, se captura la posición `const scrollY = window.scrollY`.
     - Para el bloqueo de scroll del fondo, se evita modificar `body.style.position = 'fixed'` con reset a 0. En su lugar, se aplica una clase de contención o al cerrar el modal (por botón `modal-close-btn` o clic en el backdrop exterior `data-testid="dish-detail-modal"`), se restituye programáticamente:
       `window.scrollTo({ top: scrollY, behavior: 'instant' })`.
     - Cierre por fondo exterior (EC-021): el contenedor externo con `onClick={onClose}` y el contenedor interno con `onClick={(e) => e.stopPropagation()}` cierran el modal sin alterar la coordenada.
  2. **Ausencia de fotografía (INV-2, FR-003, EC-001)**:
     - Si `dish.photoUrl == null` o vacío: el contenedor superior de imagen no se renderiza. La cabecera del modal inicia directamente con el título, categoría y descripción, sin reservar rectángulos grises vacíos ni placeholders ilustrativos.
  3. **Degradación elegante en red lenta (INV-3, FR-005, SC-004, EC-002)**:
     - Si existe `dish.photoUrl`: se renderiza inmediatamente el bloque textual (nombre, descripción, precio, ingredientes).
     - La sección de foto muestra un indicador visual de carga animado (skeleton/spinner) mientras la imagen se descarga. Al disparar el evento nativo `onLoad`, el skeleton es sustituido suavemente por la imagen en alta resolución. Si falla la carga (`onError`), se oculta la sección visual de foto adaptando el layout.
  4. **Adaptación de textos largos (INV-4, FR-006, SC-005, EC-003)**:
     - El contenedor de contenido del modal utiliza `max-h-[85vh]` y `overflow-y-auto`, permitiendo desplazamiento vertical interno continuo para descripciones extensas sin desbordar los bordes de la ventana.
- **Justificación**: Cumple 1:1 con las invariantes de experiencia de usuario y asegura que un comensal en conexiones móviles degradadas pueda leer la información crítica sin esperar la descarga de bytes pesados.
- **Alternativas descartadas**:
  - *Renderizar una imagen genérica por defecto cuando no hay foto*: Descartada porque viola expresamente la INV-2 y el requerimiento FR-003.

---

### D-005 — Formateo Monetario Regional y Consistencia de Precio con el Carrito

- **Pregunta Técnica**: ¿Cómo garantizar el formateo monetario regional unificado y la coincidencia matemática absoluta entre el precio unitario del catálogo, el modal de detalle y el total del carrito (INV-9, INV-10, INV-12, FR-011, FR-012, FR-014, SC-007, EC-009, EC-010)?
- **Decisión**:
  1. **Consistencia de origen (INV-9)**:
     - El precio proviene exclusivamente de `dish.retailPrice` (PVP vigente fijado por la administración en spec 007 con impuestos incluidos).
  2. **Módulo centralizado de formateo regional (`currencyUtils.ts`)**:
     - Crear/extender la utilidad de presentación:
       ```typescript
       export function formatRegionalPrice(amount: number | BigDecimal): string {
         const numericValue = typeof amount === 'number' ? amount : Number(amount);
         return `$ ${numericValue.toLocaleString('es-AR', {
           minimumFractionDigits: 2,
           maximumFractionDigits: 2,
         })}`;
       }
       ```
     - Aplica el símbolo `$ `, separador de miles `.` y precisión decimal `,00` vigente para la región de los establecimientos (`America/Argentina/Buenos_Aires`).
  3. **Platos sin stock (INV-12, FR-014, EC-010, SC-009)**:
     - El precio final continúa visible con su formateo regional, mientras que los botones de suma de unidades y de agregar al pedido se presentan deshabilitados con estilo atenuado.
  4. **Actualización de precio administrativo (INV-11, FR-013, SC-008, EC-009)**:
     - Al recargar el catálogo o reabrir el modal, se consulta el catálogo actualizado reflejando el nuevo precio sin exigir re-login del comensal.
- **Justificación**: Elimina la dispersión de llamadas ad-hoc a `toLocaleString` en los componentes y asegura que el comensal visualice exactamente el mismo valor monetario en la tarjeta, en el modal y en el resumen de compra.
- **Alternativas descartadas**:
  - *Hardcodear concatenación manual de cadenas sin `Intl`*: Descartada por propensión a errores de internacionalización y separadores.

---

### D-006 — Selector de Cantidad sin Tope Artificial y Validación de Inventario al Agregar al Carrito

- **Pregunta Técnica**: ¿Cómo estructurar el selector de cantidad del modal para permitir estimación libre ($n \ge 1$) y realizar la validación estricta de inventario descontando las unidades ya en carrito al momento de confirmar (C-2, INV-13, FR-015, SC-011, EC-011)?
- **Decisión**:
  1. **Estimación sin tope artificial (C-2, INV-13)**:
     - El selector de cantidad en `DishDetailModal.tsx` se inicializa en $1$ y permite incrementar libremente a cualquier entero positivo $n \ge 1$ mediante controles `[-]` y `[+]` o entrada numérica.
     - El subtotal provisorio se calcula en tiempo real con aritmética de punto fijo basada en centavos:
       $$\text{subtotal\_provisorio} = \text{fromCents}(\text{toCents}(p.\text{retailPrice}) \times n)$$
  2. **Validación de disponibilidad en "Agregar al carrito" (FR-015, EC-011)**:
     - Al accionar el botón "Agregar al Carrito", el sistema consulta las unidades del plato que ya existen en el carrito de compras: $q_{\text{en\_carrito}}$.
     - Verifica la disponibilidad remanente:
       $$n + q_{\text{en\_carrito}} \le \text{stockDisponible}$$
     - Si la disponibilidad es superada: se bloquea la adición, se muestra un mensaje de validación visible ("La cantidad seleccionada supera la disponibilidad en cocina. Disponibles: X") y no se altera el carrito.
     - Si la disponibilidad es suficiente: se agregan las $n$ unidades al carrito (`addItem(dish, n)`), se muestra la notificación de éxito y se cierra el modal.
  3. **Descarte de estimación al cerrar sin agregar (EC-011)**:
     - Si el comensal cierra el modal (por botón de cierre o clic exterior) sin haber pulsado "Agregar al carrito", el subtotal provisorio calculado se descarta y el selector vuelve a $1$ en la siguiente apertura.
- **Justificación**: Cumple 100% con la Clarification #2 y el requerimiento FR-015, evitando que un comensal quede artificialmente restringido durante la exploración presupuestaria de su pedido.
- **Alternativas descartadas**:
  - *Limitar el botón `[+]` del selector al stock físico restante*: Descartada porque viola expresamente la Clarification #2 ("Sin tope artificial en el selector del modal").

---

### D-007 — Neutralidad y Transparencia de Ingredientes y Alérgenos en Vista Informativa

- **Pregunta Técnica**: ¿Cómo presentar los ingredientes y las etiquetas de alérgenos de forma neutra y objetiva para todos los comensales, manejando recetas actualizadas y platos sin ingredientes (C-4, INV-6, INV-7, INV-8, FR-008, FR-009, FR-010, SC-006, EC-006, EC-007, EC-008)?
- **Decisión**:
  1. **Neutralidad de alérgenos (C-4, INV-6)**:
     - En el modal `DishDetailModal.tsx`, la sección de alérgenos expone etiquetas estándar y uniformes para todos los comensales, derivadas de los alérgenos configurados en el plato (provenientes de `dish_dietary_restrictions` con taxonomía `COMMON_ALLERGEN`).
     - No se aplican alertas preventivas personalizadas, bordes rojos ni advertencias basadas en el perfil del usuario autenticado en esta vista informativa. (El filtrado condicional por perfil dietario permanece exclusivamente en el catálogo general según spec 003).
  2. **Platos con ingredientes pero sin alérgenos (EC-008)**:
     - Se muestra la lista de ingredientes y se informa explícitamente: "No contiene alérgenos declarados".
  3. **Platos sin información de componentes (INV-8, FR-010, SC-006, EC-007)**:
     - Si la lista de ingredientes está vacía o no registrada, el panel presenta la leyenda explícita: `"Lista de componentes no disponible"`. No se muestran listas vacías ni errores técnicos.
  4. **Modificación de recetas en cocina (INV-7, FR-009, EC-006)**:
     - La información de ingredientes y alérgenos se ensambla dinámicamente desde `dish_recipe_ingredients` e `ingredients`, reflejándose de inmediato en la siguiente apertura del panel.
- **Justificación**: Respeta taxativamente la Clarification #4 y la INV-6, asegurando claridad nutricional objetiva sin inducir a confusión al comensal.
- **Alternativas descartadas**:
  - *Resaltar alérgenos en rojo si coinciden con el perfil del usuario*: Descartada porque viola expresamente la Clarification #4.

---

### D-008 — Comportamiento de Platos Agotados bajo Filtros Activos

- **Pregunta Técnica**: ¿Cómo manejar la visibilidad de platos sin stock cuando se aplican filtros manuales por categoría o precio (INV-17, FR-021, SC-014, EC-019, EC-020)?
- **Decisión**:
  1. Los filtros manuales evalúan pertenencia a la categoría y adecuación al rango de precios, **sin excluir** los platos por su condición de inventario.
  2. Los platos con $\text{stock} = 0$ que cumplan las condiciones del filtro permanecen visibles en la grilla filtrada bajo estado atenuado (`opacity-60 grayscale`), con la insignia "Agotado" y el botón de compra deshabilitado (INV-17, FR-021).
  3. Variaciones de stock reactivas (EC-020, FR-023): cuando el hook de tiempo real actualiza la disponibilidad, la grilla filtrada actualiza el estado de los platos de inmediato sin restablecer los filtros aplicados ni reiniciar la posición de navegación.
- **Justificación**: Permite al comensal conocer la oferta completa del menú y evita la desaparición súbita de productos mientras interactúa con la interfaz.

---

## 3. Matriz de Cobertura de Clarificaciones y Puntos de Investigación

| Punto de Investigación / Clarificación | Decisión Técnica Asociada | Requerimientos / Invariantes Cubiertos |
|---|---|---|
| **C-1: AND Filtros + Búsqueda y Limpieza Unificada** | D-002 | INV-14, INV-16, FR-018, FR-020, EC-013, EC-018 |
| **C-2: Selector sin Tope y Validación al Agregar** | D-006 | INV-13, FR-015, SC-011, EC-011 |
| **C-3: Persistencia en Sesión por Restaurante** | D-003 | INV-16, FR-024 |
| **C-4: Alérgenos Neutros sin Perfil Dietario** | D-007 | INV-6, FR-008 |
| **C-5: Invalidación en Panel Abierto en $\le 3$s** | D-001 | INV-5, FR-007, SC-010, EC-004 |
| **Degradación de Imagen y Scroll Intacto** | D-004 | INV-1, INV-2, INV-3, INV-4, FR-003 a FR-006, SC-001 a SC-005, EC-001 a EC-003 |
| **Formateo Regional y Consistencia de Precio** | D-005 | INV-9, INV-10, INV-11, INV-12, FR-011 a FR-014, SC-007 a SC-009, EC-009, EC-010 |
| **Platos Agotados bajo Filtros Activos** | D-008 | INV-17, FR-021, FR-023, SC-014, EC-019, EC-020 |

---

## 4. Estado de Clarificaciones

- Total de incógnitas técnicas abiertas: **0**.
- Total de Clarifications resueltas: **5/5**.
- Coherencia con la Constitución SDD v2.0.0: **100% PASS**.
- Conclusión: Fase 0 completada satisfactoriamente. Se autoriza el avance a la Fase 1 (Modelo de Datos, Contratos y Quickstart).

