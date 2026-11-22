# Plan de implementación: Arquitectura de temas visuales por rol y primitivos base accesibles

**Branch**: `feat/024-ui-theme-architecture` | **Date**: 21/11/2026 | **Spec**: [specs/024-ui-theme-architecture/spec.md](spec.md)

**Input**: Especificación funcional de `specs/024-ui-theme-architecture/spec.md`, constitución vigente `.specify/memory/constitution.md` (v2.1.0), contratos visuales `.specify/memory/designs/*.DESIGN.md` y verdad de producto capturada en `PRODUCT.md`.

---

## Summary

La especificación `024` establece la arquitectura visual fundacional multi-tema de EasyEat como entrega exclusiva de frontend (`single-tier frontend-only`, autorizada expresamente bajo Principio X), sin modificar backend, servicios Axios ni lógica de negocio de las especificaciones `001`–`023`.

El plan cubre cinco ejes técnicos trazables:

1. **Remediación constitucional de los cuatro `.DESIGN.md`**: erradicación de `Inter` en Comensal (`DM Sans`), adopción de `Archivo` para cuerpo operativo en Mostrador conservando `Archivo Black` en display, corrección de contraste de Mostrador a `#F8FAFC` sobre `#111111`, y pares/tríadas WCAG 2.2 AA verificados matemáticamente para los cuatro temas (`Bento`, `Bold`, `Brutalism`, `Glassmorphism`), cerrando `TODO(DESIGN_CONTRACTS_REMEDIATION)` en la constitución.
2. **Sistema tipográfico autoalojado en Vite**: empaquetado local vía `@fontsource/*` con subconjunto estricto de pesos por rol, `font-display: swap`, descarga selectiva por tema activo, cero peticiones a CDN externos y aprovechamiento de la política `Cache-Control: immutable` de `easyeat/frontend/nginx.conf`.
3. **Ámbito determinista de tema por ruta y aislamiento por capas**: registro único de tokens (`themeRegistry.ts`) proyectado a variables CSS `--ee-*` bajo `[data-theme="<role>"]` mediante `ThemeScopeProvider` en `App.tsx`, cumpliendo la restricción constitucional de vistas compartidas y redirecciones por rol (`/` autenticado y deep-links de staff en `/login` heredan el tema del rol destino; `/first-login-password-change` hereda el rol autenticado con respaldo `'admin'`; resto público/comensal usa Comensal), completamente aislado de las variables `--brand-*` de `BrandThemeProvider` (Spec 016).
4. **Contenedores de navegación minimalistas (`RoleLayoutShell`)**: barra superior minimalista con menú desplegable hamburguesa accesible por teclado en móvil y escritorio para los cuatro roles (incluyendo las 7 secciones completas del Administrador y las 4 del Comensal), conservando acceso directo en 1 toque en cabecera escritorio para las 3 secciones del Chef (Spec 005 C-08), más contenedores transitorios `data-legacy-surface` para preservar legibilidad de `CounterWorkspacePage` y `AdminAnalyticsPage` hasta Specs `026` y `028`.
5. **Cinco primitivos base polimórficos y sub-contrato de visualización de datos**: `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton` y `EmptyState` gobernados exclusivamente por tokens `--ee-*` sin condicionales por nombre de rol, junto con `DataVizThemeTokens` expuesto declarativa y programáticamente desde la misma fuente única de verdad.

---

## Technical Context

