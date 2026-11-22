# Tasks: Arquitectura de temas visuales por rol y primitivos base accesibles

**Input**: Documentos de diseño en `specs/024-ui-theme-architecture/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`) y auditoría base `.impeccable/critique/2026-11-21T21-37-04Z__src-components-common-workspacelayout-tsx-f8bdeeeb.md`.

**Prerequisites**: `plan.md` (aprobado), `spec.md` (US1–US4), `research.md`, `data-model.md`, `contracts/theme-tokens.contract.ts`, `contracts/navigation-shells.contract.ts`, `contracts/ui-primitives.contract.ts`.

**Tests (TDD obligatorio)**: Todas las capacidades frontend siguen ciclos verticales **Red (prueba que falla primero) → Green (implementación mínima que la hace pasar)** sobre los cinco seams públicos confirmados. **El backend (`easyeat/backend/`) y los servicios Axios (`easyeat/frontend/src/api/`) NO se modifican** (excepción `single-tier frontend-only` autorizada bajo Principio X).

**Organization**: Tareas atómicas ordenadas por dependencias y agrupadas por User Story (`US1` a `US4`) para permitir implementación, verificación y entrega incremental independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Ejecutable en paralelo (archivos distintos, sin dependencia sobre tareas incompletas).
- **[Story]**: User Story a la que pertenece la tarea (`[US1]`, `[US2]`, `[US3]`, `[US4]`). Las fases de Setup, Foundational y Polish no llevan etiqueta de historia.
- Todas las tareas incluyen rutas exactas de archivo dentro del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)
> **GitHub Issue**: [#448](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/448)

**Purpose**: Instalar activos tipográficos autoalojados, limpiar estilos base de entrada y extender Tailwind CSS 3.4 con utilidades semánticas sin alterar tokens heredados de `001`–`023`.

- [X] T001 Instalar paquetes de fuentes autoalojadas `@fontsource/dm-sans`, `@fontsource/archivo-black`, `@fontsource/archivo`, `@fontsource/darker-grotesque`, `@fontsource/plus-jakarta-sans` y `@fontsource/jetbrains-mono` en `easyeat/frontend/package.json`
- [X] T002 Importar estrictamente los 20 archivos CSS de pesos requeridos (`DM Sans` 400/500/600/700; `Archivo Black` 400; `Archivo` 400/500/600/700; `Darker Grotesque` 500/600/700/800; `Plus Jakarta Sans` 400/500/600/700; `JetBrains Mono` 400/500/600) en `easyeat/frontend/src/main.tsx`
- [X] T003 [P] Actualizar `<title>` a español (`EasyEat`) y retirar clases fijas `bg-gray-50 text-gray-900` del `<body>` para permitir herencia limpia de `--ee-surface` y `--ee-text` en `easyeat/frontend/index.html`
- [X] T004 [P] Extender `easyeat/frontend/tailwind.config.js` con utilidades semánticas `ee-*` (colores, fuentes `ee-display`/`ee-sans`/`ee-mono`, radios `ee-sm`/`ee-md`/`ee-lg` y sombras) preservando intactos `colors.primary` y `colors.brand` heredados

---

## Phase 2: Foundational (Blocking Prerequisites)
> **GitHub Issue**: [#449](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/449)

**Purpose**: Infraestructura bloqueante de tipos y hoja CSS multi-tema que todas las historias de usuario consumen.

**⚠️ CRITICAL**: Ninguna User Story puede comenzar hasta completar Phase 2.

- [X] T005 [P] Crear tipos ejecutables del contrato de temas (`RoleThemeId`, `EasyEatUserRole`, `SemanticVariant`, `SemanticTriad`, `DataVizThemeTokens`, `RoleVisualThemeContract`, `ResolvedThemeScope`, `RoleThemeContextValue`) en `easyeat/frontend/src/theme/types.ts` conforme a `specs/024-ui-theme-architecture/contracts/theme-tokens.contract.ts`
- [X] T006 [P] Crear hoja de variables CSS aisladas `--ee-*` para los cuatro ámbitos `[data-theme="comensal"]`, `[data-theme="mostrador"]`, `[data-theme="chef"]` y `[data-theme="admin"]`, reglas `:focus-visible`, `@media (prefers-reduced-motion: reduce)` y contenedores transitorios `[data-legacy-surface="light"]` / `[data-legacy-surface="dark"]` en `easyeat/frontend/src/styles/themes.css`
- [X] T007 Importar `./styles/themes.css` y vincular `html, body` a `var(--ee-surface)`, `var(--ee-text)` y `var(--ee-font-sans)` en `easyeat/frontend/src/index.css` (depende de T006)

**Checkpoint**: Base de fuentes autoalojadas, Tailwind `ee-*`, tipos y variables `--ee-*` lista; las User Stories ya pueden avanzar.

---

## Phase 3: User Story 1 — Identidad visual aislada por rol y contratos remediados (Priority: P1) 🎯 MVP
> **GitHub Issue**: [#450](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/450)

**Goal**: Remediar constitucionalmente los cuatro contratos `.DESIGN.md`, exponer la fuente única de verdad de tokens + `DataVizThemeTokens` y aplicar de forma determinista el tema por ruta sin colisiones ni residuos entre transiciones.

**Independent Test**: Navegar secuencialmente por rutas públicas, de Comensal, Mostrador, Chef, Administrador y `/first-login-password-change`, comprobando en cada paso los tokens activos, contraste WCAG 2.2 AA (`148` pares), sub-contrato `DataVizThemeTokens` y `0` residuos al cambiar de rol.

### Slice TDD 1A — Seam de contratos `.DESIGN.md`, `ROLE_THEME_REGISTRY`, WCAG 2.2 AA y `DataVizThemeTokens` (`FR-001`, `FR-002`, `FR-003`, `FR-019`)

> **RED FIRST**: Escribir la suite de contrato y confirmar que FALLA antes de remediar `.DESIGN.md` e implementar el registro.

- [X] T008 [US1] [TDD-RED] Escribir suite de pruebas de contrato en `easyeat/frontend/tests/theme/themeContractsContrast.test.ts` verificando `0` usos de `Inter` en los cuatro `.DESIGN.md`, contraste WCAG 2.2 AA mediante `getContrastRatio` en los 148 pares de los 4 temas (texto, `onPrimary`, `onSecondary`, `onDanger`, bordes, foco, tríadas semánticas) y derivación desde fuente única de `DataVizThemeTokens` (5 series, ejes, etiquetas y tooltip); ejecutar `npx vitest run tests/theme/themeContractsContrast.test.ts` y verificar fallo inicial
- [X] T009 [P] [US1] [TDD-GREEN] Remediar contrato Bento (`DM Sans` 400–700, `JetBrains Mono` 400–600, `0` `Inter`, pares WCAG 2.2 AA, tríadas y `dataViz`) en `.specify/memory/designs/comensal.DESIGN.md`
- [X] T010 [P] [US1] [TDD-GREEN] Remediar contrato Bold (`#F8FAFC` sobre `#111111`, `Archivo Black` 400 + `Archivo` 400–700, `on-secondary: #0A0A0A`, tríadas y `dataViz`) en `.specify/memory/designs/mostrador.DESIGN.md`
- [X] T011 [P] [US1] [TDD-GREEN] Remediar contrato Brutalism (`Darker Grotesque` 500–800, `on-primary: #111827`, `primary-hover: #E67966`, bordes `#111827`, tríadas y `dataViz`) en `.specify/memory/designs/chef.DESIGN.md`
- [X] T012 [P] [US1] [TDD-GREEN] Remediar contrato Glassmorphism (`Plus Jakarta Sans` 400–700, lienzo `#F4F7FC` + capas glass sin degradados púrpura-azul, tríadas WCAG 2.2 AA y `dataViz`) en `.specify/memory/designs/admin.DESIGN.md`
- [X] T013 [US1] [TDD-GREEN] Implementar fuente única de verdad `ROLE_THEME_REGISTRY`, `getThemeContract` y proyección `DataVizThemeTokens` en `easyeat/frontend/src/theme/themeRegistry.ts` (depende de T005)
- [X] T014 [US1] [TDD-GREEN] Cerrar el registro pendiente `TODO(DESIGN_CONTRACTS_REMEDIATION)` en `.specify/memory/constitution.md` (depende de T009, T010, T011, T012) y ejecutar `npx vitest run tests/theme/themeContractsContrast.test.ts` confirmando verde

### Slice TDD 1B — Seam de resolución determinista por ruta y `ThemeScopeProvider` (`FR-004`, `FR-005`, `FR-019`)

> **RED FIRST**: Escribir las pruebas de enrutamiento y ciclo de vida DOM antes de implementar el resolver y el proveedor.

- [X] T015 [US1] [TDD-RED] Escribir pruebas de resolución de ruta y aislamiento DOM en `easyeat/frontend/tests/theme/routeThemeScope.test.tsx` cubriendo 100% de rutas y alias de `AppRoutes.tsx`, `/` autenticado (hereda tema del rol destino) y sin sesión (`comensal`), `/login` con deep-links de personal vía `location.state.from` (`/mostrador*` → `mostrador`, `/cocina*` → `chef`, `/admin*` → `admin`) y sin deep-link (`comensal`), `/first-login-password-change` (rol autenticado y fallback `'admin'`), exposición de `useRoleTheme()`/`useDataVizTokens()` y transiciones `mostrador -> login -> chef -> admin -> comensal` sin residuos en `data-theme`; ejecutar y confirmar fallo inicial
- [X] T016 [US1] [TDD-GREEN] Implementar función pura `resolveThemeForRoute` (soportando `pathname`, `userRole` y `fromPath` conforme a `FR-004` y constitución L157) en `easyeat/frontend/src/theme/routeThemeResolver.ts` (depende de T005)
- [X] T017 [US1] [TDD-GREEN] Implementar `ThemeScopeProvider` (sincronización síncrona vía `useLayoutEffect` sobre `document.documentElement` y contenedor `data-testid="theme-scope-root"`) en `easyeat/frontend/src/theme/ThemeScopeProvider.tsx` y hooks `useRoleTheme` / `useDataVizTokens` en `easyeat/frontend/src/theme/useRoleTheme.ts` (depende de T013, T016)
- [X] T018 [US1] [TDD-GREEN] Integrar `ThemeScopeProvider` dentro de `BrowserRouter` y `AuthProvider` envolviendo `AppRoutes` en `easyeat/frontend/src/App.tsx` (depende de T017) y ejecutar `npx vitest run tests/theme/routeThemeScope.test.tsx` confirmando verde

**Checkpoint**: US1 completa y verificable de forma independiente (MVP arquitectónico funcional).

---

## Phase 4: User Story 2 — Contenedores de navegación minimalistas y responsivos por rol (Priority: P1)
> **GitHub Issue**: [#451](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/451)

**Goal**: Entregar contenedores de navegación minimalistas por rol con menú desplegable hamburguesa accesible por teclado, las 7 secciones completas de Administrador, acceso directo C-08 en escritorio para Chef, endurecimiento WCAG 2.2 AA de `AdminNotificationBell` y aislamiento transitorio para `CounterWorkspacePage` y `AdminAnalyticsPage`.

**Independent Test**: Montar los contenedores para los 4 roles e invitado verificando operabilidad por teclado del menú hamburguesa (`Escape`, clic exterior, retorno de foco, `aria-expanded`, `aria-current="page"`), mapa completo de enlaces, targets `>= 44×44 px`, skip link, endurecimiento de `AdminNotificationBell`, y cero regresiones en `LogoutFlow.test.tsx` y `AdminAnalyticsPage.test.tsx`.

### Slice TDD 2A — Seam de mapa de navegación y `RoleHamburgerMenu` (`FR-008`, `FR-010`)

- [X] T019 [US2] [TDD-RED] Escribir pruebas de `RoleHamburgerMenu` en `easyeat/frontend/tests/components/RoleHamburgerMenu.test.tsx` verificando botón disparador (`>= 44×44 px`, `aria-expanded`, `aria-controls`), apertura/cierre por clic, cierre al seleccionar enlace, cierre por `Escape` con retorno de foco al disparador, cierre por clic exterior, `aria-current="page"` dinámico, metadatos de cuenta dentro del panel (rol legible y correo), cobertura completa de enlaces por rol (4 Comensal, 1 Mostrador, 3 Chef, 7 Admin) y ausencia total de enlaces a `/perfil` para `ENCARGADO`, `CHEF` y `ADMINISTRADOR`; ejecutar y confirmar fallo inicial
- [X] T020 [P] [US2] [TDD-GREEN] Definir configuración tipada `ROLE_NAVIGATION_CONFIG` y función de coincidencia activa (`exact`, `prefix`, `hash`) en `easyeat/frontend/src/components/layout/roleNavigationConfig.ts` conforme a `specs/024-ui-theme-architecture/contracts/navigation-shells.contract.ts`
- [X] T021 [US2] [TDD-GREEN] Implementar `RoleHamburgerMenu` en `easyeat/frontend/src/components/layout/RoleHamburgerMenu.tsx` (depende de T020) y ejecutar `npx vitest run tests/components/RoleHamburgerMenu.test.tsx` confirmando verde

### Slice TDD 2B — Seam de endurecimiento WCAG 2.2 AA de `AdminNotificationBell` (`FR-007`, `FR-010`)

- [X] T022 [US2] [TDD-RED] Escribir pruebas de accesibilidad en `easyeat/frontend/tests/components/AdminNotificationBell.test.tsx` verificando `aria-expanded`, `aria-haspopup`, cierre por `Escape` con retorno de foco al botón, objetivo táctil `>= 44×44 px`, texto `sr-only` para notificaciones no leídas y contraste WCAG 2.2 AA en marcas de tiempo; ejecutar y confirmar fallo inicial
- [X] T023 [US2] [TDD-GREEN] Endurecer `easyeat/frontend/src/components/notification/AdminNotificationBell.tsx` aplicando tokens `--ee-*`, `aria-expanded`, `aria-haspopup`, manejador `Escape` con retorno de foco, texto accesible para estado no leído y texto de fecha con contraste `>= 4.5:1`; ejecutar `npx vitest run tests/components/AdminNotificationBell.test.tsx` confirmando verde

### Slice TDD 2C — Seam de `RoleLayoutShell`, fachada `WorkspaceLayout` y compatibilidad transitoria (`FR-007`, `FR-009`, `FR-018`)

- [ ] T024 [US2] [TDD-RED] Escribir pruebas integrales en `easyeat/frontend/tests/components/RoleLayoutShell.test.tsx` (skip link `"Saltar al contenido principal"`, barra minimalista por rol sin enums crudos ni correo en cabecera, avatar navegable a `/perfil` únicamente para `COMENSAL` y no navegable para staff, `tenant-badge` en escritorio para staff, botón `Cerrar sesión` directo en cabecera, excepción Chef C-08 con los 3 enlaces directos `>= 44px` en escritorio, modo invitado `Iniciar sesión`/`Registrarse`, y soporte `legacySurfaceMode="legacy-light"|"legacy-dark"`) y actualizar únicamente las aserciones de layout inline obsoletas en `easyeat/frontend/tests/components/WorkspaceLayout.test.tsx` manteniendo intactas todas las aserciones conductuales; ejecutar y confirmar fallo inicial
- [ ] T025 [US2] [TDD-GREEN] Implementar `RoleLayoutShell` gobernado exclusivamente por tokens `--ee-*` (incluyendo skip link, cabecera minimalista, navegación directa escritorio C-08 para Chef, `RoleHamburgerMenu`, `AdminNotificationBell` para Admin, `Cerrar sesión` accesible y contenedor `data-legacy-surface`) en `easyeat/frontend/src/components/layout/RoleLayoutShell.tsx` (depende de T017, T021, T023)
- [ ] T026 [US2] [TDD-GREEN] Refactorizar `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` como fachada compatible hacia `RoleLayoutShell` preservando props `{ title, subtitle?, children, legacySurfaceMode? }`, carga de avatar y notificaciones de Admin (depende de T025)
- [ ] T027 [P] [US2] [TDD-GREEN] Aplicar `legacySurfaceMode="legacy-light"` al `WorkspaceLayout` de `easyeat/frontend/src/pages/CounterWorkspacePage.tsx` sin modificar su contenido ni lógica interna (depende de T026)
- [ ] T028 [P] [US2] [TDD-GREEN] Envolver la ruta `/admin/analytics` en un wrapper `WorkspaceLayout` (`title="Métricas y Rendimiento"`, `legacySurfaceMode="legacy-dark"`) dentro de `easyeat/frontend/src/router/AppRoutes.tsx` sin modificar `AdminAnalyticsPage.tsx` ni `AdminAnalyticsPage.test.tsx` (depende de T026)
- [ ] T029 [US2] [TDD-GREEN] Ejecutar `npx vitest run tests/components/RoleLayoutShell.test.tsx tests/components/WorkspaceLayout.test.tsx tests/session/LogoutFlow.test.tsx tests/pages/AdminAnalyticsPage.test.tsx tests/pages/KitchenWorkspacePage.test.tsx tests/pages/CounterWorkspacePage.test.tsx` y confirmar verde total

**Checkpoint**: US1 y US2 completas, accesibles y compatibles con todas las pantallas heredadas.

---

## Phase 5: User Story 3 — Convivencia aislada con la identidad personalizada del establecimiento (Priority: P2)
> **GitHub Issue**: [#452](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/452)

**Goal**: Garantizar y verificar que la capa de marca personalizada por restaurante (`--brand-*`, Spec 016) coexista de forma estrictamente aislada por debajo del tema Comensal (`--ee-*`) sin colisiones ni filtraciones hacia otros roles o hacia `/admin/mi-local`.

**Independent Test**: Renderizar un establecimiento con marca personalizada, cambiar a uno neutro y navegar a rutas sin establecimiento y de staff (`/admin/mi-local`, `/mostrador`, `/cocina`), confirmando aislamiento total entre `--ee-*` y `--brand-*`.

### Slice TDD 3A — Seam de convivencia de capas `--ee-*` y `--brand-*` (`FR-006`, `SC-006`)

- [ ] T030 [US3] [TDD-RED] Escribir suite de aislamiento por capas en `easyeat/frontend/tests/theme/brandLayerIsolation.test.tsx` verificando: (1) establecimiento con colores personalizados aplica solo `--brand-*` en `brand-theme-container` sin modificar `--ee-*` de `[data-theme="comensal"]`; (2) cambio a establecimiento neutro o ruta fuera del catálogo limpia `--brand-*` preservando la paleta Bento; (3) `BrandThemeProvider` no altera el tema de `/admin/mi-local`, `/mostrador` ni `/cocina`; (4) colores extremos mantienen contraste protegido mediante `colorContrastUtils`; ejecutar y verificar estado inicial
- [ ] T031 [US3] [TDD-GREEN] Auditar y blindar `easyeat/frontend/src/components/common/BrandThemeProvider.tsx` (asegurando atributo `data-brand-layer="establishment"`, ausencia de mutaciones sobre `--ee-*` y limpieza limpia al desmontar o cambiar de `establishmentId` sin modificar contratos de `brandingApi.ts`) y ejecutar `npx vitest run tests/theme/brandLayerIsolation.test.tsx` confirmando verde

**Checkpoint**: US3 verificada con aislamiento probado frente a Spec 016.

---

## Phase 6: User Story 4 — Primitivos base accesibles adaptables a los cuatro temas (Priority: P2)
> **GitHub Issue**: [#453](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/453)

**Goal**: Construir y verificar los cinco primitivos base polimórficos (`Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`) gobernados exclusivamente por tokens `--ee-*`, sin lógica condicional acoplada a nombres de rol y validados en la matriz completa de 20 combinaciones (`5 primitivos × 4 temas`).

**Independent Test**: Montar los cinco primitivos bajo `[data-theme="comensal"]`, `[data-theme="mostrador"]`, `[data-theme="chef"]` y `[data-theme="admin"]` validando variantes, accesibilidad WCAG 2.2 AA, teclado, `prefers-reduced-motion` y ausencia de condicionales por rol.

### 5 pares atómicos TDD en paralelo `[P]` (`FR-011` a `FR-015`)

- [ ] T032 [P] [US4] [TDD-RED] Escribir pruebas unitarias de `Button` en `easyeat/frontend/tests/components/primitives/Button.test.tsx` (variantes `primary`, `secondary`, `ghost`, `outline`, `danger`; tamaños `sm`/`md`/`lg` con áreas táctiles `>= 44px` y compactas `>= 24px`; `disabled`; `loading` con `aria-busy="true"` y bloqueo anti-doble clic; `motion-reduce:animate-none`)
- [ ] T033 [P] [US4] [TDD-RED] Escribir pruebas unitarias de `StatusBadge` en `easyeat/frontend/tests/components/primitives/StatusBadge.test.tsx` (variantes `neutral`, `info`, `success`, `warning`, `danger`; etiqueta textual obligatoria; soporte de `icon` con `aria-hidden="true"` y `prefix`)
- [ ] T034 [P] [US4] [TDD-RED] Escribir pruebas unitarias de `FeedbackState` en `easyeat/frontend/tests/components/primitives/FeedbackState.test.tsx` (variantes `info`, `success`, `warning`, `danger`; `role="alert"` + `aria-live="assertive"` en `danger` y `role="status"` + `aria-live="polite"` en las demás; `title`, `message`, acción de reintento y cierre operables por teclado)
- [ ] T035 [P] [US4] [TDD-RED] Escribir pruebas unitarias de `LoadingSkeleton` en `easyeat/frontend/tests/components/primitives/LoadingSkeleton.test.tsx` (variantes `text` con múltiples líneas, `block` y `circle`; figuras `aria-hidden="true"`; contenedor `role="status"` con texto `sr-only`; `motion-reduce:animate-none`)
- [ ] T036 [P] [US4] [TDD-RED] Escribir pruebas unitarias de `EmptyState` en `easyeat/frontend/tests/components/primitives/EmptyState.test.tsx` (ícono decorativo opcional, título, descripción y acción principal renderizada con `Button` del tema activo operable por teclado)
- [ ] T037 [P] [US4] [TDD-GREEN] Implementar `Button` gobernado únicamente por tokens `--ee-*` en `easyeat/frontend/src/components/primitives/Button.tsx` (depende de T032) y ejecutar `npx vitest run tests/components/primitives/Button.test.tsx`
- [ ] T038 [P] [US4] [TDD-GREEN] Implementar `StatusBadge` gobernado únicamente por tríadas semánticas `--ee-*` en `easyeat/frontend/src/components/primitives/StatusBadge.tsx` (depende de T033) y ejecutar `npx vitest run tests/components/primitives/StatusBadge.test.tsx`
- [ ] T039 [P] [US4] [TDD-GREEN] Implementar `FeedbackState` gobernado únicamente por tríadas semánticas `--ee-*` en `easyeat/frontend/src/components/primitives/FeedbackState.tsx` (depende de T034) y ejecutar `npx vitest run tests/components/primitives/FeedbackState.test.tsx`
- [ ] T040 [P] [US4] [TDD-GREEN] Implementar `LoadingSkeleton` gobernado únicamente por tokens `--ee-*` en `easyeat/frontend/src/components/primitives/LoadingSkeleton.tsx` (depende de T035) y ejecutar `npx vitest run tests/components/primitives/LoadingSkeleton.test.tsx`
- [ ] T041 [P] [US4] [TDD-GREEN] Implementar `EmptyState` reutilizando `Button` y tokens `--ee-*` en `easyeat/frontend/src/components/primitives/EmptyState.tsx` (depende de T036, T037) y ejecutar `npx vitest run tests/components/primitives/EmptyState.test.tsx`

### Consolidación de catálogo y matriz de 20 combinaciones (`FR-016`, `FR-017`, `SC-005`)

- [ ] T042 [US4] Exportar el catálogo unificado de los 5 primitivos en `easyeat/frontend/src/components/primitives/index.ts` (depende de T037, T038, T039, T040, T041)
- [ ] T043 [US4] Escribir y ejecutar prueba integradora de matriz en `easyeat/frontend/tests/components/primitives/PrimitivesMatrix.test.tsx` verificando las 20 combinaciones (`5 primitivos × 4 temas`) bajo `ThemeScopeProvider` más aserción estática de `0` referencias a nombres de rol o tema (`comensal`, `mostrador`, `chef`, `admin`, `COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`) en `easyeat/frontend/src/components/primitives/*.tsx` (depende de T042)

**Checkpoint**: Los 5 primitivos base están verificados en los 4 temas con polimorfismo puro.

---

## Phase 7: Polish & Cross-Cutting Concerns
> **GitHub Issue**: [#454](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/454)

**Purpose**: Verificación integral de no regresión (`001`–`023`), autonomía tipográfica en bundle de producción, auditoría visual constitucional y cierre documental contra `quickstart.md`.

- [ ] T044 Ejecutar verificación completa de tipado, regresión integral y build de producción (`npm run lint`, `npm test` y `npm run build` en `easyeat/frontend/`) confirmando 100% de la suite `001`–`023` + `024` en verde y presencia exclusiva de archivos `.woff2` locales con `0` referencias a `fonts.googleapis.com` o `Inter` en `easyeat/frontend/dist/`
- [ ] T045 Verificar invariante de alcance exclusivo frontend (`git diff -- easyeat/backend easyeat/frontend/src/api` vacío y ausencia de rutas artificiales de demostración en `easyeat/frontend/src/router/AppRoutes.tsx`)
- [ ] T046 Ejecutar re-auditoría visual mediante `/impeccable critique` sobre `easyeat/frontend/src/components/layout/RoleLayoutShell.tsx` y `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` confirmando superación del puntaje base `17/40`, `0` defectos P0/P1 críticos y conformidad con los cuatro `.DESIGN.md` (`SC-008`)
- [ ] T047 Validar el checklist completo de escenarios `SC-001` a `SC-009` descrito en `specs/024-ui-theme-architecture/quickstart.md` (incluyendo inspección en navegador real en viewports `360×800` y `1280×800` y pestaña Network → Font)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup — T001..T004)**: Sin dependencias previas; puede iniciarse de inmediato.
- **Phase 2 (Foundational — T005..T007)**: Depende de Phase 1; **BLOQUEA** todas las User Stories.
- **Phase 3 (US1 — T008..T018)**: Depende de Phase 2. Habilita `ThemeScopeProvider` y `themeRegistry` consumidos por US2, US3 y US4.
- **Phase 4 (US2 — T019..T029)**: Depende de US1 (T017) para el estilo de `RoleLayoutShell` y pruebas integradas, aunque T019–T023 (`RoleHamburgerMenu` y `AdminNotificationBell`) pueden prepararse en paralelo una vez cerrada Phase 2.
- **Phase 5 (US3 — T030..T031)**: Depende de US1 (T018) para validar la convivencia real `ThemeScopeProvider` + `BrandThemeProvider`.
- **Phase 6 (US4 — T032..T043)**: Depende de Phase 2 (`themes.css`) para los 5 pares atómicos T032–T041, y de US1 (T017) para la matriz integradora T043.
- **Phase 7 (Polish — T044..T047)**: Depende de la finalización de US1, US2, US3 y US4.

### Within Each User Story (TDD Discipline)

- Escribir siempre la tarea `[TDD-RED]` primero y comprobar su fallo antes de iniciar las tareas `[TDD-GREEN]` del mismo corte vertical.
- No agrupar implementaciones antes de correr su prueba correspondiente (evitar horizontal slicing).
- Realizar commits atómicos por corte vertical o al cierre de cada User Story en verde.

---

## Parallel Opportunities & Examples

### Ejemplo paralelo — US1 (Remediación de los 4 `.DESIGN.md`)

```text
Tras fallar T008 (Red), ejecutar en paralelo:
- T009 [P] [US1] Remediar .specify/memory/designs/comensal.DESIGN.md
- T010 [P] [US1] Remediar .specify/memory/designs/mostrador.DESIGN.md
- T011 [P] [US1] Remediar .specify/memory/designs/chef.DESIGN.md
- T012 [P] [US1] Remediar .specify/memory/designs/admin.DESIGN.md
```

### Ejemplo paralelo — US4 (5 primitivos independientes Red → Green)

```text
Fase Red en paralelo (archivos independientes):
- T032 [P] [US4] tests/components/primitives/Button.test.tsx
- T033 [P] [US4] tests/components/primitives/StatusBadge.test.tsx
- T034 [P] [US4] tests/components/primitives/FeedbackState.test.tsx
- T035 [P] [US4] tests/components/primitives/LoadingSkeleton.test.tsx
- T036 [P] [US4] tests/components/primitives/EmptyState.test.tsx

Fase Green en paralelo (cada primitivo contra su propia prueba Red):
- T037 [P] [US4] src/components/primitives/Button.tsx
- T038 [P] [US4] src/components/primitives/StatusBadge.tsx
- T039 [P] [US4] src/components/primitives/FeedbackState.tsx
- T040 [P] [US4] src/components/primitives/LoadingSkeleton.tsx
- T041 [P] [US4] src/components/primitives/EmptyState.tsx (tras T037 por reutilización de Button)
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2 + User Story 1)

1. Completar Phase 1 (fuentes autoalojadas `@fontsource/*`, `index.html`, `tailwind.config.js`) y Phase 2 (`types.ts`, `themes.css`, `index.css`).
2. Completar Phase 3 (US1: remediación de los 4 `.DESIGN.md`, `themeRegistry.ts`, `routeThemeResolver.ts`, `ThemeScopeProvider` y `DataVizThemeTokens`).
3. **DETENER y VALIDAR**: ejecutar `themeContractsContrast.test.ts` y `routeThemeScope.test.tsx` de forma independiente.

### Incremental Delivery

1. **Incremento 1 (MVP — US1)**: Contratos remediados + ámbito determinista por ruta + `DataVizThemeTokens`.
2. **Incremento 2 (US2)**: `RoleLayoutShell` minimalista + `RoleHamburgerMenu` + `AdminNotificationBell` accesible + compatibilidad transitoria `CounterWorkspacePage` y `AdminAnalyticsPage`.
3. **Incremento 3 (US3)**: Convivencia verificada y blindada con branding de establecimiento (Spec 016).
4. **Incremento 4 (US4)**: Catálogo de 5 primitivos base verificados en las 20 combinaciones (`5 × 4`).
5. **Cierre (Phase 7)**: Suite completa `001`–`024`, build con verificación de `.woff2` locales, `git diff` cero en backend/API y re-critique con Impeccable.

---

## Notes

- Toda tarea `[P]` opera sobre archivos distintos sin colisión.
- Ninguna tarea toca `easyeat/backend/` ni `easyeat/frontend/src/api/`.
- Las pantallas internas específicas de cada rol permanecen intactas para las especificaciones `025` (Comensal), `026` (Mostrador), `027` (Chef) y `028` (Administrador).
