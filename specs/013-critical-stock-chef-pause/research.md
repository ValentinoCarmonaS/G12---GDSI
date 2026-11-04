# Technical Research & Decision Log: Umbral de Stock Crítico en Inventario y Pausa Manual de Platos por Chef

**Feature**: `013-critical-stock-chef-pause`  
**Fecha**: 01/11/2026  
**Fuentes primarias**: `specs/013-critical-stock-chef-pause/spec.md`, `.specify/memory/constitution.md`, código base de `easyeat` (specs 001, 002, 004, 005, 006, 007, 008, 009, 011).

---

## 1. Contexto y Objetivos Técnicos

La iteración 013 amplía el control operativo del inventario (rol Administrador) y el gobierno del menú durante el servicio en vivo (rol Chef) a través de tres historias de usuario P1:
1. **ADM-4.1.3**: Configuración de un umbral de stock crítico numérico entero individual por ingrediente.
2. **ADM-4.1.4**: Detección y despliegue de alertas visuales de reabastecimiento en el módulo de inventario con filtro rápido de criticidad.
3. **CHF-2.2.5**: Control de alternancia (toggle) para pausa manual y arbitraria de platos en cocina con sincronización colaborativa y ocultamiento inmediato en el catálogo del comensal.

Este documento formaliza las decisiones arquitectónicas de la Fase 0, justificadas contra la evidencia del repositorio y las aclaraciones cerradas del `spec.md`, descartando cualquier patrón no anclado al sistema.

---

## 2. Decisiones Técnicas Fundamentales

### Decisión 1: Reutilización del canal de tiempo real para catálogo de comensales y sincronización entre chefs (SLA $\le 3$ s)

- **Decisión**: Reutilizar el mecanismo preexistente de polling condicional HTTP basado en encabezados de validación `ETag` / `If-None-Match` con respuesta liviana `304 Not Modified` y cadencia de $2500\text{ ms}$, tanto para la propagación de visibilidad en el catálogo del comensal como para la sincronización colaborativa en los paneles de cocina de los chefs (`ChefDishesPage`).
- **Justificación**:
  - `spec.md §INV-15`, `§INV-18`, `§FR-022`, `§SC-006`, `§SC-007` y Clarificación C-11 exigen propagación en $\le 3\text{ s}$.
  - El repositorio ya tiene implementado y probado en producción este patrón en `useRealtimeAvailability.ts` (`intervalMs = 2500`), `DishAvailabilityEngine.java` (cálculo de ETag determinista SHA-256) y `DinerCatalogController.java` (`GET /api/v1/establishments/{tenantId}/catalog/availability`).
  - No requiere infraestructura con estado (WebSockets, Redis Pub/Sub, Server-Sent Events ni Message Brokers), preservando la arquitectura stateless de Spring Boot y React definida en la Constitución (§IV KISS).
- **Alternativas descartadas**:
  - *WebSockets / STOMP*: Rechazado. Introduce conexiones bidireccionales con estado persistente, mayor consumo de memoria en servidor y rompe el patrón establecido en specs 008 y 011.
  - *Server-Sent Events (SSE)*: Rechazado. No existe en el backend actual y añade complejidad innecesaria de reconexión y proxy buffering para una necesidad resuelta con HTTP 304.
  - *Long Polling*: Rechazado. Ocupa hilos del contenedor Tomcat y genera latencia no determinista.

---

### Decisión 2: Representación desacoplada de las tres causas de indisponibilidad (INV-10)