**Language/Version**: TypeScript 5.6.3, React 18.3.1, CSS3 (Tailwind CSS 3.4.13 con PostCSS/Autoprefixer).  
**Primary Dependencies**: `react-router-dom` 6.27.0, `lucide-react` 0.453.0, `recharts` 3.11.0 (sin migración de gráficos en `024`), `axios` 1.7.7 (intacto, solo lectura de contratos existentes), y nuevos paquetes de fuentes autoalojadas `@fontsource/dm-sans`, `@fontsource/archivo-black`, `@fontsource/archivo`, `@fontsource/darker-grotesque`, `@fontsource/plus-jakarta-sans` y `@fontsource/jetbrains-mono`.  
**Storage**: N/A (capa exclusivamente frontend; se preserva el uso existente de `sessionStorage` en `AuthContext` y `TenantContext` sin cambios de esquema).  
**Testing**: Vitest 2.1.3, React Testing Library 16.0.1, `@testing-library/user-event` 14.5.2, `@testing-library/jest-dom` 6.5.0, JSDOM 25.0.1, validación estática `tsc --noEmit`, auditoría visual con Impeccable (`/critique`) y verificación en navegador real en viewports `360px` y `1280px`.  
**Target Platform**: Navegadores web modernos en dispositivos móviles (`>= 360px`), tabletas y escritorio (`>= 1280px`), servido en producción como SPA estática sobre Nginx (`easyeat/frontend/nginx.conf`).  
**Project Type**: Aplicación web full-stack (`easyeat/backend` + `easyeat/frontend`) con incremento autorizado estrictamente en `easyeat/frontend` y `.specify/memory/designs/`.  
**Performance Goals**: Cero FOIT mediante `font-display: swap`; carga selectiva bajo demanda de `.woff2` por tema activo; cero FOUC en transiciones entre roles mediante aplicación síncrona de `data-theme`; cero peticiones externas a CDN de fuentes.  
**Constraints**: WCAG 2.2 AA estricto (`>= 4.5:1` texto normal, `>= 3.0:1` texto grande/componentes/foco), objetivos táctiles `>= 44×44 px` en acciones principales (`>= 24×24 px` en compactos), soporte `prefers-reduced-motion`, política Zero AI-Slop, cero cambios en backend/Axios/reglas de negocio de `001`–`023`, cero rutas artificiales de demostración.  
**Scale/Scope**: 4 contratos visuales de rol remediados, 1 resolvedor determinista para 100% de las rutas de `AppRoutes.tsx`, 1 arquitectura unificada de `RoleLayoutShell` compatible con `WorkspaceLayout`, 5 primitivos base verificados en 20 combinaciones (`5 × 4`), 1 sub-contrato `DataVizThemeTokens` para los 4 temas y compatibilidad transitoria con ~25 páginas heredadas.

---

## Constitution Check

*GATE: Evaluado antes de Phase 0 y reevaluado tras completar Phase 1.*

### Evaluación Pre-Diseño (antes de Phase 0)

| Principio constitucional | Estado | Evidencia / Justificación |
|---|---|---|
| **I. Specification as Source of Truth** | PASA | Toda decisión responde directamente a `FR-001`–`FR-019` y `SC-001`–`SC-009` de `specs/024-ui-theme-architecture/spec.md`. |
| **II. Specification Before Implementation** | PASA | Spec `024` aprobada con aclaraciones de Grill-Me; no se escribe código fuente ni `tasks.md` en esta fase. |
| **III. Layered Verification Proportional to Risk** | PASA | Se planifican pruebas unitarias (contraste, resolver), integración (aislamiento de tema y Spec 016, navegación, 20 combinaciones de primitivos), regresión completa (`001`–`023`) y auditoría visual Impeccable. |
| **IV. Simple, Cohesive, and Justified Design** | PASA | Un único registro de tokens (`themeRegistry.ts`) alimenta CSS `--ee-*` y `DataVizThemeTokens`; polimorfismo sin condicionales por rol. |
| **V. Bounded Scope** | PASA | Exclusiones estrictas aplicadas: sin cambios en backend, Axios, pantallas internas de `025`–`028`, gráficos Recharts ni rutas artificiales de demo. |
| **VI. Controlled Technical Debt** | PASA | Cierra `TODO(DESIGN_CONTRACTS_REMEDIATION)` y acota la transición de pantallas heredadas mediante `data-legacy-surface` documentado para retiro en `026` y `028`. |
| **VII. Traceability, Consistency, Documentation** | PASA | Cadena completa `spec.md` → `research.md` → `data-model.md` → `contracts/*` → `quickstart.md` → `plan.md`, además de `PRODUCT.md` vía `/impeccable init`. |
| **VIII. Evidence Over Assumption & Escalation** | PASA | Se inspeccionaron `AppRoutes.tsx`, `WorkspaceLayout.tsx`, `BrandThemeProvider.tsx`, `AdminAnalyticsPage.tsx`, `CounterWorkspacePage.tsx` y `WorkspaceLayout.test.tsx`; toda tensión arquitectónica se escaló por `grill-me` antes de diseñar. |
| **IX. Engineering Over Convenience** | PASA | Todos los ratios WCAG 2.2 AA fueron verificados matemáticamente (148 comprobaciones, 0 fallas) antes de fijar los tokens en `data-model.md`. |
| **X. Full-Stack Integrity (Vertical Slices)** | PASA (Excepción autorizada) | Excepción `single-tier frontend-only` autorizada expresamente por el responsable del proyecto el 21/11/2026 y documentada en `spec.md`. Asimismo, la entrega en rama única `feat/024-ui-theme-architecture` con commits atómicos por corte vertical/historia cuenta con excepción documentada en `spec.md`. |
| **XI. Role-Scoped Visual Governance & Non-Regressive UI** | PASA | Remediación WCAG 2.2 AA y Zero AI-Slop precede la implementación; Axios y lógica de negocio permanecen intactos; actualización mínima de aserciones de layout obsoletas en `WorkspaceLayout.test.tsx` expresamente autorizada preservando el 100% de las aserciones conductuales y `LogoutFlow.test.tsx`. |

