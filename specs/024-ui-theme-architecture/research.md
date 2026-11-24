# Investigación técnica (Phase 0): Arquitectura de temas visuales por rol y primitivos base accesibles

**Especificación**: `specs/024-ui-theme-architecture/spec.md`  
**Rama**: `feat/024-ui-theme-architecture`  
**Fecha**: 21/11/2026

Este documento resuelve todas las decisiones técnicas de Phase 0 para la especificación `024`, eliminando cualquier ambigüedad antes de definir los modelos de datos, contratos de interfaz y estrategia de verificación.

---

## 1. Autoalojamiento local de activos tipográficos en Vite (`FR-002`, `SC-009`)

### Decisión

Empaquetar localmente las cinco familias tipográficas mediante paquetes `@fontsource/*` en `easyeat/frontend/package.json`, importando exclusivamente las hojas CSS correspondientes a los pesos autorizados por cada tema y verificando `font-display: swap` junto con pilas de respaldo del sistema con métricas compatibles:

| Tema de rol | Paquetes `@fontsource/*` | Pesos importados estrictos | Rol tipográfico | Pila de respaldo del sistema |
|---|---|---|---|---|
| Comensal (`Bento`) | `@fontsource/dm-sans`, `@fontsource/jetbrains-mono` | `400`, `500`, `600`, `700` (sans); `400`, `500`, `600` (mono) | Encabezados y cuerpo (`DM Sans`); etiquetas técnicas (`JetBrains Mono`) | `'DM Sans', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif` |
| Mostrador (`Bold`) | `@fontsource/archivo-black`, `@fontsource/archivo`, `@fontsource/jetbrains-mono` | `400` (display); `400`, `500`, `600`, `700` (body); `400`, `500`, `600` (mono) | Encabezados de impacto (`Archivo Black`); cuerpo operativo (`Archivo`); códigos y etiquetas (`JetBrains Mono`) | `'Archivo Black', 'Arial Black', Impact, sans-serif` / `'Archivo', ui-sans-serif, system-ui, 'Segoe UI', sans-serif` |
| Chef (`Brutalism`) | `@fontsource/darker-grotesque`, `@fontsource/jetbrains-mono` | `500`, `600`, `700`, `800` (display y body); `400`, `500`, `600` (mono) | Encabezados y cuerpo (`Darker Grotesque`); etiquetas técnicas (`JetBrains Mono`) | `'Darker Grotesque', ui-sans-serif, system-ui, 'Segoe UI', sans-serif` |
| Administrador (`Glassmorphism`) | `@fontsource/plus-jakarta-sans`, `@fontsource/jetbrains-mono` | `400`, `500`, `600`, `700` (display y body); `400`, `500`, `600` (mono) | Encabezados y cuerpo (`Plus Jakarta Sans`); etiquetas técnicas (`JetBrains Mono`) | `'Plus Jakarta Sans', ui-sans-serif, system-ui, 'Segoe UI', sans-serif` |

La tipografía `Inter` queda completamente erradicada de los contratos visuales, de las hojas de estilo y del grafo de dependencias.

### Fundamento

1. **Cero peticiones a CDN en tiempo de ejecución**: Vite procesa los imports `@fontsource/<family>/<weight>.css`, copia los subconjuntos `.woff2` (latin y latin-ext) en `dist/assets/` con hash de contenido y reescribe las URL de `@font-face` a rutas relativas internas. Ninguna regla apunta a `fonts.googleapis.com` ni a dominios externos.
2. **Caché inmutable ya existente**: `easyeat/frontend/nginx.conf` sirve `location /assets/` con `Cache-Control: public, max-age=31536000, immutable`. Los archivos `.woff2` generados por Vite heredan automáticamente esa política sin modificar la infraestructura de despliegue.
3. **Descarga selectiva por tema activo**: los navegadores descargan un archivo `.woff2` declarado en `@font-face` únicamente cuando un nodo renderizado en el DOM solicita esa `font-family` y ese `font-weight`. Al aplicar cada familia únicamente dentro de su selector `[data-theme="<role>"]`, un Comensal solo descarga `DM Sans` y `JetBrains Mono`, sin transferir `Archivo Black`, `Archivo`, `Darker Grotesque` ni `Plus Jakarta Sans`.
4. **Control estricto de pesos y `font-display: swap`**: todos los paquetes `@fontsource/*` declaran `font-display: swap` por defecto en sus CSS por peso, evitando FOIT (texto invisible) y limitando la carga exacta a los pesos definidos en `FR-002` en lugar de los nueve pesos `100`–`900`.
5. **Reproducibilidad y mantenimiento**: versionar las fuentes mediante `package.json` y `package-lock.json` evita mantener archivos binarios manualmente en Git y mantiene auditoría estándar de dependencias.