- **Decisión**: Representar y almacenar de forma 100% independiente las tres dimensiones de indisponibilidad sobre el plato, exponiéndolas de manera discriminada en los contratos de servicio y paneles de personal:
  1. `sin_stock`: Derivado dinámicamente en tiempo de ejecución por `DishAvailabilityEngine` a partir del inventario físico remanente de los insumos de su receta (`current_stock >= quantity` y `status == ACTIVE`).
  2. `pausa_chef`: Persistido como columna booleana dedicada `paused_by_chef` en la tabla `dishes` (`DEFAULT FALSE`), conmutable exclusivamente por usuarios con rol Chef.
  3. `desactivacion_admin`: Representado por `is_active` (`BOOLEAN DEFAULT TRUE`) y `status` (`DRAFT`, `PENDING_PRICE`, `PUBLISHED`, `CANCELLED`), gobernado exclusivamente por el Administrador.
  - La disponibilidad agregada resultante para el comensal se calcula como:
    $$\text{Visible} \iff (\text{status} = \text{'PUBLISHED'}) \land (\text{is\_active} = \text{true}) \land (\text{paused\_by\_chef} = \text{false})$$
    $$\text{Comprable} \iff \text{Visible} \land (\text{sin\_stock} = \text{false})$$
- **Justificación**:
  - `spec.md §INV-10`, `§INV-12`, `§INV-13`, `§FR-015`, `§SC-008` y `§SC-014` prohíben explícitamente colapsar estas fuentes en un solo flag genérico (`isAvailable` o `no_disponible`).
  - Cada causa obedece a un actor distinto (Sistema vs Chef vs Administrador), tiene un disparador distinto (consumo/mermas vs contingencia en cocina vs decisión comercial) y una vía de recuperación independiente (reposición vs despausa vs reactivación).
  - La coexistencia de causas (ej. plato sin stock que además es pausado preventivamente por el chef, EC-011) debe mantener intactos ambos estados.
- **Alternativas descartadas**:
  - *Unificar en una columna `availability_status` (ENUM)*: Rechazado. Un enum no permite registrar simultáneamente que un plato carece de stock físico y que además el chef lo pausó por mantenimiento.
  - *Sobrescribir `is_active` con el toggle del chef*: Rechazado. Violaría la autoridad del administrador (INV-10, INV-16) y destruiría el estado comercial fijado por el Back-Office.

---

### Decisión 3: Autorización por Roles y Segregación de Responsabilidades (RBAC)

- **Decisión**: Enforzar en la capa de seguridad backend (`@PreAuthorize`) y en los componentes frontend la estricta separación de privilegios:
  - Configuración y edición de `critical_stock_threshold`: restringido estrictamente a `hasAnyRole('ADMIN', 'ADMINISTRADOR')` en `AdminIngredientController.java`. Intentos de rol Chef devuelven HTTP 403 Forbidden (INV-16, FR-017, SC-009, EC-018).
  - Conmutación de `paused_by_chef`: restringido estrictamente a `hasRole('CHEF')` en `DishController.java` (`PATCH /api/v1/dishes/{id}/chef-pause`). Intentos de rol Admin devuelven HTTP 403 Forbidden (INV-16, FR-018, SC-010, EC-019).
  - Aislamiento multi-tenant: Todas las mutaciones validan que el insumo o plato pertenezca al `tenant_id` resuelto en el contexto de seguridad (`TenantContextHolder`).
- **Justificación**:
  - Garantiza el principio de menor privilegio y la coherencia con specs 001, 002 y 004.
  - En frontend, el control de alternancia (toggle) se renderiza exclusivamente en las vistas del Chef (`ChefDishesPage.tsx`), estando completamente ausente en la interfaz del Administrador (SC-010).
- **Alternativas descartadas**:
  - *Permitir al administrador conmutar la pausa del chef en caso de emergencia*: Rechazado terminantemente por `spec.md §Out of Scope`, `§INV-10` y Clarificación C-4. El administrador posee su propio mecanismo de desactivación comercial.

---

### Decisión 4: Filtro rápido de criticidad en cabecera y composición con resaltado visual