### Reevaluación Post-Diseño (tras Phase 1)

- **Consistencia de contratos**: `data-model.md` y los tres contratos en `contracts/` mantienen fuente única de verdad sin duplicación de paletas ni dependencias circulares.
- **Compatibilidad de tests heredados**: envolver `AdminAnalyticsPage` en el shell administrativo desde `AppRoutes.tsx` garantiza que `AdminAnalyticsPage.test.tsx` (que monta el componente sin `AuthProvider`) continúe pasando intacto; conservar el botón `Cerrar sesión` en cabecera garantiza que `LogoutFlow.test.tsx` continúe pasando intacto.
- **Puertas de calidad**: no quedan `NEEDS CLARIFICATION`, no hay violaciones sin justificar y los hooks configurados en `.specify/extensions.yml` fueron verificados (sin hooks registrados para `before_plan` ni `after_plan`).

---

## Project Structure

### Documentation (this feature)

```text
specs/024-ui-theme-architecture/
├── plan.md                                 # Este archivo (plan arquitectónico /speckit-plan)
├── research.md                             # Phase 0: decisiones técnicas verificadas
├── data-model.md                           # Phase 1: entidades, matriz de tokens WCAG 2.2 AA y máquinas de estado
├── quickstart.md                           # Phase 1: escenarios ejecutables SC-001 a SC-009
├── contracts/
│   ├── theme-tokens.contract.ts            # Contrato de tokens, resolver de ruta y DataVizThemeTokens
│   ├── navigation-shells.contract.ts       # Contrato de RoleLayoutShell y menú hamburguesa
│   └── ui-primitives.contract.ts           # Contrato polimórfico WCAG 2.2 AA de los 5 primitivos
└── tasks.md                                # Phase 2 (NO creado en /speckit-plan; generado en /speckit-tasks)
```

### Source Code (repository root)