### Alternativas evaluadas

- **Google Fonts CDN en `index.html`**: descartada por violar `FR-002` y `SC-009` (dependencia externa en runtime y riesgo en entornos sin salida a internet).
- **Archivos `.woff2` versionados manualmente en `src/assets/fonts/`**: descartada por requerir mantenimiento manual de subconjuntos Unicode, rangos `unicode-range` y licencias binarias en el repositorio.
- **Fuentes variables (`@fontsource-variable/*`)**: descartada parcialmente porque `Archivo Black` solo existe como peso estático `400` y los pesos estáticos por archivo garantizan el subconjunto exacto solicitado por `FR-002` con menor tamaño cuando una vista usa pocos pesos.

---

## 2. Aislamiento de tokens CSS en Tailwind CSS 3.4 frente a Spec 016 (`FR-003`, `FR-005`, `FR-006`)

### Decisión

Implementar una arquitectura de tokens CSS en dos capas estrictamente separadas y sin superposición de prefijos:

1. **Capa de tema de rol (`--ee-*`)**:
   - Definida desde una fuente única de verdad en TypeScript (`easyeat/frontend/src/theme/themeRegistry.ts`) sincronizada con bloques CSS por selector de atributo `[data-theme="comensal"]`, `[data-theme="mostrador"]`, `[data-theme="chef"]` y `[data-theme="admin"]` en `easyeat/frontend/src/styles/themes.css`.
   - El atributo `data-theme` se aplica de forma determinista y síncrona sobre `document.documentElement` y sobre el contenedor raíz `ThemeScopeProvider` (`data-testid="theme-scope-root"`), reemplazando el valor anterior por completo en cada transición de ruta.
   - `tailwind.config.js` conserva intactos los tokens heredados `primary` (`colors.emerald`) y `brand` (`dark`, `accent`, `text`) para garantizar cero regresiones visuales en pantallas heredadas de `001`–`023`, y extiende utilidades semánticas bajo el prefijo `ee` (por ejemplo `bg-ee-surface`, `bg-ee-elevated`, `text-ee-ink`, `text-ee-muted`, `border-ee-subtle`, `border-ee-strong`, `bg-ee-primary`, `text-ee-on-primary`, `ring-ee-focus`, `font-ee-display`, `font-ee-sans`, `font-ee-mono`, `rounded-ee-sm`, `rounded-ee-md`).
   - Los tokens cromáticos que requieran opacidad en Tailwind se almacenan como tripletes RGB o pares hex/RGB derivados desde la misma definición, evitando duplicación manual.
2. **Capa de marca de establecimiento (`--brand-*`, Spec 016)**:
   - Permanece confinada exclusivamente a `BrandThemeProvider` (`easyeat/frontend/src/components/common/BrandThemeProvider.tsx`) con sus seis variables existentes (`--brand-primary`, `--brand-primary-contrast`, `--brand-secondary`, `--brand-secondary-contrast`, `--brand-accent`, `--brand-accent-contrast`).
   - Ningún selector `[data-theme="*"]` declara ni modifica variables `--brand-*`, y ningún primitivo base ni `RoleLayoutShell` lee variables `--brand-*`.
   - Cuando el Comensal navega fuera de un establecimiento personalizado o hacia otro local con branding neutro, el desmontaje o recarga de `BrandThemeProvider` limpia automáticamente las variables inline del nodo `data-testid="brand-theme-container"`, manteniendo intacta la capa `--ee-*` de Comensal (`Bento`).
   - En `/admin/mi-local` (`AdminBrandingPage`), el contenedor administrativo permanece regido exclusivamente por `[data-theme="admin"]` (`--ee-*`), sin propagar los colores editados hacia el shell de administración.

### Fundamento

- Separar los namespaces (`--ee-*` en raíz de tema vs. `--brand-*` en el subárbol de catálogo del Comensal) elimina estructuralmente toda posibilidad de colisión o sobrescritura cruzada (`FR-005`, `FR-006`).
- Aplicar `data-theme` mediante una función pura `resolveThemeForRoute(pathname, userRole)` asegura que al cambiar de Mostrador (oscuro `#111111`) a Login o Chef (claro), todas las propiedades CSS heredadas se recalculen en un único frame sin dejar estilos inline residuales.
- Conservar `colors.primary` y `colors.brand` en `tailwind.config.js` protege las ~60 vistas y componentes de `001`–`023` que todavía usan utilidades Tailwind anteriores.