- **Decisión**: Incorporar en la cabecera de `IngredientCatalogTable.tsx` un control de conmutación rápida ("Solo insumos en stock crítico", `criticalOnly: boolean`) que interactúa de manera conjuntiva (intersección lógica AND) con la búsqueda por texto existente (`searchTerm`), el filtro de categoría (`categoryFilter`) y el filtro de estado (`statusFilter`).
  - Backend: Se añade el parámetro opcional `criticalOnly=true` en `GET /api/v1/admin/ingredients`.
  - En la consulta del repositorio `IngredientRepository`: se filtra `status = 'ACTIVE' AND critical_stock_threshold IS NOT NULL AND current_stock < critical_stock_threshold`.
  - Frontend: Las filas de ingredientes críticos mantienen su resaltado visual destacado (insignia o etiqueta de color ámbar/rojo "⚠️ Stock crítico") independientemente de si el filtro rápido está activo o inactivo (FR-005, FR-020, SC-003, SC-015).
- **Justificación**:
  - Permite al administrador obtener de inmediato la lista de compras ante proveedores sin recorrer manualmente todas las páginas o registros del inventario (C-9, FR-020).
  - No oculta información: cuando el filtro rápido está inactivo, todos los ingredientes se listan y los críticos se distinguen visualmente por su distintivo.
- **Alternativas descartadas**:
  - *Crear una página o vista independiente separada para compras*: Rechazado. Duplica componentes, layouts y lógica de navegación.

---

### Decisión 5: Suspensión de alertas en insumos descontinuados (`DISCONTINUED`)

- **Decisión**: Implementar la regla de negocio que suprime la evaluación de criticidad cuando `ingredient.status == IngredientStatus.DISCONTINUED`:
  $$\text{isCritical}(i) \iff (\text{status}(i) = \text{'ACTIVE'}) \land (\text{threshold}(i) \ne \text{null}) \land (\text{stock}(i) < \text{threshold}(i))$$
  - Al cambiar un insumo a `DISCONTINUED`, el atributo `critical_stock_threshold` permanece intacto en la base de datos (preservación histórica).
  - Si el administrador vuelve a activar el ingrediente a `ACTIVE`, la condición de criticidad se reevalúa de manera automática e instantánea en la siguiente consulta (INV-17, FR-021, EC-024, C-10).
- **Justificación**:
  - Un insumo descontinuado no forma parte del aprovisionamiento corriente de la cocina; alertar sobre su falta generaría ruido operativo y pedidos erróneos a proveedores.
- **Alternativas descartadas**:
  - *Borrar el umbral al descontinuar*: Rechazado. Obligaría al administrador a reingresar el valor si el producto vuelve a utilizarse por estacionalidad.

---

### Decisión 6: Carrito de compras ante pausa del chef: preservación con bloqueo (FR-019, EC-023)

- **Decisión**: Adaptar el manejo de disponibilidad en el carrito de sesión del comensal (`CartContext.tsx` y `CartDrawer.tsx`):
  - Si un plato presente en el carrito es pausado por el chef (`paused_by_chef = true`):
    1. Se preserva la línea en el carrito (no se elimina automáticamente de la sesión).
    2. Exhibe un mensaje de advertencia visible y específico: `"Pausado por cocina"`.
    3. Se inhabilitan los controles de incremento unitario (`+`).
    4. Se bloquea la confirmación del pedido (botón de checkout deshabilitado con aviso explícito).
  - La liberación del bloqueo ocurre cuando: (a) el comensal elimina el ítem del carrito mediante el botón de retiro, o (b) el chef desactiva la pausa manual.
- **Justificación**:
  - Cumple estrictamente con Clarificación C-8, `FR-019`, `EC-023` y mantiene consistencia absoluta con la regla de indisponibilidad de la especificación 009 FR-014a.
  - Brinda transparencia al usuario sobre la causa exacta de la contingencia en lugar de mutar silenciosamente el total de su orden.
- **Alternativas descartadas**:
  - *Eliminación silenciosa del plato del carrito*: Rechazado. Degrada la experiencia de usuario y oculta al comensal qué ocurrió con su selección.

---

### Decisión 7: Panel de detalle del comensal ante pausa en tiempo real (EC-017, FR-016)

