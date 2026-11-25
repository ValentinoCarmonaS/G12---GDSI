# Guía de validación (Quickstart): Spec 027 — Rediseño Brutalism del espacio del Chef

**Feature**: `specs/027-chef-brutalism-redesign/spec.md`  
**Contracts**: [contracts/brutalism-ui-blocks.contract.ts](contracts/brutalism-ui-blocks.contract.ts), [contracts/chef-navigation-and-kds.contract.ts](contracts/chef-navigation-and-kds.contract.ts), [contracts/chef-unified-dish-editor.contract.ts](contracts/chef-unified-dish-editor.contract.ts), [contracts/chef-screens-audit.contract.ts](contracts/chef-screens-audit.contract.ts)

---

## 1. Prerrequisitos

- Rama activa: `feat/027-chef-brutalism-redesign` (creada desde `feat/frontend-redesign`).
- Dependencias instaladas en `easyeat/frontend/`.

```powershell
cd easyeat/frontend
npm install
```

## 2. Verificación automatizada (SC-002, SC-005, SC-006)

Ejecutar la suite trazable de Spec `027`, la suite completa de regresión (`001`–`027`), la validación de tipos y el build de producción:

```powershell
# 1. Suite trazable de Spec 027
npx vitest run tests/chef/

# 2. Suites de Chef, KDS, platos y navegación relacionadas
npx vitest run tests/components/KitchenKanbanBoard.test.tsx tests/components/KitchenTicketReadyDispatch.test.tsx tests/pages/KitchenWorkspacePage.test.tsx tests/pages/ChefDishesPage.test.tsx tests/pages/ChefDishEditorPage.test.tsx tests/pages/ChefIngredientsPage.test.tsx tests/components/RoleLayoutShell.test.tsx tests/components/dish/ tests/integration/us1_basic_identity.test.tsx tests/integration/us2_recipe_definition.test.tsx tests/integration/us3_allergen_labeling.test.tsx tests/integration/us4_review_submission.test.tsx

# 3. Suite completa de frontend (cero regresiones 001–027)
npm test -- --run

# 4. Typecheck y build de producción
npx tsc --noEmit
npm run build
```

**Resultado esperado**: 100 % de las pruebas en verde, 0 errores de TypeScript, bundle compilado sin nuevas dependencias externas y cero cambios en `easyeat/backend/` ni `easyeat/frontend/src/api/`.

## 3. Escenarios de validación en navegador real (SC-001, SC-002, SC-003, SC-004, SC-006)

Levantar el servidor de desarrollo (`npm run dev`), iniciar sesión con una cuenta con rol `CHEF` y validar en **360 px** (móvil), **768 px** (tableta vertical), **1024 px** (tableta horizontal) y **1440 px** (escritorio):

1. **Arquitectura Brutalism y navegación táctil de un toque (US1)**:
   - En `768 px`, `1024 px` y `1440 px`: cabecera Brutalism con borde inferior duro `2 px #111827`, los tres accesos directos (`Comandas KDS`, `Gestionar Platos`, `Ingredientes`) con altura mínima `48 px` y estado activo inequívoco; sin barra inferior móvil.
   - En `360 px`: barra inferior fija Brutalism con tres botones de un toque (`>= 56 px`) para `Comandas KDS`, `Platos` e `Ingredientes`, sin tapar tarjetas ni botones de contenido; menú hamburguesa en cabecera con datos de cuenta y `Cerrar sesión` (sin enlace a `/perfil`).
   - Verificar `prefers-reduced-motion: reduce`: desaparecen micro-desplazamientos táctiles y animaciones continuas manteniendo bordes y estados activos explícitos.
   - Verificar con sesiones de `COMENSAL`, `ENCARGADO` y `ADMINISTRADOR` que ningún otro rol recibe la barra inferior ni estilos Brutalism.