### Alternativas evaluadas

- **Sobrescribir `colors.primary` en `tailwind.config.js` con variables CSS**: descartada por alto riesgo de regresión de contraste en pantallas heredadas que asumen escala `emerald` (`primary-50` a `primary-900`).
- **Inyectar estilos de tema mediante `style={{ ... }}` inline por componente**: descartada porque ensucia el DOM, dificulta el uso de utilidades Tailwind (`hover:`, `focus-visible:`) y favorece fugas de estado entre transiciones.
- **Usar Tailwind `darkMode: 'class'` para Mostrador**: descartada porque EasyEat no tiene un par light/dark binario sino cuatro sistemas de diseño completos con tipografía, geometría, bordes, elevación y paletas semánticas independientes.

---

## 3. Auditoría inicial (`/impeccable init`) y reglas de `Awesome Design` aplicadas a `.DESIGN.md` (`FR-001`, `FR-003`, `FR-017`)

### Decisión

1. **Registro de verdad de producto (`/impeccable init`)**:
   - Se ejecutó `impeccable context`, confirmando ausencia previa de `PRODUCT.md` y `.impeccable/config.json`.
   - Tras verificar la documentación vigente (`README.md` y `specs/001-*` a `specs/024-*`) y confirmar con el responsable del proyecto, se crearon `PRODUCT.md` (esquema `impeccable:product-schema 1`, plataforma `web`) y `.impeccable/config.json` con `"buildPath": "code"` (flujo `code-first` coherente con pantallas operativas y los cuatro contratos `.DESIGN.md`).
2. **Aplicación de skills `bento`, `bold`, `brutalism` y `glassmorphism` bajo precedencia constitucional (Principio XI)**:
   - **Comensal (`Bento` — `comensal.DESIGN.md`)**: preserva la grilla modular tipo bloques con jerarquía limpia, superficie cálida `#FFF5E6`, texto `#111827`, acentos `#FAD4C0` y `#80A1C1` y escala de espaciado `4/8/12/16/24/32`. Remediación constitucional: se reemplaza `Inter` por `DM Sans` (400/500/600/700), se restringe `JetBrains Mono` a 400/500/600, se añaden pares `on-primary` (`#111827`, ratio `12.88:1`), `primary-border` (`#9A4A24`, ratio `5.76:1` sobre `#FFF5E6` para cumplir WCAG 2.2 AA 1.4.11 en botones sobre fondo crema) y `on-secondary` (`#111827`, ratio `6.58:1`).
   - **Encargado del Mostrador (`Bold` — `mostrador.DESIGN.md`)**: preserva estética oscura contundente (`#111111`), acentos `#0077BC` y `#009866`, objetivos táctiles `>= 44 px` y encabezados expresivos en `Archivo Black`. Remediación constitucional: se corrige el defecto crítico de texto `#111827` sobre `#111111` reemplazándolo por `#F8FAFC` (ratio `18.05:1`), se adopta `Archivo` (400/500/600/700) para cuerpo operativo reservando `Archivo Black` (400) a encabezados de impacto, y se fija `on-secondary: #0A0A0A` sobre `#009866` (ratio `5.36:1`, dado que `#FFFFFF` sobre `#009866` falla el umbral `4.5:1`).
   - **Chef (`Brutalism` — `chef.DESIGN.md`)**: preserva estética funcional cruda de alto contraste sobre `#FFFFFF`, texto `#111827`, bordes marcados `#111827`, geometría directa con radios contenidos (`0px`/`2px`/`4px`) y tipografía `Darker Grotesque` (500/600/700/800) + `JetBrains Mono`. Remediación constitucional: se fijan `on-primary: #111827` sobre `#DD614C` (ratio `4.97:1`) y `primary-hover: #E67966` (ratio `6.16:1` con `#111827`, evitando la caída por debajo de `4.5:1` al oscurecer terracota) junto con `on-secondary: #111827` sobre `#DAA144` (ratio `7.74:1`).
   - **Administrador (`Glassmorphism` — `admin.DESIGN.md`)**: preserva entorno profesional limpio de alto contraste con acentos `#1856FF` y `#3A344E`, capas translúcidas esmeriladas (`rgba(255, 255, 255, 0.82)` con `backdrop-filter: blur(12px)` sobre lienzo sutil `#F4F7FC`, sin degradados púrpura-azul prohibidos), texto `#141414` y tipografía `Plus Jakarta Sans` (400/500/600/700) + `JetBrains Mono`. Remediación constitucional: se reemplazan los tonos semánticos crudos de bajo contraste sobre blanco (`#07CA6B`, `#E89558`, `#EA2143`) por tríadas derivadas accesibles (`fg` sobre `bg` entre `5.8:1` y `9.4:1`, y `danger-solid: #C81433` con `#FFFFFF` a `5.83:1`).
   - **Cierre de deuda constitucional**: en la fase de implementación (`024`), la actualización de los cuatro `.DESIGN.md` permite cerrar el `TODO(DESIGN_CONTRACTS_REMEDIATION)` registrado en `.specify/memory/constitution.md` (v2.1.0).

