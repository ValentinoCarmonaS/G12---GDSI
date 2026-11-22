# Investigación técnica (Phase 0): Rediseño ejecutivo Glassmorphism del Administrador

**Feature**: `specs/026-admin-glassmorphism-redesign/spec.md`  
**Date**: 22/11/2026  
**Branch**: `feat/026-admin-glassmorphism-redesign`

---

## Decisiones técnicas consolidadas

### D-01 — Reutilización estricta de bloques existentes y creación acotada de bloques Glassmorphism
- **Decision**: Reutilizar `AccessibleModal`, `useAccessibleOverlay` y `AuthCardShell` desde `easyeat/frontend/src/components/diner/` (ya implementados de forma 100 % polimórfica sobre tokens `--ee-*` en Spec `025`) junto con los cinco primitivos base de `src/components/primitives/` (`Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`). Crear únicamente dos componentes nuevos en `easyeat/frontend/src/components/admin/`: `GlassCard.tsx` y `AdminSidebar.tsx`.
- **Rationale**: Evita duplicación de lógica crítica de accesibilidad (`Escape`, retorno de foco, `scroll lock`) en los 8 modales administrativos y en `/first-login-password-change`, mantiene intacto el código de Spec `025` sin tocar archivos fuera de alcance y aísla los comportamientos propios de Glassmorphism (cristal esmerilado `rgba(255,255,255,0.82)` + `blur(12px)` + degradación bajo `prefers-reduced-transparency` y barra lateral ejecutiva fija en escritorio).
- **Alternatives considered**: (1) Promover `AccessibleModal`/`AuthCardShell` a `src/components/common/` (rechazado por modificar imports en archivos de Spec `025`); (2) Duplicar modales en `src/components/admin/` (rechazado por violar reutilización y aumentar riesgo de divergencia WCAG 2.2 AA).

### D-02 — Navegación ejecutiva bimodal en `RoleLayoutShell` y `roleNavigationConfig`
- **Decision**: Extender `RoleNavigationConfig` con un flag opcional `useDesktopSidebar?: boolean` activado exclusivamente para `ADMINISTRADOR`, reduciendo sus ítems de 7 a 6 (eliminando `Métricas` al consolidarse en `Panel` `/admin`) agrupados en `Operación` (`Panel`, `Operaciones`, `Inventario`), `Catálogo y equipo` (`Auditoría`, `Personal`) y `Negocio` (`Mi local`). En `RoleLayoutShell`, cuando `config.useDesktopSidebar` es `true`, renderizar `AdminSidebar` fija y siempre expandida desde `lg:` (1024 px+) y ocultar `RoleHamburgerMenu` en `lg:` (`lg:hidden`), preservando el menú hamburguesa en móvil/tableta (`< 1024 px`).
- **Rationale**: Cumple `FR-005` sin alterar `COMENSAL`, `ENCARGADO` ni `CHEF` (que conserva `exposeDirectDesktopNav` de Spec `005` C-08), evita doble navegación en escritorio y mantiene la barra superior minimalista con `AdminNotificationBell`, insignia de local e indicador no navegable de cuenta.
- **Alternatives considered**: Crear un shell duplicado `AdminLayoutShell` (rechazado porque duplica cabecera, skip-link, notificaciones y manejo de sesión).

### D-03 — Sincronización ultraligera de `pendingCount` sin polling (`AdminPendingDishesProvider`)
- **Decision**: Implementar `AdminPendingDishesContext` / `AdminPendingDishesProvider` montado únicamente en el subárbol `/admin/*` en `AppRoutes.tsx`. Realiza una única consulta mínima (`size=1`, `status=PENDING_REVIEW` usando el cliente existente de revisión de platos) al montar el espacio administrativo, sin polling periódico. Expone `pendingCount: number | null`, `setPendingCount(count: number)` y `decrementPendingCount()` para que `AdminDishReviewPage` sincronice el contador en memoria usando el `totalElements` ya devuelto por sus propias recargas tras aprobar, rechazar o cancelar un plato (cero requests adicionales al servidor). Si la carga inicial falla o está en curso, `pendingCount` permanece `null` y la insignia no se muestra (nunca un cero falso).
- **Rationale**: Cumple `FR-006` con impacto prácticamente nulo en un servidor de recursos limitados y en dispositivos móviles/escritorio del Administrador.
- **Alternatives considered**: Polling periódico (rechazado por consumo innecesario de servidor y prohibido en `FR-006`); props drilling en 7 páginas (rechazado por acoplamiento); CustomEvents globales (rechazado por menor trazabilidad tipada).

