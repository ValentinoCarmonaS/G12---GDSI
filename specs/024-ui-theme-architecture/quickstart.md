# Guía de validación rápida (`quickstart.md`): Arquitectura de temas visuales por rol y primitivos base accesibles

**Especificación**: `specs/024-ui-theme-architecture/spec.md`  
**Plan**: `specs/024-ui-theme-architecture/plan.md`  
**Rama**: `feat/024-ui-theme-architecture`  
**Fecha**: 21/11/2026

Esta guía define los escenarios ejecutables para verificar de punta a punta los criterios de éxito `SC-001` a `SC-009` sin modificar contratos del servidor ni introducir rutas artificiales de demostración.

---

## 1. Prerrequisitos

- Node.js 18+ y npm disponibles en la estación de trabajo.
- Dependencias del frontend instaladas en `easyeat/frontend/`:

```bash
cd easyeat/frontend
npm install
```

- Para verificación visual interactiva en navegador real sobre rutas reales (Escenario 7), levantar los servicios locales documentados en `README.md` (Docker PostgreSQL/Mailpit, backend en `http://localhost:8080` y frontend en `http://localhost:3000`).

---

## 2. Escenario 1 — Remediación de contratos `.DESIGN.md`, contraste WCAG 2.2 AA y sub-contrato de visualización de datos (`SC-001`, `SC-009`)

### Comandos

```bash
cd easyeat/frontend
npx vitest run tests/theme/themeContractsContrast.test.ts
```

### Resultados esperados

1. Los cuatro archivos `.specify/memory/designs/comensal.DESIGN.md`, `mostrador.DESIGN.md`, `chef.DESIGN.md` y `admin.DESIGN.md` están sincronizados con `data-model.md` y registran `0` ocurrencias de `Inter`.
2. La suite verifica mediante `getContrastRatio` (WCAG 2.2) el 100% de los pares declarados en `ROLE_THEME_REGISTRY`:
   - Texto principal y secundario sobre superficie base y elevada `>= 4.5:1` (incluyendo Mostrador `#F8FAFC` sobre `#111111` a `18.05:1`).
   - `onPrimary`, `onSecondary`, `onDanger` en estado normal y hover `>= 4.5:1`.
   - `borderStrong`, `primaryBorder`, `secondaryBorder` y `focusRing` `>= 3.0:1`.
   - Tríadas `neutral`, `info`, `success`, `warning`, `danger`: `fg/bg >= 4.5:1` y `border/surface >= 3.0:1`.
   - Sub-contrato `DataVizThemeTokens` expuesto desde la misma fuente única de verdad: las 5 series categóricas `>= 3.0:1` (verificadas `>= 4.5:1`), `axisLabelFg >= 4.5:1` y `tooltip.fg/bg >= 4.5:1`.

---

## 3. Escenario 2 — Resolución determinista por ruta y cero filtración entre temas (`SC-002`)

### Comandos

```bash
cd easyeat/frontend
npx vitest run tests/theme/routeThemeScope.test.tsx
```

### Resultados esperados

1. El 100% de las rutas de `easyeat/frontend/src/router/AppRoutes.tsx` resuelve el tema esperado al primer renderizado:
   - `/login` (sin deep-link de personal), `/register`, `/forgot-password`, `/reset-password`, `/auth/reset-password`, `/verificar-email`, `/verify-email`, `/` (sin sesión), `*`, `/descubrimiento`, `/discovery`, `/catalogo/*`, `/catalog/*`, `/perfil`, `/profile`, `/checkout/*`, `/pedidos/*`, `/seguimiento/*` → `data-theme="comensal"`.
   - `/` con usuario autenticado → hereda el tema del rol destino (`ENCARGADO` → `mostrador`, `CHEF` → `chef`, `ADMINISTRADOR` → `admin`, `COMENSAL` → `comensal`).
   - `/login` con `location.state.from = "/mostrador"` → `data-theme="mostrador"` (y análogamente `/cocina/*` → `chef`, `/admin/*` → `admin`).
   - `/mostrador/*` y `/counter/*` → `data-theme="mostrador"`.
   - `/cocina/*` y `/kds/*` → `data-theme="chef"`.
   - `/admin/*` → `data-theme="admin"`.
   - `/first-login-password-change` → hereda el tema del rol autenticado y cae a `data-theme="admin"` si el rol no está resuelto.
2. Transiciones secuenciales `mostrador → login → chef → admin → comensal` reemplazan completamente `data-theme` en `document.documentElement` y en `data-testid="theme-scope-root"` con `0` atributos o estilos residuales del rol anterior.