### Fundamento

- Todos los ratios fueron verificados matemáticamente mediante la fórmula estándar de luminancia relativa sRGB de WCAG 2.2 (37 pares verificados por cada tema, 148 verificaciones con 0 fallas).
- Ningún tema recurre a patrones prohibidos por la política Zero AI-Slop (`Inter`, degradados púrpura-azul, tarjetas anidadas innecesarias o texto gris de bajo contraste).

---

## 4. Compatibilidad transitoria con pantallas heredadas (`FR-018`, `SC-007`) y arquitectura de `WorkspaceLayout` (`FR-007`–`FR-010`)

### Decisión

1. **Contenedores transitorios de aislamiento cromático (`data-legacy-surface`) hasta Specs `025`–`028`**:
   - **`CounterWorkspacePage.tsx` (Mostrador — pendiente Spec `026`)**: actualmente renderiza texto `text-gray-900` y tarjetas `bg-white` directamente dentro del `<main>` de `WorkspaceLayout`. Al activar el tema oscuro `#111111` de Mostrador, el contenido heredado se envuelve en una zona de contención transitoria (`data-legacy-surface="light"`, superficie `#FFFFFF` / borde sutil y texto `#111827` aislado dentro del área de contenido heredado) para preservar legibilidad `>= 4.5:1` en todos los controles heredados mientras la barra superior, el shell y los nuevos primitivos ya lucen plenamente el tema Mostrador (`Bold`).
   - **`AdminAnalyticsPage.tsx` (Administrador — pendiente Spec `028`)**: actualmente no usa `WorkspaceLayout`, fija clases oscuras (`text-neutral-100`, `border-neutral-800`, `bg-neutral-950` en `FinancialHealthChart` y `DishRankingTable`) y su test `AdminAnalyticsPage.test.tsx` la renderiza de forma aislada dentro de `<MemoryRouter>` sin `AuthProvider`. Para cumplir simultáneamente `FR-007` (mostrar la barra administrativa con las 7 secciones), `FR-018` (legibilidad intacta) y `SC-007` (cero ruptura de `AdminAnalyticsPage.test.tsx`), `AdminAnalyticsPage` se integra dentro de una ruta envolvente de shell administrativo en `AppRoutes.tsx` (sin inyectar `useAuth()` dentro de `AdminAnalyticsPage`), y su bloque heredado recibe el contenedor transitorio `data-legacy-surface="dark"` (`bg-neutral-950 rounded-2xl p-6 text-neutral-100`) para garantizar contraste completo sobre el lienzo claro de Administrador (`Glassmorphism`) hasta que la Spec `028` migre la pantalla y los gráficos Recharts al sub-contrato `DataVizThemeTokens`.
