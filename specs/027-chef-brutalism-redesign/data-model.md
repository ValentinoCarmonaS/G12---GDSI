# Modelo de datos y entidades de UI (Phase 1): Spec 027

**Feature**: `specs/027-chef-brutalism-redesign/spec.md`  
**Date**: 22/11/2026  
**Scope**: Capa exclusivamente frontend (sin cambios en entidades de dominio, base de datos ni contratos HTTP Axios de `001`–`023`).

---

## 1. Entidades de interfaz y estado local

### 1.1 `KitchenBrutalistSurface` (`BrutalistCard`)
Contenedor estructural Brutalism que estandariza tarjetas, paneles, métricas y bloques de formulario del Chef conforme a `.specify/memory/designs/chef.DESIGN.md`.

| Campo / Prop | Tipo | Reglas y restricciones |
|---|---|---|
| `variant` | `'default' \| 'elevated' \| 'subtle' \| 'alert-warning' \| 'alert-danger'` | `default`: superficie `#FFFFFF`, borde `2 px #111827`, sombra `4px 4px 0px #111827`; `elevated`: fondo `#F4F4F0` con mismo marco duro; `subtle`: fondo `#E7E7E0` sin sombra para agrupaciones secundarias sin cards-in-cards; `alert-warning`: marco `4 px` `--ee-warning-border` (alerta KDS `>= 15 min`, manteniendo interior neutro); `alert-danger`: marco `2 px` `--ee-danger-border` con acento visual redundante (`Out of Stock`). |
| `padding` | `'none' \| 'sm' \| 'md' \| 'lg'` | Mapeado a la escala `0 / 12px / 16px / 24px` (`4/8/12/16/24/32 px`). |
| `interactive` | `boolean` | Cuando es `true`, habilita respuesta mecánica táctil (`active:translate-x-[2px] active:translate-y-[2px] active:shadow-none`) anulada bajo `prefers-reduced-motion`. |
| `as` | `'div' \| 'section' \| 'article' \| 'aside'` | Semántica HTML explícita. |

### 1.2 `BrutalistModal`
Diálogo modal accesible específico del rol Chef, compuesto sobre `useAccessibleOverlay` (`src/components/diner/useAccessibleOverlay.ts` sin modificar Spec `025`).

| Campo / Prop | Tipo | Reglas y restricciones |
|---|---|---|
| `isOpen` | `boolean` | Controla montaje y activación del bloqueo de scroll y retorno de foco. |
| `title` | `string` | Conectado por `aria-labelledby` en tipografía `Darker Grotesque` negrita. |
| `subtitle` | `string` | Opcional; eyebrow técnico en `JetBrains Mono` mayúsculas. |
| `size` | `'sm' \| 'md' \| 'lg'` | Ancho máximo responsivo con `max-h-[90vh]` y cuerpo con scroll vertical interno. |
| `dismissible` | `boolean` | Cierre por tecla `Escape`, botón cerrar (`>= 48×48 px`) y clic en backdrop. |
| `footer` | `ReactNode` | Acciones primarias/secundarias con objetivos táctiles `>= 56 px`. |

### 1.3 `OneTapKitchenNavigation` (`RoleLayoutShell` + `ChefMobileBottomBar`)
Extensión controlada de la navegación del Chef derivada de `ROLE_NAVIGATION_CONFIG.CHEF`.

- **Tres destinos de primer nivel (Spec `005` C-08)**:
  1. `Comandas KDS` → `/cocina` (`exact`; etiqueta móvil `KDS` o `Comandas KDS` legible sin truncar).
  2. `Gestionar Platos` → `/cocina/platos` (`prefix`; etiqueta móvil `Platos`).
  3. `Ingredientes` → `/cocina/ingredientes` (`prefix`; etiqueta móvil `Ingredientes`).
- **Comportamiento responsivo**:
  - Tableta y escritorio (`>= 768 px`, `md:`): enlaces directos en cabecera (`min-h-[48px]`, borde `2 px #111827`, estado activo `aria-current="page"` en terracota `#DD614C` con tinta `#111827`); `ChefMobileBottomBar` oculta (`md:hidden`).
  - Móvil (`< 768 px`): `ChefMobileBottomBar` fija al borde inferior (`min-h-[64px]`, 3 botones `min-h-[56px]`, borde superior `2 px #111827`, soporte `safe-area-inset-bottom`); menú hamburguesa en cabecera reservado para metadatos de cuenta y `Cerrar sesión` (sin enlace a `/perfil`).
  - Contenedor `<main>`: `fullWidth` en `/cocina` elimina `max-w-7xl`; en móvil reserva relleno inferior (`pb-24 md:pb-6`, y `pb-40 md:pb-6` en el editor por la barra de acciones fija).