2. **KDS a pantalla completa para cocina caótica en `/cocina` (US2)**:
   - Verificar ancho completo del tablero sin el bloque redundante de ingredientes, tira ultracompacta de 4 KPIs arriba (`En Cola`, `En Preparación`, `Para Retirar`, `Completadas hoy`) en `JetBrains Mono` tabular y cero clases de modo oscuro heredado.
   - En `360 px`: conmutar entre `Pendientes` y `En preparación` mediante el selector rápido fijo (`>= 56 px`), con tarjetas al 100 % de ancho y cero desbordamiento horizontal.
   - Comprobar anatomía de tarjeta: `#Ticket` XXL, franja horaria, insignia `Inmediato` / `Programado`, cronómetro `MM min` + botón `+5` (`>= 56×56 px`) en cabecera, platos `[Cantidad]x [Nombre]` XXL en tinta `#111827` sin checkboxes ni notas vacías, y botón ancho completo `EMPEZAR` / `LISTO` (`>= 64 px`) físicamente separado de `+5`.
   - Confirmar que una comanda `>= 15 min` activa borde perimetral `4 px` e insignia `warning` con etiqueta redundante sin teñir el texto de los platos, que `LISTO` despacha sin modal de confirmación y que ante error de red aparece el banner Brutalism de reintento conservando las comandas visibles.

3. **Catálogo de platos y pausa rápida en `/cocina/platos` (US3)**:
   - Probar los chips táctiles Brutalism (`>= 48 px`) por estado y verificar grilla de tarjetas sin tablas ni scroll horizontal en `360`–`1440 px`.
   - En un plato `PUBLISHED`, alternar el interruptor individual `Estado Operativo` (`>= 56 px`) comprobando actualización optimista y ausencia total de botones de pausa masiva (EC-016).
   - En un plato `DRAFT`, pulsar `Eliminar` y validar el modal Brutalism accesible (`Escape`, gestión de foco, botones `>= 56 px`) sin diálogos nativos `window.confirm`/`window.alert`.

4. **Editor guiado de fichas técnicas en `/cocina/platos/nuevo` y `/cocina/platos/:id` (US4)**:
   - Recorrer los pasos `01` a `04` usando `"Anterior"` / `"Siguiente"` verificando validaciones por paso (nombre obligatorio, foto `<= 5 MB` y formato válido, cantidades `> 0` y enteros en `'u'`, confirmación de alérgenos) y conservación de datos en memoria.
   - Comprobar composición de 2 columnas en escritorio en Paso 1, tarjetas BOM y modal selector Brutalism claro en Paso 2 (preservando `Filtrar por categoría`), grillas de tarjetas checkbox en Paso 3, y lista de verificación con `Guardar borrador` y `Guardar y enviar a revisión` en Paso 4.
   - Verificar en `360 px` que la barra de acciones fija del paso y la barra inferior móvil no se superponen ni ocultan errores; verificar badge `"Cambios sin guardar"` y modal de confirmación al pulsar `← Volver a Platos` con cambios pendientes.
   - Verificar un borrador devuelto con propuestas precargadas y un plato en `PENDING_PRICE` / `CANCELLED` en solo lectura, confirmando cero datos financieros visibles.

5. **Catálogo de ingredientes en `/cocina/ingredientes` (US5)**:
   - Verificar grilla responsiva de tarjetas Brutalism (1 columna móvil, 2 tableta, 3 escritorio), enlace `← Volver al Panel KDS de Cocina`, buscador `"Buscar materia prima..."`, filtro por categoría y paginación.
   - Confirmar etiquetas `In Stock` / `Out of Stock`, énfasis inmediato `danger` (borde + insignia con texto e ícono) en `Out of Stock` y ausencia absoluta de costos, precios o símbolos monetarios (FR-023).

6. **Auditoría visual Impeccable (`/critique`)**:
   - Evaluar las cinco rutas contra `.specify/memory/designs/chef.DESIGN.md`, WCAG 2.2 AA, política Zero AI-Slop y modo **Operate**.