---

## 4. Escenario 3 — Aislamiento por capas con branding de establecimiento (`SC-006`)

### Comandos

```bash
cd easyeat/frontend
npx vitest run tests/theme/brandLayerIsolation.test.tsx
```

### Resultados esperados

1. En `/catalogo` con un establecimiento personalizado (Spec 016), `BrandThemeProvider` inyecta únicamente `--brand-*` en `data-testid="brand-theme-container"`, mientras `[data-theme="comensal"]` conserva intactos sus tokens `--ee-*`.
2. Al pasar a un establecimiento sin personalización o a una ruta sin establecimiento, las variables `--brand-*` vuelven a `DEFAULT_NEUTRAL_BRANDING` o se desmontan sin alterar la paleta Bento.
3. En `/admin/mi-local`, `/mostrador` y `/cocina`, los contenedores operativos permanecen regidos exclusivamente por `--ee-*` sin filtración de `--brand-*`.

---

## 5. Escenario 4 — Contenedores de navegación, menú hamburguesa, accesibilidad por teclado y excepción Chef C-08 (`SC-003`, `SC-004`)

### Comandos

```bash
cd easyeat/frontend
npx vitest run tests/components/WorkspaceLayout.test.tsx tests/components/RoleLayoutShell.test.tsx tests/components/RoleHamburgerMenu.test.tsx tests/components/AdminNotificationBell.test.tsx
```

### Resultados esperados

1. El botón hamburguesa y `AdminNotificationBell` exponen `aria-expanded`, `aria-controls`/`aria-haspopup` y etiquetas accesibles descriptivas, objetivos `>= 44×44 px`, y cierran el panel con clic, `Escape` (devolviendo foco al disparador) y clic exterior.
2. Mapa completo verificado por rol con `aria-current="page"` dinámico (y enlace a `/perfil` exclusivo de `COMENSAL`):
   - **Administrador**: 7 secciones (`Panel`, `Auditoría`, `Personal`, `Inventario`, `Operaciones`, `Mi local`, `Métricas`) + `AdminNotificationBell` (sin enlace a `/perfil`).
   - **Comensal**: 4 secciones (`Descubrimiento`, `Catálogo`, `Mis pedidos`, `Mi perfil`) + avatar navegable a `/perfil` + `Cerrar sesión`; estado invitado muestra `Iniciar sesión` y `Registrarse`.
   - **Mostrador**: `Despacho en mostrador` + metadatos de cuenta y cierre de sesión (sin enlace a `/perfil`).
   - **Chef**: 3 secciones (`Comandas KDS`, `Gestionar Platos`, `Ingredientes`) accesibles directamente en 1 interacción en cabecera de escritorio (C-08) y unificadas en el menú hamburguesa en móvil (sin enlace a `/perfil`).
3. En `/admin/analytics`, el shell de Administrador envuelve la vista desde `AppRoutes` sin romper `AdminAnalyticsPage.test.tsx`, y tanto `AdminAnalyticsPage` (`data-legacy-surface="dark"`) como el contenido heredado de `CounterWorkspacePage` (`data-legacy-surface="light"`) conservan legibilidad íntegra.

---

## 6. Escenario 5 — Matriz de 20 combinaciones de primitivos base y polimorfismo puro (`SC-004`, `SC-005`)

### Comandos

```bash
cd easyeat/frontend
npx vitest run tests/components/primitives/
```

### Resultados esperados

1. Los cinco primitivos (`Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`) superan todas las pruebas de variantes, accesibilidad y semántica bajo los cuatro temas (`5 × 4 = 20` combinaciones):
   - `Button`: variantes `primary`, `secondary`, `ghost`, `outline`, `danger`; tamaños táctiles; `loading` con `aria-busy="true"` y bloqueo de doble disparo.
   - `StatusBadge`: variantes `neutral`, `info`, `success`, `warning`, `danger` con etiqueta textual obligatoria e ícono/prefijo opcional.
   - `FeedbackState`: `role="alert"` + `aria-live="assertive"` para `danger`; `role="status"` + `aria-live="polite"` para `info`/`success`/`warning`; acción de reintento y cierre por teclado.
   - `LoadingSkeleton`: formas `text`, `block`, `circle`; figuras `aria-hidden="true"`; texto `sr-only`; clase `motion-reduce:animate-none`.
   - `EmptyState`: ícono opcional, título, descripción y acción principal con `Button` del tema activo.
2. Verificación estática: cero referencias a `'comensal' | 'mostrador' | 'chef' | 'admin'` ni a roles de usuario dentro de `easyeat/frontend/src/components/primitives/`.