2. **Refactorización de `WorkspaceLayout` hacia `RoleLayoutShell` minimalista + menú hamburguesa**:
   - `WorkspaceLayout` conserva su firma pública (`{ title, subtitle?, children }`) para mantener compatibilidad binaria y de mocks con todas las páginas y pruebas existentes (`us1_basic_identity`, `AdminDishReviewPage.test`, `ChefDishEditorPage.test`, etc.), delegando la presentación a `RoleLayoutShell`.
   - **Barra superior minimalista por rol**: muestra únicamente la marca `EasyEat`, el título de la sección actual, el disparador del menú desplegable hamburguesa accesible (para usuarios autenticados en los 4 roles, en móvil y escritorio) y los controles esenciales del usuario (avatar/perfil, identificador del local cuando corresponda, `AdminNotificationBell` para Administrador, botón `Cerrar sesión` o accesos `Iniciar sesión` / `Registrarse` para invitados).
   - **Excepción Chef en escritorio (`FR-009`, Spec 005 C-08)**: el rol Chef mantiene directamente visibles en la cabecera de escritorio (`md:flex`) los tres accesos de primer nivel (`Comandas KDS`, `Gestionar Platos`, `Ingredientes`) con `aria-current="page"` derivado de la ruta, mientras que en móvil (`< 768px`) esos tres enlaces se unifican en el menú desplegable hamburguesa.
   - **Actualización estrictamente acotada de pruebas heredadas (autorizada en Grill-Me)**: `LogoutFlow.test.tsx` y `KitchenWorkspacePage.test.tsx` continúan pasando sin cambios (manteniendo `Cerrar sesión` accesible en cabecera y los enlaces de Chef en escritorio). En `easyeat/frontend/tests/components/WorkspaceLayout.test.tsx`, se autoriza actualizar únicamente las aserciones de layout obsoletas que exigían la barra inline antigua (`overflow-x-auto` en `<nav>` de Comensal y ausencia de ocultamiento móvil en Chef), preservando intactas todas las aserciones conductuales (rutas canónicas, `aria-current="page"`, estado invitado y cierre de sesión).

### Fundamento

- Evita tanto el fracaso de legibilidad inmediata (texto oscuro sobre `#111111` o texto blanco sobre `#F4F7FC`) como la invasión indebida del alcance reservado a las especificaciones `025`–`028`.
- Resuelve la tensión real entre `FR-007`/`FR-008` (barra minimalista + hamburguesa) y las aserciones de CSS antiguas en `WorkspaceLayout.test.tsx` con aprobación explícita documentada, sin debilitar ninguna garantía conductual de `001`–`023`.

### Alternativas evaluadas

- **Dejar el `<main>` con fondo gris neutro heredado en todos los roles**: descartada porque impediría validar el tema real de cada rol en ruta y dejaría deuda visual antes de `025`–`028`.
- **Refactorizar internamente `CounterWorkspacePage` y `AdminAnalyticsPage` en Spec `024`**: descartada por violar el control estricto de exclusiones (reservado a Specs `026` y `028`).
- **Agregar `WorkspaceLayout` directamente dentro de `AdminAnalyticsPage.tsx`**: descartada porque `AdminAnalyticsPage.test.tsx` monta el componente sin `AuthProvider` y modificar el test o silenciar el error de `useAuth` degradaría las garantías existentes.

---

## 5. Estrategia de validación visual y automatizada (`SC-001` a `SC-009`)

### Decisión

La verificación de la especificación `024` se apoya en tres capas complementarias:

1. **Suite automatizada en Vitest + React Testing Library + JSDOM (`easyeat/frontend`)**:
   - Pruebas deterministas de contraste WCAG 2.2 AA sobre la fuente única de verdad de los 4 temas (todos los pares de texto, bordes, foco, tríadas semánticas y sub-contrato `DataVizThemeTokens`).
   - Pruebas de resolución de tema por ruta (100% de rutas de `AppRoutes.tsx`, alias, fallback Comensal, `/first-login-password-change` y transiciones sin residuos).
   - Pruebas de aislamiento por capas con `BrandThemeProvider` (Spec 016).
   - Pruebas de los 4 `RoleLayoutShell` y del menú hamburguesa (teclado, `Escape`, clic exterior, retorno de foco, `aria-expanded`, `aria-current="page"`, 7 secciones de Administrador, 4 de Comensal, 1 de Mostrador y 3 de Chef con excepción C-08).
   - Matriz completa de 20 combinaciones (`5 primitivos × 4 temas`), verificando polimorfismo sin condicionales por nombre de rol, `prefers-reduced-motion`, `aria-live`, estado `loading` anti-doble envío y objetivos táctiles.
   - Ejecución integral de la suite de regresión de `001`–`023` (`npm test` y `npm run lint` + `npm run build`).
2. **Verificación de empaquetado de producción (`npm run build`)**:
   - Inspección del bundle en `dist/` y `dist/assets/` confirmando presencia de archivos `.woff2` locales para los pesos estrictos, ausencia de `Inter` y cero referencias a dominios externos de fuentes.
3. **Auditoría de calidad visual y accesibilidad (`SC-008`)**:
   - Auditoría formal con Impeccable (`/critique`) contra los cuatro `.DESIGN.md` remediados y revisión visual en navegador real en viewports móvil (`360×800`) y escritorio (`1280×800`) sobre las rutas reales de EasyEat, sin introducir rutas artificiales de demostración.