### 1.4 `KdsKitchenTicketCard` (`/cocina`)
Representación visual de `KdsTicket` en el tablero KDS Brutalism claro:

| Bloque | Datos y formato | Reglas ergonómicas y de estado |
|---|---|---|
| **Cabecera** | `#ticketNumber` XXL (`JetBrains Mono`), `timeSlotStart - timeSlotEnd`, modalidad (`Inmediato` / `Programado`), y en preparación: `formattedTime` (`MM min`), botón `+5` (`>= 56×56 px`), badge `+N min` | Separación física `>= 12 px` respecto de controles adyacentes; `select-none`. |
| **Cuerpo (solo `IN_PREPARATION`)** | Lista `items` en renglones `[quantity]x [dishName]` XXL (`text-xl sm:text-2xl font-bold text-ee-text`) | Cero checkboxes por plato; cero textos de relleno de notas vacías; envoltura limpia en `360 px`; tinta neutra `#111827` invariable incluso bajo demora `>= 15 min`. |
| **Pie** | Botón ancho completo `EMPEZAR` (`QUEUED`) o `LISTO` (`IN_PREPARATION`) | Altura mínima `64 px`; `EMPEZAR` bloquea doble clic mientras muta; `LISTO` despacha en 1 toque sin modal y revierte con `FeedbackState` ante fallo. |
| **Estado `delayed` (`elapsedMinutes >= 15`)** | `border-4 border-ee-warning-border`, `data-delayed="true"`, badge cronómetro en tríada `warning` con ícono/etiqueta `DEMORA` | Distinción visual en `< 1 s` sin teñir la lectura de platos. |

### 1.5 `UnifiedDishDraftState` (`/cocina/platos/nuevo` y `/cocina/platos/:id`)
Estado local unificado que gobierna los cuatro pasos del editor de fichas técnicas y la orquestación final del Paso 4.

| Campo | Tipo | Semántica y validación |
|---|---|---|
| `persistedDishId` | `string \| null` | `null` en plato nuevo hasta que `createDishDraft` responde; se conserva si falla un paso posterior de la orquestación del Paso 4 para reintentar con `updateDish` sin duplicar. |
| `status` | `DishStatus` | Por defecto `'DRAFT'`. Si es `'PENDING_PRICE'`, `'PUBLISHED'` o `'CANCELLED'`, activa `isReadOnly = true`. |
| `name` | `string` | Obligatorio al avanzar desde Paso 1 (`trim().length > 0`, máx. `100`). |
| `category` | `DishCategory` | Categoría cerrada de `DISH_CATEGORIES`. |
| `description` | `string` | Opcional, máx. `500` caracteres. |
| `existingPhotoUrl` | `string \| null` | URL actual en servidor si el borrador ya posee imagen. |
| `pendingPhotoFile` | `File \| null` | Archivo local validado (`<= 5 MB`, JPEG/PNG/WebP) pendiente de subida en Paso 4. |
| `photoMarkedForDeletion` | `boolean` | Marca eliminación de fotografía previa al persistir en Paso 4. |
| `recipe` | `RecipeIngredientItem[]` | Líneas de escandallo; al avanzar desde Paso 2 exige cantidades `> 0` y enteros para `'u'`. |
| `allergensConfirmed` | `boolean` | Confirmación explícita obligatoria para habilitar `Guardar y enviar a revisión` (admite `restrictionCodes.length === 0`). |
| `restrictionCodes` | `string[]` | Códigos combinados de estilos de vida y alérgenos comunes. |
| `isDirty` | `boolean` | `true` tras cualquier edición del usuario; activa badge `"Cambios sin guardar"`, `beforeunload` y confirmación en `← Volver a Platos`. Se reinicia a `false` tras guardado exitoso o en modo solo lectura. |