```text
PRODUCT.md                                  # Verdad durable de producto (/impeccable init)
.impeccable/
└── config.json                             # Flujo por defecto code-first ("buildPath": "code")

.specify/memory/
├── constitution.md                         # Cierre del TODO(DESIGN_CONTRACTS_REMEDIATION) en implementación
└── designs/
    ├── comensal.DESIGN.md                  # Remediación Bento (DM Sans, pares WCAG 2.2 AA, DataViz)
    ├── mostrador.DESIGN.md                 # Remediación Bold (#F8FAFC sobre #111111, Archivo + Archivo Black, DataViz)
    ├── chef.DESIGN.md                      # Remediación Brutalism (Darker Grotesque 500–800, pares WCAG, DataViz)
    └── admin.DESIGN.md                     # Remediación Glassmorphism (Plus Jakarta Sans, tríadas WCAG, DataViz)

easyeat/frontend/
├── package.json                            # Incorporación exclusiva de paquetes @fontsource/* requeridos
├── tailwind.config.js                      # Extensión de utilidades semánticas ee-* conservando primary/brand heredados
├── src/
│   ├── main.tsx                            # Imports estrictos por peso de @fontsource/*
│   ├── index.css                           # Carga de themes.css y reglas base prefers-reduced-motion / legacy-surface
│   ├── App.tsx                             # Integración de ThemeScopeProvider sobre AppRoutes
│   ├── styles/
│   │   └── themes.css                      # Variables CSS --ee-* aisladas por [data-theme="comensal|mostrador|chef|admin"]
│   ├── theme/
│   │   ├── types.ts                        # Tipos ejecutables del contrato de temas y DataVizThemeTokens
│   │   ├── themeRegistry.ts                # Fuente única de verdad de los 4 RoleVisualThemeContract y DataVizThemeTokens
│   │   ├── routeThemeResolver.ts           # Función pura resolveThemeForRoute (FR-004, constitución L157)
│   │   ├── ThemeScopeProvider.tsx          # Contexto React + sincronización síncrona de data-theme
│   │   └── useRoleTheme.ts                 # Hooks useRoleTheme() y useDataVizTokens() (FR-019)
│   ├── router/
│   │   └── AppRoutes.tsx                   # Envoltorio de ruta administrativa para AdminAnalyticsPage sin tocar su test aislado
│   ├── components/
│   │   ├── common/
│   │   │   ├── WorkspaceLayout.tsx         # Fachada compatible que delega en RoleLayoutShell
│   │   │   └── BrandThemeProvider.tsx      # Intacto en contrato (--brand-*), verificado en aislamiento
│   │   ├── notification/
│   │   │   └── AdminNotificationBell.tsx   # Endurecimiento WCAG 2.2 AA en cabecera de Administrador (FR-007, FR-010)
│   │   ├── layout/
│   │   │   ├── roleNavigationConfig.ts     # Mapa de rutas por rol (4 Comensal, 1 Mostrador, 3 Chef C-08, 7 Admin; sin /perfil en staff)
│   │   │   ├── RoleHamburgerMenu.tsx       # Menú desplegable accesible (teclado, Escape, clic exterior, foco)
│   │   │   └── RoleLayoutShell.tsx         # Contenedor minimalista responsivo gobernado por --ee-*
│   │   └── primitives/
│   │       ├── Button.tsx                  # FR-011 (primary, secondary, ghost, outline, danger; loading anti-doble envío)
│   │       ├── StatusBadge.tsx             # FR-012 (neutral, info, success, warning, danger + ícono/prefijo)
│   │       ├── FeedbackState.tsx           # FR-013 (info, success, warning, danger; aria-live diferenciado)
│   │       ├── LoadingSkeleton.tsx         # FR-014 (text, block, circle; aria-hidden; reduced-motion)
│   │       ├── EmptyState.tsx              # FR-015 (ícono, título, descripción, acción principal)
│   │       └── index.ts                    # Barrel export de los 5 primitivos
│   └── pages/
│       ├── CounterWorkspacePage.tsx        # Único ajuste permitido: prop legacySurfaceMode="legacy-light" en WorkspaceLayout
│       └── AdminAnalyticsPage.tsx          # Sin cambios funcionales; protegido vía wrapper de ruta con legacy-dark
└── tests/
    ├── theme/
    │   ├── themeContractsContrast.test.ts  # Verificación WCAG 2.2 AA (148 pares + DataViz + 0 Inter)
    │   ├── routeThemeScope.test.tsx        # Cobertura 100% rutas AppRoutes + / autenticado + deep-links /login + transiciones sin residuos
    │   └── brandLayerIsolation.test.tsx    # Aislamiento --ee-* vs --brand-* (Spec 016)
    ├── components/
    │   ├── WorkspaceLayout.test.tsx        # Aserciones de layout inline actualizadas (autorizado en spec.md); comportamiento intacto
    │   ├── RoleHamburgerMenu.test.tsx      # Menú desplegable accesible, Escape, retorno de foco y mapas por rol
    │   ├── AdminNotificationBell.test.tsx  # Accesibilidad WCAG 2.2 AA de la campana administrativa
    │   ├── RoleLayoutShell.test.tsx        # Contenedor minimalista, skip link, 7 rutas Admin, 4 Comensal, C-08 Chef, sin /perfil en staff
    │   └── primitives/
    │       ├── Button.test.tsx             # Unitarias Button (5 variantes, targets táctiles, loading)
    │       ├── StatusBadge.test.tsx        # Unitarias StatusBadge
    │       ├── FeedbackState.test.tsx      # Unitarias FeedbackState
    │       ├── LoadingSkeleton.test.tsx    # Unitarias LoadingSkeleton
    │       ├── EmptyState.test.tsx         # Unitarias EmptyState
    │       └── PrimitivesMatrix.test.tsx   # Matriz 20 combinaciones (5 primitivos × 4 temas) + polimorfismo puro
```