### D-04 — Consolidación de `/admin` y redirección transparente de `/admin/analytics`
- **Decision**: Reemplazar el tablero placeholder de `AdminWorkspacePage.tsx` (`/admin`) por el Panel Ejecutivo consolidado (Salud Financiera + Ranking de Platos Más Vendidos de Spec `023`, manteniendo la verificación de vigencia de sesión al montar). En `AppRoutes.tsx`, convertir `/admin/analytics` en `<Navigate to="/admin" replace />` (retirando el contenedor `legacySurfaceMode="legacy-dark"` de las rutas administrativas).
- **Rationale**: Cumple `FR-007` y `SC-001`, elimina el modo oscuro heredado incompatible con Glassmorphism y preserva compatibilidad con marcadores/enlaces existentes a `/admin/analytics`.
- **Alternatives considered**: Mantener `/admin/analytics` como pantalla duplicada (rechazado por redundancia y fricción ejecutiva).

### D-05 — Composición del Panel Ejecutivo y migración de Recharts a `dataViz`
- **Decision**: Estructurar `/admin` con una banda superior de KPIs de Salud Financiera (ingresos totales y pedidos concretados en `font-ee-mono tabular-nums`) + selector segmentado accesible `Día` / `Semana` / `Mes`, seguida en escritorio (`lg:`) por una grilla asimétrica de 2 columnas (`lg:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]`, ~62 % / ~38 %): `FinancialHealthChart` a la izquierda y `DishRankingTable` (migrado a tarjetas Glassmorphism) a la derecha, apilados verticalmente en móvil y conservando selectores de período independientes (`023`). Estilizar `FinancialHealthChart` consumiendo el sub-contrato programático `dataViz` de Spec `024` (`useRoleTheme().dataViz`) para series (`#1856FF`, `#3A344E`), grilla (`#CBD5E1`), ejes (`#64748B`), etiquetas (`#334155`), tooltip (`#141414`/`#FFFFFF`/`#64748B`) y cursor (`rgba(24,86,255,0.10)`), conservando el modo alternable `"Ver tabla"` / `"Ver gráfico"` en tabla semántica accesible.
- **Rationale**: Cumple `FR-008`, garantiza WCAG 2.2 AA en gráficos vectoriales sin hardcodear hexadecimales dispersos y mantiene la visibilidad inmediata de KPIs y top dish en `< 3s` (`SC-004`).
- **Alternatives considered**: Cambiar de librería de gráficos (rechazado por agregar dependencias y riesgo innecesario).

### D-06 — Patrón unificado de tarjetas Glassmorphism y excepciones semánticas
- **Decision**: Migrar todas las listas administrativas (`DishRankingTable`, `StaffRosterTable`, `IngredientCatalogTable`, `ReviewInboxList`/`ReviewInboxRow`, `FoodCostBreakdown`, `WeeklyScheduleMatrix`) a tarjetas `GlassCard` responsivas (grillas adaptables en PC, apilado vertical sin scroll horizontal desde 360 px). Preservar exactamente las dos excepciones semánticas aprobadas en `FR-009`: (a) la vista alternable `"Ver tabla"` de `FinancialHealthChart` mantiene `<table>` semántica, y (b) `SlotCapacityGrid` mantiene `role="row"` por franja dentro de su contenedor Glassmorphism.
- **Rationale**: Elimina el scroll horizontal móvil en las vistas densas sin debilitar accesibilidad ni aserciones conductuales existentes en pruebas.
- **Alternatives considered**: Tablas con scroll horizontal en móvil (rechazado por baja ergonomía móvil).