- **Decisión**: Actualizar reactivamente el componente `DishDetailModal.tsx` al recibir el cambio de disponibilidad vía el hook `useRealtimeAvailability`:
  - Si el modal se encuentra abierto en el instante de la pausa:
    1. Actualiza el estado en $\le 3\text{ s}$ mostrando la insignia `"No disponible / Pausado por cocina"`.
    2. Inhabilita los controles de selección de cantidad y el botón `"Agregar al Carrito"`.
    3. Permite al comensal continuar leyendo la información técnica o cerrar el modal voluntariamente.
    4. Al cerrar el modal, el plato ya no se muestra en la grilla del catálogo (INV-8, EC-017).
- **Justificación**:
  - Cumple con la Clarificación #5 de la especificación 011: el panel no debe cerrarse bruscamente en la cara del usuario mientras lee la descripción o receta.
- **Alternativas descartadas**:
  - *Cierre forzado automático del modal al pausar*: Rechazado. Causa saltos visuales y pérdida de contexto de lectura.

---

### Decisión 8: Autonomía preventiva del toggle del chef y resolución de concurrencia (INV-19, EC-025)

- **Decisión**:
  - El control de alternancia del chef opera de manera desacoplada de cualquier otra restricción: puede conmutarse libremente aun si el plato no tiene stock de receta o si está desactivado por el administrador (INV-19, FR-023, EC-011, EC-012).
  - Resolución de alternancia rápida y concurrencia multi-operador (EC-015, EC-025):
    - La operación `PATCH /api/v1/dishes/{id}/chef-pause` ejecuta una actualización atómica en base de datos:
      `UPDATE dishes SET paused_by_chef = :paused, updated_at = NOW() WHERE id = :id AND tenant_id = :tenantId`.
    - La última confirmación de base de datos prevalece de forma determinista ("last write wins").
    - En el siguiente ciclo de polling ($\le 2.5\text{ s}$), todos los clientes de cocina convergen en el estado confirmado definitivo.
- **Justificación**:
  - Permite a la brigada de cocina anticiparse a contingencias (ej. apagar preventivamente un plato mientras los insumos se reabastecen en bodega o mientras el administrador ajusta precios).
- **Alternativas descartadas**:
  - *Bloqueo pesimista con tokens de edición de pausa*: Rechazado. Sobreingeniería innecesaria para un interruptor operativo de cocina.

---

## 3. Matriz de Estado de Incógnitas (NEEDS CLARIFICATION)

| Punto Técnico Evaluado | Estado | Resolución Técnica |
|---|---|---|
| Reutilización canal tiempo real comensales / chefs | **RESUELTO** | Polling condicional HTTP con ETags (2500 ms) preexistente de spec 008. |
| Modelo de tres causas de indisponibilidad | **RESUELTO** | Tres estados separados e independientes: `sin_stock` (derivado), `paused_by_chef` (columna `dishes`), `is_active`/`status` (admin). |
| RBAC Admin vs Chef en nuevos endpoints | **RESUELTO** | `@PreAuthorize` segregado: Admin para umbral; Chef para pausa de plato. |
| Filtro rápido y resaltado visual en inventario | **RESUELTO** | Parámetro `criticalOnly` en API y filtro interactivo en `IngredientCatalogTable`. |
| Suspensión de alerta en `DISCONTINUED` | **RESUELTO** | Filtro `status == ACTIVE` en lógica de criticidad; preservación histórica del umbral. |
| Reacción del carrito ante pausa del chef | **RESUELTO** | Preservación de ítem con aviso "Pausado por cocina", bloqueo de incremento y checkout. |
| Reacción del modal de detalle ante pausa | **RESUELTO** | Deshabilitación reactiva en $\le 3$ s sin cierre forzado; exclusión al cerrar. |
| Autonomía operativa del toggle de cocina | **RESUELTO** | Control siempre operable; resolución de concurrencia "última confirmación prevalece". |

**Total de incógnitas pendientes (`[NEEDS CLARIFICATION]`): 0.**