**Structure Decision**: Se preserva íntegramente la arquitectura existente de `easyeat/frontend/src/` (`api/`, `components/`, `context/`, `hooks/`, `pages/`, `router/`, `types/`, `utils/`) agregando únicamente tres módulos cohesivos de infraestructura visual (`src/styles/`, `src/theme/`, `src/components/layout/` y `src/components/primitives/`) sin tocar `easyeat/backend/` ni `easyeat/frontend/src/api/`.

---

## Architecture & Implementation Blueprint

### Módulo A — Remediación de contratos `.DESIGN.md` y fuentes autoalojadas (`FR-001`, `FR-002`, `FR-003`)

1. Actualizar los cuatro archivos en `.specify/memory/designs/` con los tokens exactos y ratios verificados de `data-model.md` (incluyendo frontmatter ampliado con `on-primary`, `on-secondary`, tríadas semánticas, subconjunto estricto de pesos y sub-contrato `dataViz`), eliminando toda referencia a `Inter` y cerrando el `TODO(DESIGN_CONTRACTS_REMEDIATION)` en `.specify/memory/constitution.md`.
2. Instalar los seis paquetes `@fontsource/*` en `easyeat/frontend/package.json` e importar únicamente los CSS de los pesos requeridos en `easyeat/frontend/src/main.tsx` (20 archivos de peso en total).
3. Verificar que `easyeat/frontend/index.html` no incluya `<link>` a fuentes externas ni clases rígidas `bg-gray-50 text-gray-900` que impidan la herencia limpia de `--ee-surface` y `--ee-text`.

### Módulo B — Registro único de temas, aislamiento CSS y enrutamiento determinista (`FR-004`, `FR-005`, `FR-006`, `FR-019`)

1. Implementar `easyeat/frontend/src/theme/themeRegistry.ts` conforme a `contracts/theme-tokens.contract.ts`, exportando `ROLE_THEME_REGISTRY`, `getThemeContract()` y `DataVizThemeTokens` sin duplicar valores cromáticos.
2. Declarar en `easyeat/frontend/src/styles/themes.css` los cuatro bloques `[data-theme="comensal"]`, `[data-theme="mostrador"]`, `[data-theme="chef"]` y `[data-theme="admin"]` usando exclusivamente variables `--ee-*`, las reglas globales de `:focus-visible`, `@media (prefers-reduced-motion: reduce)` y los contenedores transitorios `[data-legacy-surface="light"]` / `[data-legacy-surface="dark"]`.
3. Extender `easyeat/frontend/tailwind.config.js` con el mapa de utilidades `ee` conectado a `var(--ee-*)` sin modificar `colors.primary` ni `colors.brand`.
4. Implementar `resolveThemeForRoute()` y `ThemeScopeProvider` dentro de `BrowserRouter` y `AuthProvider` en `App.tsx`, aplicando `data-theme` mediante `useLayoutEffect` (para eliminar FOUC entre transiciones) en `document.documentElement` y en el nodo raíz `data-testid="theme-scope-root"`.
5. Exponer `useRoleTheme()` y `useDataVizTokens()` para consumo declarativo y programático de los tokens del tema activo (listo para Recharts en Spec `028`).

### Módulo C — Contenedores de navegación minimalistas por rol y compatibilidad transitoria (`FR-007`–`FR-010`, `FR-018`)