---

## 7. Escenario 6 — Regresión integral de `001`–`023`, tipado y autonomía tipográfica en build (`SC-007`, `SC-009`)

### Comandos

```bash
cd easyeat/frontend
npm run lint
npm test
npm run build
```

### Resultados esperados

1. `tsc --noEmit` y la suite completa de Vitest (`001`–`023` + `024`) finalizan con código de salida `0` y sin debilitar aserciones conductuales existentes.
2. El directorio `easyeat/frontend/dist/assets/` contiene los archivos `.woff2` autoalojados de `dm-sans`, `archivo-black`, `archivo`, `darker-grotesque`, `plus-jakarta-sans` y `jetbrains-mono` únicamente para los pesos configurados.
3. Una búsqueda sobre `easyeat/frontend/dist/` devuelve `0` referencias a `fonts.googleapis.com`, `fonts.gstatic.com` y `Inter`.
4. `git diff` confirma `0` modificaciones en `easyeat/backend/` y `0` modificaciones en `easyeat/frontend/src/api/`.

---

## 8. Escenario 7 — Auditoría visual constitucional (`SC-008`)

### Procedimiento

1. Con la aplicación corriendo en `http://localhost:3000` (credenciales de prueba en `README.md`), recorrer en navegador real las rutas reales en viewports móvil (`360×800`) y escritorio (`1280×800`):
   - Pública / Comensal: `/login`, `/descubrimiento`, `/catalogo`, `/perfil`.
   - Mostrador: `/mostrador`.
   - Chef: `/cocina`, `/cocina/platos`, `/cocina/ingredientes`.
   - Administrador: `/admin`, `/admin/mi-local`, `/admin/analytics`.
2. Verificar en la pestaña **Network → Font** de DevTools que:
   - Todas las fuentes se sirven desde el origen propio `/assets/*.woff2` (`0` solicitudes a CDN externos).
   - Cada rol descarga únicamente las familias de su tema activo.
3. Ejecutar la auditoría visual de diseño y accesibilidad mediante Impeccable (`/critique`) contra los cuatro `.DESIGN.md` remediados, confirmando ausencia de defectos críticos de contraste, jerarquía y patrones Zero AI-Slop.

---

## 9. Registro de verificación de cierre (Phase 7 — 22/11/2026)

| Escenario | Criterios | Resultado | Evidencia |
|---|---|---|---|
| Escenario 1 | `SC-001`, `SC-009` | PASA | `themeContractsContrast.test.ts` en verde (148 pares WCAG 2.2 AA, `DataVizThemeTokens`, `0` usos de `Inter`). |
| Escenario 2 | `SC-002` | PASA | `routeThemeScope.test.tsx` en verde (100% rutas `AppRoutes.tsx`, `/` autenticado, deep-links `/login`, transiciones sin residuos). |
| Escenario 3 | `SC-006` | PASA | `brandLayerIsolation.test.tsx` en verde (aislamiento estricto `--ee-*` vs `--brand-*`). |
| Escenario 4 | `SC-003`, `SC-004` | PASA | `WorkspaceLayout.test.tsx`, `RoleLayoutShell.test.tsx`, `RoleHamburgerMenu.test.tsx`, `AdminNotificationBell.test.tsx` y `LogoutFlow.test.tsx` en verde (34/34 tests). |
| Escenario 5 | `SC-004`, `SC-005` | PASA | `tests/components/primitives/` y `PrimitivesMatrix.test.tsx` en verde (20 combinaciones `5 × 4`, polimorfismo puro sin referencias a roles). |
| Escenario 6 | `SC-007`, `SC-009` | PASA | `npm run lint` (`tsc --noEmit`: 0), `npm test` (194/194 suites, 1209/1209 tests en verde), `npm run build` (`0` referencias a `fonts.googleapis.com`/`fonts.gstatic.com`/`Inter`, archivos `.woff2` locales verificados en `dist/assets/`), `git diff` vacío en `easyeat/backend` y `easyeat/frontend/src/api`. |
| Escenario 7 | `SC-008` | PARCIAL (aprobado) | Re-auditoría dual-agent `/impeccable critique` superó la línea base `17/40` → **`30/40` (Good)** con **`0` P0 y `0` P1** (`impeccable detect --json`: `0` hallazgos en 10 archivos). La inspección interactiva en Chrome (`360×800`, `1280×800` y pestaña Network → Font) queda pendiente de verificación manual local porque la estación no tenía habilitado `DevToolsActivePort` (`chrome://inspect/#remote-debugging`). |
