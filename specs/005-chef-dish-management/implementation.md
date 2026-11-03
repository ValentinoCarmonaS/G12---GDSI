# Implementation Notes: C-08 — Reubicar "Gestionar Platos" en la cabecera del chef

**Spec**: [spec.md](./spec.md) §User Story 5 (C-08), §FR-027..FR-032, §SC-009..SC-010
**Plan**: [plan.md](./plan.md) §Phase 6
**Branch**: `fix/C-08-relocate-manage-dishes-chef-header`
**Scope**: Frontend únicamente (`easyeat/frontend`). Sin cambios de backend, rutas, guards ni contratos API.

---

## 1. Componentes afectados

| Componente / archivo | Cambio | Spec |
|---|---|---|
| `src/components/common/WorkspaceLayout.tsx` | Bloque de navegación `CHEF`: tres secciones ("Comandas KDS" `/cocina`, "Gestionar Platos" `/cocina/platos`, "Ingredientes" `/cocina/ingredientes`); estado activo derivado de `useLocation()` con `aria-current="page"`; contenedor `min-w-0 overflow-x-auto` (antes `hidden sm:flex`); estilos naranjas por enlace reemplazados por un set común activo/inactivo | FR-027, FR-030, FR-031 |
| `src/pages/KitchenWorkspacePage.tsx` | Se elimina la tarjeta "Gestión de Platos y Fichas Técnicas" (link a `/cocina/platos`). Se conservan: tarjeta de Ingredientes, 4 tarjetas de estadísticas, banner `DishRejectionFeedbackBanner` (`?dishId=`) y `authApi.me()` | FR-028, FR-032 |
| `src/pages/ChefDishesPage.tsx` | Envuelto en `WorkspaceLayout` (title "Gestión de Platos"); el div raíz pierde el padding redundante (`container ... py-8` → `max-w-6xl`) que ahora aporta el `<main>` del layout | FR-029 |
| `src/pages/ChefDishEditorPage.tsx` | Envuelto en `WorkspaceLayout` (title "Gestión de Platos"); estados de carga y formulario pierden el padding redundante (`max-w-4xl`) | FR-029 |
| `tests/pages/ChefDishesPage.test.tsx` | Mock pass-through de `WorkspaceLayout` (patrón de `ChefDishEditorPage.test.tsx`) | FR-029 |
| `tests/integration/us1_basic_identity.test.tsx`, `us2_recipe_definition.test.tsx`, `us3_allergen_labeling.test.tsx`, `us4_review_submission.test.tsx` | Mock pass-through de `WorkspaceLayout` | FR-029 |
| `tests/components/WorkspaceLayout.test.tsx` | Nuevos tests: tres enlaces del chef con `href` correcto, `aria-current="page"` en la sección activa según ruta | FR-027, FR-030 |
| `tests/pages/KitchenWorkspacePage.test.tsx` | Nuevo test: la vista KDS ya no renderiza la tarjeta de gestión de platos y conserva ingredientes + estadísticas (los tests del banner C-07 permanecen intactos) | FR-028, FR-032 |

## 2. Rutas (sin cambios)

| Ruta | Guard | Página | Estado C-08 |
|---|---|---|---|
| `/cocina` | `RoleRouteGuard(['CHEF'])` | `KitchenWorkspacePage` | Entrada por cabecera; sin tarjeta de platos |
| `/cocina/platos` | `RoleRouteGuard(['CHEF'])` | `ChefDishesPage` | Ahora accesible desde cabecera; renderiza dentro de `WorkspaceLayout` |
| `/cocina/platos/nuevo`, `/cocina/platos/:id` | `RoleRouteGuard(['CHEF'])` | `ChefDishEditorPage` | Renderiza dentro de `WorkspaceLayout` |
| `/cocina/ingredientes` | `RoleRouteGuard(['CHEF'])` | `ChefIngredientsPage` | Sin cambios |

`src/router/AppRoutes.tsx`, `src/router/RoleRouteGuard.tsx` y `src/router/postAuthRouting.ts` **no se modifican**: `/cocina/platos` ya está cubierto por el perímetro de prefijo `CHEF: ['/cocina', '/kds']`, y el landing post-auth del chef sigue siendo `/cocina` (spec 002 US4 / FR-005).

## 3. Decisiones de implementación

1. **Estado activo por ruta, no por estilo fijo**: `pathname === '/cocina'` (coincidencia exacta) y `pathname.startsWith(...)` para `/cocina/platos` y `/cocina/ingredientes`. De este modo `/cocina/platos/nuevo` y `/cocina/platos/:id` iluminan "Gestionar Platos" y nunca "Comandas KDS" (evita la colisión de prefijo `/cocina`).
2. **Accesibilidad**: solo el enlace activo recibe `aria-current="page"`; los inactivos usan `hover:bg-gray-100 text-gray-700 font-medium` y el activo `bg-orange-50 text-orange-700 font-semibold`.
3. **Viewport pequeño**: el nav del chef pasa de `hidden sm:flex` a `flex min-w-0 overflow-x-auto`; el contenedor izquierdo de la cabecera recibe `min-w-0` para permitir que el nav se contraiga y haga scroll en vez de desbordar el header de `h-16`.
4. **Desacoplamiento KDS**: la vista de comandas deja de ser el intermediario para llegar a la gestión de platos; su único punto de entrada removido es la tarjeta de platos. Banner de rechazo (C-07) y tarjeta de ingredientes quedan fuera de alcance por decisión explícita.
5. **Envoltura de vistas de platos**: a nivel de página (no de ruta) para no triplicar el `WorkspaceLayout` en las tres rutas y para que cualquier test que renderice la página directamente obtenga el mismo layout que en producción.
6. **Ajuste de tests**: se usa el mock pass-through ya existente en el repo (`vi.mock('@/components/common/WorkspaceLayout', () => ({ WorkspaceLayout: ({ children }) => <div>{children}</div> })`) en lugar de re-renderizar el layout real, manteniendo el foco de cada test en su historia de usuario. La cabecera en sí se valida en su propio test de componente.

## 4. Fuera de alcance (explícito)

- Mover el banner de rechazo C-07 a `/cocina/platos?dishId=` (spec 007/008, T089–T090) — FR-032.
- Modificar la tarjeta de Ingredientes ni las 4 tarjetas de estadísticas del KDS (hardcodeadas en `0`).
- Roles ENCARGADO / ADMINISTRADOR / COMENSAL, guards de ruta, backend y contratos.
- Eliminar el alias `/kds/*` o el enlace inverso "Volver" de `ChefIngredientsPage` → `/cocina`.

## 5. Verificación

```bash
cd easyeat/frontend
npm test          # Vitest: unit + component + integration
npm run lint      # tsc --noEmit
```

Gate de aceptación: ambos en verde, con los tests nuevos de cabecera (3 enlaces + `aria-current`) y de ausencia de tarjeta en el KDS, y sin regresiones en `KitchenWorkspacePage` T029 (banner C-07), `ChefDishesPage` T024, `ChefDishEditorPage` ni `us1`–`us4`.