1. Definir `roleNavigationConfig.ts` con el mapa completo de rutas y estrategias de coincidencia activa (`exact`, `prefix`, `hash`) para los cuatro roles, excluyendo `/perfil` de los roles de personal (`ENCARGADO`, `CHEF`, `ADMINISTRADOR`).
2. Implementar `RoleHamburgerMenu.tsx` cumpliendo todas las reglas de `contracts/navigation-shells.contract.ts` (teclado, `Escape`, retorno de foco al disparador, clic exterior, cierre al navegar, `aria-expanded`, `aria-controls`, `aria-current="page"`, objetivos `>= 44×44 px`).
3. Endurecer `AdminNotificationBell.tsx` con tokens `--ee-*`, `aria-expanded`, `aria-haspopup`, cierre por `Escape` con retorno de foco, objetivo táctil `>= 44×44 px`, texto `sr-only` para no leídas y contraste `>= 4.5:1` en fechas (`FR-007`, `FR-010`).
4. Implementar `RoleLayoutShell.tsx` estilizado exclusivamente mediante `--ee-*` (con efecto `backdrop-filter: blur(12px)` en Administrador, bordes marcados y sombra dura en Chef, alto contraste oscuro en Mostrador y bloques cálidos suaves en Comensal), conservando el `<nav aria-label="Secciones de cocina">` directo en escritorio (`md:flex`) para Chef (C-08), avatar navegable a `/perfil` únicamente para `COMENSAL`, y el menú hamburguesa para todos los roles.
5. Adaptar `WorkspaceLayout.tsx` como fachada hacia `RoleLayoutShell.tsx`, manteniendo `AdminNotificationBell`, `tenant-badge`, avatar y botón `Cerrar sesión` (y `Iniciar sesión` / `Registrarse` para invitados).
6. Proteger las dos pantallas heredadas con riesgo de contraste sin refactorizarlas:
   - En `CounterWorkspacePage.tsx`: pasar `legacySurfaceMode="legacy-light"` a `WorkspaceLayout`.
   - En `AppRoutes.tsx`: envolver `/admin/analytics` en un wrapper `WorkspaceLayout` con `title="Métricas y Rendimiento"` y `legacySurfaceMode="legacy-dark"` para mantener `AdminAnalyticsPage.tsx` y `AdminAnalyticsPage.test.tsx` intactos.

### Módulo D — Catálogo de cinco primitivos base polimórficos (`FR-011`–`FR-017`)

1. Implementar `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton` y `EmptyState` en `easyeat/frontend/src/components/primitives/` siguiendo estrictamente `contracts/ui-primitives.contract.ts`.
2. Verificar por diseño y por prueba automatizada que ninguno de los cinco primitivos contiene lógica condicional acoplada a nombres de rol o tema.

---

## Control estricto de exclusiones

- **Prohibido** modificar cualquier archivo bajo `easyeat/backend/` o `easyeat/frontend/src/api/`.
- **Prohibido** alterar reglas de negocio, estado de hooks de dominio o contratos de datos heredados de `001`–`023`.
- **Prohibido** refactorizar el contenido interno de las pantallas específicas de rol (reservado para Specs `025` Comensal, `026` Mostrador, `027` Chef y `028` Administrador) o migrar `FinancialHealthChart.tsx` a `DataVizThemeTokens` (reservado para Spec `028`).
- **Prohibido** crear rutas artificiales de demostración, catálogo visual o previsualización en `AppRoutes.tsx`.

---

## Complexity Tracking

| Elemento | Por qué es necesario | Alternativa más simple rechazada |
|---|---|---|
| Contenedor transitorio `data-legacy-surface` (`light`/`dark`) | Garantiza contraste `>= 4.5:1` inmediato en `CounterWorkspacePage` (`text-gray-900` sobre `#111111`) y `AdminAnalyticsPage` (`text-neutral-100` sobre `#F4F7FC`) sin invadir el alcance de Specs `026` y `028`. | Dejar el `<main>` sin tema de rol (incumpliría `FR-004`/`FR-005`) o refactorizar esas pantallas dentro de `024` (violaría exclusiones de `026`/`028`). |
| Wrapper de shell en `AppRoutes.tsx` para `/admin/analytics` | Provee la barra administrativa con las 7 secciones en `/admin/analytics` (`FR-007`/`FR-008`) sin romper `AdminAnalyticsPage.test.tsx`, que renderiza la página sin `AuthProvider`. | Meter `WorkspaceLayout` dentro de `AdminAnalyticsPage.tsx` (rompería el test heredado o obligaría a alterar `AdminAnalyticsPage.test.tsx`). |