#### Transiciones de estado del asistente
1. `Nuevo (/cocina/platos/nuevo)` → inicializa `persistedDishId = null`, `status = 'DRAFT'`, `activeTab = 'identity'`.
2. `Editar (/cocina/platos/:id)` → carga `dishApi.getDishDetail(id)` y, si `status === 'DRAFT'`, `chefReviewFeedbackApi.getFeedback(id)` precargando propuestas culinarias del último ciclo (`FR-042`) sin ningún campo financiero.
3. `Navegación Paso 1 ↔ Paso 2 ↔ Paso 3 ↔ Paso 4` → `"Siguiente"` valida el paso actual y conserva el borrador en memoria sin llamar al servidor; `"Anterior"` y el stepper permiten volver libremente sin perder datos.
4. `Paso 4 → Guardar borrador` → orquesta `createDishDraft`/`updateDish` → foto → receta → alérgenos; mantiene `DRAFT`, fija `isDirty = false`.
5. `Paso 4 → Guardar y enviar a revisión` → habilitado únicamente si `isIdentityComplete && isRecipeComplete && isAllergensComplete`; ejecuta los mismos pasos de persistencia y concluye con `submitForReview(dishId)` → transiciona a `PENDING_PRICE` (`isReadOnly = true`, `isDirty = false`).
6. `Fallo intermedio en Paso 4` → conserva `persistedDishId` (y actualiza URL con `replace: true` si se creó en el intento) + borrador completo en memoria + muestra `FeedbackState` accionable para reintento seguro.

---

## 2. Trazabilidad requisitos → componentes y pruebas

| Requisito | Componentes / Archivos principales | Cobertura de verificación |
|---|---|---|
| `FR-001`, `FR-002`, `FR-003`, `FR-004` | `BrutalistCard.tsx`, `BrutalistModal.tsx`, `DishStatusBadge.tsx`, primitivos `src/components/primitives/*`, `chef.DESIGN.md` | `tests/chef/brutalistBlocksAndNav.test.tsx`, auditoría Impeccable `/critique` (modo Operate) |
| `FR-005` | `ChefMobileBottomBar.tsx`, `RoleLayoutShell.tsx`, `WorkspaceLayout.tsx`, `roleNavigationConfig.ts` | `tests/chef/brutalistBlocksAndNav.test.tsx`, `tests/components/RoleLayoutShell.test.tsx` |
| `FR-006`, `FR-007`, `FR-008` | `KitchenWorkspacePage.tsx`, `KitchenKanbanBoard.tsx`, `KdsQueuedCard.tsx`, `KdsInPreparationCard.tsx`, `KitchenTicketDelayControl.tsx`, `DishRejectionFeedbackBanner.tsx` | `tests/chef/kdsBrutalistErgonomics.test.tsx`, `tests/components/KitchenKanbanBoard.test.tsx`, `tests/components/KitchenTicketReadyDispatch.test.tsx`, `tests/pages/KitchenWorkspacePage.test.tsx` |
| `FR-009`, `FR-010` | `ChefDishesPage.tsx`, `ChefDishPauseToggle.tsx`, `DishStatusBadge.tsx`, `BrutalistModal.tsx` | `tests/chef/chefDishesBrutalist.test.tsx`, `tests/pages/ChefDishesPage.test.tsx`, `tests/components/ChefDishPauseToggle.test.tsx` |
| `FR-011`, `FR-012` | `ChefDishEditorPage.tsx`, `chefDishPersistence.ts`, `DishStepper.tsx`, `BasicIdentityStep.tsx`, `RecipeBOMStep.tsx`, `IngredientSelectorModal.tsx`, `AllergenStep.tsx`, `ReviewSubmitStep.tsx` | `tests/chef/dishEditorUnifiedBrutalist.test.tsx`, `tests/pages/ChefDishEditorPage.test.tsx`, `tests/components/dish/*.test.tsx`, `tests/integration/us1`–`us4` |
| `FR-013` | `ChefIngredientsPage.tsx`, `ChefIngredientTable.tsx` | `tests/chef/chefIngredientsBrutalist.test.tsx`, `tests/pages/ChefIngredientsPage.test.tsx` |
| `FR-014`, `FR-015` | Suite completa `001`–`027`, actualización acotada de pruebas autorizadas, cero cambios en `easyeat/backend/` y `src/api/` | `npm test -- --run`, `tsc --noEmit`, `vite build`, navegador real `360/768/1024/1440 px` |