### D-07 — Split-pane en escritorio y maestro-detalle en móvil para `/admin/dishes`
- **Decision**: En `AdminDishReviewPage.tsx`, usar una grilla dividida en escritorio (`lg:grid-cols-[380px_minmax(0,1fr)]`): bandeja de tarjetas (`ReviewInboxTabs`, `ReviewInboxList`, `InboxPagination`) a la izquierda y panel de auditoría (`DishAuditPanel`, `FoodCostBreakdown`, `MarginPriceCalculator`, `DishDecisionActions`) a la derecha. En móvil/tableta (`< 1024 px`), alternar entre bandeja y detalle con un botón accesible `Volver a la bandeja` (`min-h-[44px]`) preservando pestaña y página activas, incluso al redimensionar la ventana.
- **Rationale**: Cumple `FR-010` y permite auditar recetas y fijar precios en móvil sin paneles aplastados ni pérdida de contexto.
- **Alternatives considered**: Modal de detalle para el plato (rechazado por densidad de contenido de receta + calculadora + acciones).

### D-08 — Acabado Glassmorphism, `prefers-reduced-transparency` y `prefers-reduced-motion`
- **Decision**: Definir en `GlassCard` y superficies administrativas el cristal esmerilado de `admin.DESIGN.md`: `rgba(255, 255, 255, 0.82)` + `backdrop-filter: blur(12px)`, borde luminoso `1px solid var(--ee-border-subtle)`, sombra azulada `0 8px 28px rgba(24, 86, 255, 0.08)` sobre lienzo `#F4F7FC` con atmósfera fría sutil (cero degradados púrpura-azul). Bajo `@media (prefers-reduced-transparency: reduce)`, reforzar la opacidad de superficie a `#FFFFFF` sólido; bajo `@media (prefers-reduced-motion: reduce)`, suprimir transiciones (que en reposo no superan `200ms`).
- **Rationale**: Cumple `FR-001`, `FR-004` y `SC-002` / `SC-006` en modo **Operate**.
- **Alternatives considered**: Transparencia alta fija sin fallback (rechazado por degradar legibilidad en usuarios con sensibilidad visual).

### D-09 — Aislamiento de marca en `/admin/mi-local` y polimorfismo en `/first-login-password-change`
- **Decision**: En `AdminBrandingPage.tsx` (`/admin/mi-local`), aplicar layout Glassmorphism de 2 columnas en escritorio (`lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]`) con formulario (`LogoUploader`, `ColorPickerInput` con hex en `JetBrains Mono`) a la izquierda y previsualización en vivo fija a la derecha, aislando estrictamente los colores editados dentro del contenedor de previsualización (`FR-014`). En `FirstLoginPasswordPage.tsx`, migrar la vista a `AuthCardShell` + primitivos base (`--ee-*`) para que herede automáticamente el tema del rol autenticado con respaldo a Administrador Glassmorphism (`FR-015`).
- **Rationale**: Preserva el aislamiento de Spec `016` y `024`, y evita lógica condicional por rol en `/first-login-password-change`.
- **Alternatives considered**: Estilos dedicados por rol en `FirstLoginPasswordPage` (rechazado por romper `FR-016` de Spec `024`).

### D-10 — Actualización acotada de pruebas heredadas (`FR-017`)
- **Decision**: Mantener el 100 % de las aserciones conductuales de `001`–`025`. Actualizar únicamente las aserciones de navegación y del antiguo hub placeholder en los tests afectados por la consolidación aprobada de `/admin` (7 → 6 ítems al consolidar `Métricas` en `Panel`, y reemplazo de las tarjetas placeholder de `/admin` por el Panel Ejecutivo consolidado) y agregar una suite trazable dedicada en `easyeat/frontend/tests/admin/`.
- **Rationale**: Garantiza `SC-005` y trazabilidad explícita sin debilitar cobertura conductual.
- **Alternatives considered**: Dejar `Métricas` como ítem fantasma para no tocar tests (rechazado por contradecir la decisión aprobada en `spec.md`).
