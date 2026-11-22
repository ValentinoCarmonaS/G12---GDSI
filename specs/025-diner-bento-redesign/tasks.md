# Tasks: Rediseño visual Bento del flujo integral del Comensal

**Input**: Documentos de diseño en `specs/025-diner-bento-redesign/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`) y auditoría base Impeccable `.impeccable/critique/2026-11-22T04-59-48Z__easyeat-frontend-src-pages-catalogpage-tsx.md` (`19/40`, P0/P1 y P2 compatibles con pruebas heredadas).

**Prerequisites**: `plan.md` (aprobado), `spec.md` (`US1`–`US5`), `research.md`, `data-model.md`, `contracts/bento-ui-blocks.contract.ts`, `contracts/brand-accent-slots.contract.ts`, `contracts/diner-screens-audit.contract.ts`, `quickstart.md`.

**Tests (TDD obligatorio)**: Todas las fases funcionales siguen ciclos verticales **TDD-RED (escribir prueba y verificar que falla primero) → TDD-GREEN (implementación mínima que hace pasar la prueba)** en `easyeat/frontend/tests/diner/`, seguidos de la verificación de regresión sobre la suite heredada (`001`–`024`). **El backend (`easyeat/backend/`) y los servicios Axios (`easyeat/frontend/src/api/`) NO se modifican** (excepción `single-tier frontend-only` autorizada bajo Principio X).

**Organization**: Tareas atómicas ordenadas por dependencias y agrupadas por User Story (`US1` a `US5`) para permitir implementación, verificación y commits atómicos por historia dentro de `feat/025-diner-bento-redesign`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Ejecutable en paralelo (archivos distintos, sin dependencia sobre tareas incompletas).
- **[Story]**: User Story a la que pertenece la tarea (`[US1]` a `[US5]`). Las fases de Setup, Foundational y Polish no llevan etiqueta de historia.
- Todas las tareas incluyen rutas exactas de archivo dentro del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)
> **GitHub Issue**: [#463](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/463)

**Purpose**: Verificar la línea base de la rama `feat/025-diner-bento-redesign`, los contratos de Spec `024` y preparar los directorios de bloques Bento y pruebas de Spec `025` sin modificar dependencias ni backend.

- [X] T001 Verificar estado limpio de compilación y suite base (`npx tsc --noEmit` y `npx vitest run`) en `easyeat/frontend/` antes de iniciar cambios de Spec `025`
- [X] T002 [P] Crear estructura base y barrel export vacío en `easyeat/frontend/src/components/diner/index.ts` conforme a `specs/025-diner-bento-redesign/contracts/bento-ui-blocks.contract.ts`
- [X] T003 [P] Crear directorio de pruebas trazables `easyeat/frontend/tests/diner/` para las suites TDD de Spec `025`

---

## Phase 2: Foundational (Blocking Prerequisites)
> **GitHub Issue**: [#464](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/464)

**Purpose**: Bloques base reutilizables Bento (`BentoCard`, `AuthCardShell`), overlays accesibles WCAG 2.2 AA (`useAccessibleOverlay`, `AccessibleModal`, `AccessibleDrawer`), respaldo neutro de marca alineado a Bento (`DEFAULT_NEUTRAL_BRANDING`) y `ConnectionStatusBadge` polimórfico.

**CRITICAL**: Ninguna User Story puede comenzar hasta completar Phase 2 en verde.

### Slice TDD 0 — Seam de bloques Bento, overlays accesibles y respaldo neutro de marca (`FR-001`–`FR-003`, `FR-008`, `FR-014`)

> **RED FIRST**: Escribir las pruebas unitarias/de contrato y confirmar fallo inicial antes de implementar los bloques base.

- [X] T004 [TDD-RED] Escribir suite de pruebas en `easyeat/frontend/tests/diner/bentoBlocksAndOverlays.test.tsx` cubriendo: (1) `BentoCard` variantes `elevated`/`subtle`/`interactive`/`hero` y mapeo de padding/radio `--ee-*`; (2) `AuthCardShell` polimórfica 100 % `--ee-*`; (3) `AccessibleModal` y `AccessibleDrawer` (`role="dialog"`, `aria-modal="true"`, cierre por `Escape`, cierre por backdrop, bloqueo/restauración de scroll en `body`, retorno de foco al disparador y botón de cierre `>= 44×44 px`); (4) contraste WCAG 2.2 AA (`>= 4.5:1`) de `DEFAULT_NEUTRAL_BRANDING` con colores Bento (`#FAD4C0`/`#111827`, `#80A1C1`/`#111827`, `#9A4A24`/`#FFFFFF`); y (5) `ConnectionStatusBadge` usando tokens `--ee-warning-*` sin `animate-pulse` continuo; ejecutar `npx vitest run tests/diner/bentoBlocksAndOverlays.test.tsx` y confirmar fallo inicial
- [X] T005 [P] [TDD-GREEN] Implementar `BentoCard` (variantes `elevated`/`subtle`/`interactive`/`hero`, escala `4/8/12/16/24/32` px, radios `--ee-radius-md|lg`, sombra `--ee-shadow-elevated` sin tarjetas anidadas) en `easyeat/frontend/src/components/diner/BentoCard.tsx`
- [X] T006 [P] [TDD-GREEN] Implementar `AuthCardShell` (tarjeta centrada responsiva 100 % gobernada por tokens `--ee-*` sin colores fijos de Bento para polimorfismo cross-role) en `easyeat/frontend/src/components/diner/AuthCardShell.tsx`
- [X] T007 [P] [TDD-GREEN] Implementar hook `useAccessibleOverlay` (cierre por `Escape`, scroll lock con contador seguro, gestión de foco inicial y retorno al elemento activador) en `easyeat/frontend/src/components/diner/useAccessibleOverlay.ts`
- [X] T008 [P] [TDD-GREEN] Implementar `AccessibleModal` en `easyeat/frontend/src/components/diner/AccessibleModal.tsx` (depende de T007)
- [X] T009 [P] [TDD-GREEN] Implementar `AccessibleDrawer` (`side="left"|"right"`, objetivos táctiles `>= 44×44 px`, `aria-modal="true"`) en `easyeat/frontend/src/components/diner/AccessibleDrawer.tsx` (depende de T007)
- [X] T010 [TDD-GREEN] Exportar `BentoCard`, `AuthCardShell`, `useAccessibleOverlay`, `AccessibleModal` y `AccessibleDrawer` en `easyeat/frontend/src/components/diner/index.ts` (depende de T005, T006, T008, T009)
- [X] T011 [P] [TDD-GREEN] Actualizar `DEFAULT_NEUTRAL_BRANDING` a los pares Bento WCAG 2.2 AA (`#FAD4C0`/`#111827`, `#80A1C1`/`#111827`, `#9A4A24`/`#FFFFFF`) en `easyeat/frontend/src/hooks/useRestaurantBranding.ts` conforme a `contracts/brand-accent-slots.contract.ts`
- [X] T012 [P] [TDD-GREEN] Migrar `ConnectionStatusBadge` a tokens polimórficos `--ee-warning-bg`/`--ee-warning-fg`/`--ee-warning-border` respetando `prefers-reduced-motion` en `easyeat/frontend/src/components/common/ConnectionStatusBadge.tsx`
- [X] T013 [TDD-GREEN] Ejecutar `npx vitest run tests/diner/bentoBlocksAndOverlays.test.tsx tests/theme/brandLayerIsolation.test.tsx` y confirmar verde total (depende de T010, T011, T012)

**Checkpoint**: Fundaciones Bento, overlays accesibles y fallback neutro de marca listos; las 5 User Stories ya pueden avanzar.

---

## Phase 3: User Story 1 — Acceso público y descubrimiento de establecimientos con estética Bento (Priority: P1) 🎯 MVP
> **GitHub Issue**: [#465](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/465)

**Goal**: Rediseñar las 5 pantallas públicas/de acceso (`/login`, `/register`, `/forgot-password`, `/reset-password`, `/verificar-email`) con tarjeta Bento centrada polimórfica (`--ee-*`) y la pantalla `/descubrimiento` en composición Bento orientada a búsqueda en vivo.

**Independent Test**: Recorrer `/login` (incluyendo deep-link hacia `/mostrador`, `/cocina` y `/admin`), `/register`, `/forgot-password`, `/reset-password`, `/verificar-email` y `/descubrimiento` (con restaurante activo, búsqueda `>= 3` caracteres, carga, sin resultados y error con reintento) verificando contraste WCAG 2.2 AA, polimorfismo y cero regresiones.

### Slice TDD 1 — Seam de acceso público polimórfico y descubrimiento Bento (`FR-004`, `FR-005`)

> **RED FIRST**: Escribir las pruebas de integración de US1 y confirmar fallo inicial antes de migrar páginas y formularios.

- [X] T014 [US1] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/diner/authAndDiscoveryBento.test.tsx` verificando: (1) `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage` y `VerifyEmailPage` renderizan `AuthCardShell` con tokens `--ee-*`, botones accesibles `>= 44px` y `FeedbackState` ante errores/avisos; (2) `/login` con `location.state.from="/mostrador"` hereda `data-theme="mostrador"` sin clases duras `bg-gray-50`/`bg-white`/`bg-amber-600`; y (3) `EstablishmentDiscoveryPage` muestra tarjeta Bento de restaurante activo, bloque héroe de búsqueda (`>= 3` caracteres) con estados de carga (`LoadingSkeleton`), vacío (`EmptyState`) y error (`FeedbackState` con reintento) y grilla Bento de 3 bloques informativos sin degradados; ejecutar `npx vitest run tests/diner/authAndDiscoveryBento.test.tsx` y confirmar fallo inicial
- [X] T015 [P] [US1] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/auth/LoginForm.tsx` y `easyeat/frontend/src/pages/LoginPage.tsx` usando `AuthCardShell`, `Button`, `FeedbackState` e inputs `--ee-*` preservando lógica JWT y deep-links
- [X] T016 [P] [US1] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/auth/RegisterForm.tsx` y `easyeat/frontend/src/pages/RegisterPage.tsx` usando `AuthCardShell`, `Button`, `FeedbackState` e inputs `--ee-*`
- [X] T017 [P] [US1] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/auth/ForgotPasswordForm.tsx` y `easyeat/frontend/src/pages/ForgotPasswordPage.tsx` usando `AuthCardShell`, `Button`, `FeedbackState` e inputs `--ee-*`
- [X] T018 [P] [US1] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/auth/ResetPasswordForm.tsx` y `easyeat/frontend/src/pages/ResetPasswordPage.tsx` usando `AuthCardShell`, `Button`, `FeedbackState` e inputs `--ee-*`
- [X] T019 [P] [US1] [TDD-GREEN] Rediseñar `easyeat/frontend/src/pages/VerifyEmailPage.tsx` usando `AuthCardShell`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton` y `Button`
- [X] T020 [P] [US1] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/catalog/EstablishmentSearchBar.tsx` y `easyeat/frontend/src/pages/EstablishmentDiscoveryPage.tsx` en bloques `BentoCard` (restaurante activo, héroe de búsqueda con estados `LoadingSkeleton`/`EmptyState`/`FeedbackState` y grilla de 3 bloques informativos sin degradados ni `dark:*` aislados)
- [X] T021 [US1] [TDD-GREEN] Ejecutar `npx vitest run tests/diner/authAndDiscoveryBento.test.tsx src/test/catalog/EstablishmentDiscoveryPage.test.tsx src/test/catalog/EstablishmentSearchBar.test.tsx tests/components/LoginForm.test.tsx tests/components/RegisterForm.test.tsx tests/components/ForgotPasswordForm.test.tsx tests/components/ResetPasswordForm.test.tsx tests/components/AdminLogin.test.tsx tests/pages/VerifyEmailPage.test.tsx tests/theme/routeThemeScope.test.tsx` confirmando verde total (depende de T015–T020)

**Checkpoint**: US1 completa y verificable de forma independiente (MVP visual de acceso y descubrimiento listo para commit atómico).

---

## Phase 4: User Story 2 — Catálogo gastronómico, seguridad alimentaria y carrito Bento con branding aislado (Priority: P1)
> **GitHub Issue**: [#466](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/466)

**Goal**: Rediseñar `/catalogo/*` y todos sus componentes de catálogo, seguridad alimentaria y carrito en composición Bento mobile-first, integrando los 3 brand slots del catálogo (`header`, `category`, `cta`) y resolviendo los hallazgos P0, P1 y P2 compatibles del `/impeccable critique` base.

**Independent Test**: Montar `/catalogo` con marca personalizada y neutra verificando aislamiento `--brand-*` en `header`/`category`/`cta`, barra lateral colapsable en escritorio + `AccessibleDrawer` móvil para filtros, `DishCard` operable por teclado con badge `quantityInCart`, alérgenos con texto + ícono `lucide-react` (y píldoras neutras para invitados), `DishDetailModal`, `AddToCartRiskModal`, `CartDrawer` con targets `>= 44×44 px` y `EstablishmentSwitchModal`.

### Slice TDD 2 — Seam de catálogo Bento, seguridad alimentaria, carrito y brand slots (`FR-006`–`FR-009`, hallazgos critique P0–P2)

> **RED FIRST**: Escribir pruebas de integración del catálogo, filtros responsivos, brand slots y accesibilidad antes de refactorizar los componentes.

- [X] T022 [US2] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/diner/catalogAndBrandSlotsBento.test.tsx` verificando: (1) aislamiento `--brand-*` exclusivamente en `data-brand-slot="header"`, `data-brand-slot="category"` y `data-brand-slot="cta"` (tanto con branding neutro Bento como con colores extremos); (2) `ManualFiltersSidebar` colapsable en escritorio y operable en `AccessibleDrawer` en móvil con contador de filtros activos y chips removibles; (3) `DishCard` accesible por teclado (sin `<div onClick>` no enfocable), precio en `font-ee-mono tabular-nums`, badge de `quantityInCart` con `aria-live="polite"`, píldoras neutras de alérgenos para invitados, fallback visual limpio ante imagen de plato o logotipo de local ausente/roto y cero emojis literales en alertas; (4) `DishDetailModal`, `AddToCartRiskModal`, `CartDrawer` y `EstablishmentSwitchModal` con `role="dialog"`, cierre por `Escape`, foco visible `--ee-focus-ring` y controles táctiles `>= 44×44 px`; y (5) ausencia de tarjetas anidadas en `CatalogMasterToggle` y de clases Tailwind v4 (`shadow-2xs`, `bg-linear-to-br`, `backdrop-blur-xs`); ejecutar `npx vitest run tests/diner/catalogAndBrandSlotsBento.test.tsx` y confirmar fallo inicial
- [X] T023 [P] [US2] [TDD-GREEN] Rediseñar alertas, alérgenos y estados de catálogo (`OperatingHoursBanner.tsx`, `EstablishmentBlockingNotice.tsx`, `AllergenWarningBadge.tsx`, `AllergenNeutralPill.tsx`, `EmptyCatalogDietaryState.tsx`, `EmptyFilterResultsNotice.tsx`) en `easyeat/frontend/src/components/catalog/` usando `StatusBadge`, `FeedbackState`, `EmptyState`, tríadas WCAG 2.2 AA e íconos `lucide-react` (cero emojis)
- [ ] T024 [P] [US2] [TDD-GREEN] Rediseñar controles de exploración y filtros (`CatalogSearchFilter.tsx`, `CatalogMasterToggle.tsx` eliminando doble tarjeta, `StickyCategoryNav.tsx` con `data-brand-slot="category"` y `ManualFiltersSidebar.tsx` sin clases Tailwind v4) en `easyeat/frontend/src/components/catalog/`
- [ ] T025 [P] [US2] [TDD-GREEN] Rediseñar `DishCard.tsx` (acción de detalle enfocable por teclado, `font-ee-mono tabular-nums` en precio, badge `quantityInCart`, `AllergenNeutralPill` cuando filtro automático está inactivo, CTA con `data-brand-slot="cta"` `>= 44px` y cero emojis) y `DishDetailModal.tsx` (migrado a `AccessibleModal` con `role="dialog"`/`Escape`) en `easyeat/frontend/src/components/catalog/`
- [ ] T026 [P] [US2] [TDD-GREEN] Rediseñar `EstablishmentSwitchModal.tsx` en `easyeat/frontend/src/components/catalog/` y los cuatro componentes de carrito (`FloatingCartButton.tsx`, `CartDrawer.tsx` usando `AccessibleDrawer` y manteniendo `Vaciar` compatible con tests heredados, `CartItemWithBadge.tsx` con steppers `>= 44×44 px` y cero emojis, `AddToCartRiskModal.tsx` usando `AccessibleModal`) en `easyeat/frontend/src/components/cart/`
- [ ] T027 [US2] [TDD-GREEN] Reestructurar `easyeat/frontend/src/pages/CatalogPage.tsx` en bloques `BentoCard` (cabecera con `data-brand-slot="header"` sin título duplicado, jerarquía `h1`/`h2`/`h3` correcta, sidebar colapsable en escritorio + botón `Filtros (N)` con `AccessibleDrawer` en móvil, sincronización clara de categorías, región `aria-live="polite"` al agregar al carrito y `LoadingSkeleton` Bento) (depende de T023, T024, T025, T026)
- [ ] T028 [US2] [TDD-GREEN] Ejecutar `npx vitest run tests/diner/catalogAndBrandSlotsBento.test.tsx src/test/catalog/ src/test/cart/ tests/pages/CatalogPage.test.tsx tests/context/CartContext.test.tsx tests/components/CartDrawer.test.tsx tests/components/CartItemWithBadge.test.tsx tests/components/FloatingCartButton.test.tsx tests/components/AddToCartRiskModal.test.tsx` confirmando verde total sin debilitar aserciones conductuales (depende de T027)

**Checkpoint**: US2 completa, accesible y verificable de forma independiente (lista para commit atómico).

---

## Phase 5: User Story 3 — Checkout modular Bento y retorno de pago (Priority: P1)
> **GitHub Issue**: [#467](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/467)

**Goal**: Rediseñar `/checkout` en grilla Bento de 2 columnas en escritorio con resumen fijo (`JetBrains Mono`), `BrandThemeProvider` activo para el CTA principal, todos los bloques de contingencia/conflicto en lenguaje Bento, y `/checkout/payment-return` en tarjeta Bento centrada.

**Independent Test**: Montar `/checkout` en escritorio y móvil verificando grilla de 2 columnas con `CheckoutSummary` fijo, selector de modalidad/fecha/franja (`>= 44×44 px`), banners y modales de contingencia (`Escape`, foco) y recorrer `/checkout/payment-return` en estados aprobado, pendiente, rechazado y error.

### Slice TDD 3 — Seam de Checkout Bento de 2 columnas, contingencias y Retorno de pago (`FR-008`, `FR-010`, `FR-011`)

> **RED FIRST**: Escribir pruebas de integración de Checkout y PaymentReturn y confirmar fallo inicial antes de migrar.

- [ ] T029 [US3] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/diner/checkoutAndReturnBento.test.tsx` verificando: (1) `CheckoutPage` envuelta en `BrandThemeProvider` con grilla Bento de 2 columnas en escritorio (`CheckoutSummary` fijo a la derecha, totales en `font-ee-mono tabular-nums`, CTA con `data-brand-slot="cta"`); (2) `FulfillmentModeSelector`, `OperatingDatePicker` y `TimeSlotGrid` con objetivos táctiles `>= 44×44 px` y contraste WCAG 2.2 AA; (3) `ConflictNotificationModal` y `ConcurrencyAlertModal` vía `AccessibleModal` (`Escape`, foco) más `PendingOrderCountdown`, `PendingAccreditationBanner`, `InProcessPaymentPanel` y `PaymentAlertBanner` con tokens `--ee-*`; y (4) `PaymentReturnPage` en tarjeta Bento centrada para estados aprobado, pendiente, rechazado y error; ejecutar `npx vitest run tests/diner/checkoutAndReturnBento.test.tsx` y confirmar fallo inicial
- [ ] T030 [P] [US3] [TDD-GREEN] Rediseñar selectores y resumen de checkout (`FulfillmentModeSelector.tsx`, `OperatingDatePicker.tsx`, `TimeSlotGrid.tsx` con targets `>= 44×44 px`, `CheckoutSummary.tsx` con `BentoCard`, `font-ee-mono tabular-nums` y `data-brand-slot="cta"`) en `easyeat/frontend/src/components/checkout/`
- [ ] T031 [P] [US3] [TDD-GREEN] Rediseñar estados de contingencia y modales de checkout (`PendingOrderCountdown.tsx` con `font-ee-mono tabular-nums`, `PendingAccreditationBanner.tsx`, `InProcessPaymentPanel.tsx`, `PaymentAlertBanner.tsx`, `ConflictNotificationModal.tsx` y `ConcurrencyAlertModal.tsx` usando `AccessibleModal`, `FeedbackState`, `StatusBadge` y `Button`) en `easyeat/frontend/src/components/checkout/`
- [ ] T032 [US3] [TDD-GREEN] Reestructurar `easyeat/frontend/src/pages/CheckoutPage.tsx` con `BrandThemeProvider` (usando `establishmentId` resuelto) y grilla Bento de 2 columnas en escritorio / 1 columna en móvil (depende de T030, T031)
- [ ] T033 [P] [US3] [TDD-GREEN] Rediseñar `easyeat/frontend/src/pages/PaymentReturnPage.tsx` en tarjeta `BentoCard` centrada usando `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `PendingAccreditationBanner` y `Button` (depende de T031)
- [ ] T034 [US3] [TDD-GREEN] Ejecutar `npx vitest run tests/diner/checkoutAndReturnBento.test.tsx src/test/checkout/` confirmando verde total (depende de T032, T033)

**Checkpoint**: US3 completa y verificable de forma independiente (lista para commit atómico).

---

## Phase 6: User Story 4 — Confirmación de pedido y seguimiento en vivo con tarjeta héroe de PIN (Priority: P2)
> **GitHub Issue**: [#468](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/468)

**Goal**: Rediseñar `/checkout/confirmacion/:orderId` y `/pedidos/:orderId/seguimiento` (`/seguimiento/:orderId`) en bloques Bento con `BrandThemeProvider` en cabecera (`confirmation`), estado de conexión polimórfico, progreso por etapas, cuenta regresiva/demora y Tarjeta Héroe Bento de PIN de máxima legibilidad a distancia.

**Independent Test**: Renderizar `OrderConfirmationTrackingPage` en cada estado del ciclo de vida (carga, error con reintento, acreditación pendiente, programado con cuenta regresiva, en preparación, con demora, desconectado, listo para retirar con PIN antifraude y entregado), verificando contraste WCAG 2.2 AA, `data-brand-slot="confirmation"` e integridad de `SecurityPinDisplay` para Mostrador.

### Slice TDD 4 — Seam de confirmación, seguimiento en vivo y Tarjeta Héroe de PIN (`FR-008`, `FR-012`)

> **RED FIRST**: Escribir pruebas de integración de Tracking y Pickup PIN Hero antes de refactorizar.

- [ ] T035 [US4] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/diner/trackingPinHeroBento.test.tsx` verificando: (1) `OrderConfirmationTrackingPage` envuelta en `BrandThemeProvider` con `OrderSuccessGreenBanner` usando `data-brand-slot="confirmation"` (preservando `data-testid="order-success-green-banner"`) y ticket en `font-ee-mono tabular-nums`; (2) `LiveTrackingWidget` con indicador progresivo de etapas, `ConnectionStatusBadge` polimórfico (preservando la última información conocida del pedido y el PIN visible ante desconexión temporal) y bloque de demora accesible (`aria-live="polite"`); (3) `ScheduledSlotCountdown` con cuenta regresiva en `font-ee-mono tabular-nums`; (4) `ReadyForPickupCallout` como Tarjeta Héroe Bento de alto contraste exhibiendo `SecurityPinDisplay` en gran formato (`text-4xl`+) con instrucciones claras; y (5) `DeliveredOrderCallout` Bento con acceso a catálogo/pedidos; ejecutar `npx vitest run tests/diner/trackingPinHeroBento.test.tsx` y confirmar fallo inicial
- [ ] T036 [P] [US4] [TDD-GREEN] Rediseñar `OrderSuccessGreenBanner.tsx` (cabecera Bento con `data-brand-slot="confirmation"`, sin `animate-pulse` continuo ni blur decorativo, preservando `data-testid="order-success-green-banner"`) y `ScheduledSlotCountdown.tsx` (`font-ee-mono tabular-nums`) en `easyeat/frontend/src/components/tracking/`
- [ ] T037 [P] [US4] [TDD-GREEN] Rediseñar `ReadyForPickupCallout.tsx` (Tarjeta Héroe Bento de PIN de alto contraste consumiendo `SecurityPinDisplay.tsx` sin modificar su interfaz compartida con Mostrador) y `DeliveredOrderCallout.tsx` (bloque Bento de cierre) en `easyeat/frontend/src/components/tracking/`
- [ ] T038 [US4] [TDD-GREEN] Rediseñar `easyeat/frontend/src/components/tracking/LiveTrackingWidget.tsx` (progreso por etapas Bento, `ConnectionStatusBadge` polimórfico y bloque accesible de demoras) y `easyeat/frontend/src/pages/OrderConfirmationTrackingPage.tsx` (integración de `BrandThemeProvider`, `LoadingSkeleton` y `FeedbackState` con reintento) (depende de T036, T037)
- [ ] T039 [US4] [TDD-GREEN] Ejecutar `npx vitest run tests/diner/trackingPinHeroBento.test.tsx src/test/tracking/ tests/pages/CounterWorkspacePage.test.tsx` confirmando verde total y cero impacto en Mostrador (depende de T038)

**Checkpoint**: US4 completa y verificable de forma independiente (lista para commit atómico).

---

## Phase 7: User Story 5 — Perfil del Comensal, restricciones dietéticas y Mis pedidos en grilla Bento (Priority: P2)
> **GitHub Issue**: [#469](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/469)

**Goal**: Rediseñar `/perfil` y `/perfil#pedidos` preservando `role="tablist"` como pestañas segmentadas Bento, con grilla Bento de 2 columnas en `Mi perfil` (datos personales + restricciones dietéticas) y grilla Bento dedicada de tarjetas en `Mis pedidos`.

**Independent Test**: Montar `ProfilePage` en escritorio y móvil, alternar entre `Mi perfil` y `Mis pedidos` verificando sincronización `/perfil#pedidos`, editar datos personales, abrir/cerrar `PhotoDeleteConfirmModal` con `Escape`, alternar restricciones dietéticas y recorrer `Mis pedidos` en estados carga, vacío, error y listado con acceso a seguimiento.

### Slice TDD 5 — Seam de Perfil Bento con pestañas segmentadas y grilla de 2 columnas (`FR-013`)

> **RED FIRST**: Escribir pruebas de integración de Perfil y Mis pedidos antes de refactorizar.

- [ ] T040 [US5] [TDD-RED] Escribir suite de integración en `easyeat/frontend/tests/diner/profileBentoTabs.test.tsx` verificando: (1) `role="tablist"` (`Mi perfil` y `Mis pedidos`) estilizado como pestañas segmentadas Bento (`>= 44px`) sincronizadas con `/perfil#pedidos`; (2) grilla Bento de 2 columnas en escritorio (`lg:grid-cols-2`) dentro de `Mi perfil` agrupando `PersonalDataForm` (con `PendingEmailBanner`, `PhotoDeleteConfirmModal` vía `AccessibleModal` y fallback limpio ante avatar ausente o con error de carga) junto a `DietaryRestrictionsSelector` (estados activo/inactivo redundantes más allá del color); y (3) `MyOrdersSection` en grilla Bento de tarjetas con `StatusBadge`, total en `font-ee-mono tabular-nums`, `LoadingSkeleton`, `EmptyState` y `FeedbackState` con reintento; ejecutar `npx vitest run tests/diner/profileBentoTabs.test.tsx` y confirmar fallo inicial
- [ ] T041 [P] [US5] [TDD-GREEN] Rediseñar `PersonalDataForm.tsx`, `PendingEmailBanner.tsx` y `PhotoDeleteConfirmModal.tsx` (migrado a `AccessibleModal`) en `easyeat/frontend/src/components/profile/` usando `BentoCard`, `Button`, `FeedbackState` y tokens `--ee-*`
- [ ] T042 [P] [US5] [TDD-GREEN] Rediseñar `DietaryRestrictionsSelector.tsx` (opciones con indicador visual redundante más allá del color y targets `>= 44px`) y `MyOrdersSection.tsx` (grilla Bento de tarjetas con `StatusBadge`, `font-ee-mono tabular-nums`, `LoadingSkeleton`, `EmptyState` y `FeedbackState`) en `easyeat/frontend/src/components/profile/`
- [ ] T043 [US5] [TDD-GREEN] Reestructurar `easyeat/frontend/src/pages/ProfilePage.tsx` con pestañas segmentadas Bento (`role="tablist"` sincronizado con `#pedidos`), grilla `lg:grid-cols-2` en `Mi perfil`, `LoadingSkeleton` y `FeedbackState` (depende de T041, T042)
- [ ] T044 [US5] [TDD-GREEN] Ejecutar `npx vitest run tests/diner/profileBentoTabs.test.tsx tests/router/AppRoutesProfileCatalog.test.tsx tests/pages/ProfilePage.test.tsx tests/components/MyOrdersSection.test.tsx` confirmando verde total (depende de T043)

**Checkpoint**: US5 completa y verificable de forma independiente (las 5 historias y las 11 pantallas están migradas a Bento).

---

## Phase 8: Polish & Cross-Cutting Concerns
> **GitHub Issue**: [#470](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/470)

**Purpose**: Verificación integral de regresión (`001`–`025`), validación estática, auditoría visual en navegador real (`360/768/1440 px`), auditoría crítica Impeccable (`/impeccable critique` + `/impeccable polish` si surgen ajustes) y confirmación de exclusiones constitucionales.

- [ ] T045 Ejecutar regresión completa de frontend (`npx vitest run`), chequeo de tipos (`npx tsc --noEmit`) y compilación de producción (`npm run build`) en `easyeat/frontend/` verificando 0 errores y 0 regresiones en suites `001`–`025`
- [ ] T046 [P] Verificar cero uso de `Inter`, cero clases Tailwind v4 huérfanas (`shadow-2xs`, `shadow-xs`, `bg-linear-to-br`, `backdrop-blur-xs`, `focus:outline-hidden`), cero emojis residuales en alertas y cero modificaciones bajo `easyeat/backend/` y `easyeat/frontend/src/api/` mediante auditoría estática y `git diff --stat`
- [ ] T047 Ejecutar validación visual en navegador real a `360px`, `768px` y `1440px` sobre las 11 pantallas definidas en `specs/025-diner-bento-redesign/contracts/diner-screens-audit.contract.ts` siguiendo `specs/025-diner-bento-redesign/quickstart.md` (cero desbordamiento horizontal, objetivos táctiles `>= 44×44 px`, contraste WCAG 2.2 AA y transiciones `<= 200ms` con `prefers-reduced-motion`)
- [ ] T048 Ejecutar auditoría crítica post-implementación `/impeccable critique` sobre las vistas centrales de cada historia de usuario —`LoginPage.tsx` y `EstablishmentDiscoveryPage.tsx` (US1), `CatalogPage.tsx` (US2, línea base `19/40`), `CheckoutPage.tsx` (US3), `OrderConfirmationTrackingPage.tsx` (US4) y `ProfilePage.tsx` (US5)— contra `.specify/memory/designs/comensal.DESIGN.md` en modo Operate, verificando mejora sobre la línea base y cero hallazgos bloqueantes P0/P1 (aplicando `/impeccable polish` únicamente si apareciera algún detalle menor y re-verificando tests)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; inicia de inmediato.
- **Foundational (Phase 2)**: Depende de Phase 1 — **BLOQUEA** todas las User Stories (`US1`–`US5`).
- **User Stories (Phases 3–7)**: Todas dependen de Phase 2.
  - `US1` (P1), `US2` (P1), `US3` (P1), `US4` (P2) y `US5` (P2) operan sobre pantallas y componentes disjuntos; pueden ejecutarse secuencialmente en orden de prioridad (`US1 → US2 → US3 → US4 → US5`) o en paralelo una vez completada Phase 2.
  - Nota de coordinación menor: `PendingAccreditationBanner.tsx` se migra en `US3` (`T031`) y es reutilizado en `US4` (`OrderConfirmationTrackingPage.tsx`), por lo que conviene completar `US3` antes de `US4` (orden natural de prioridad P1 → P2).
- **Polish (Phase 8)**: Depende de la finalización de las cinco User Stories (`T001`–`T044`).

### Within Each User Story (TDD obligatorio)

1. Escribir la suite `[TDD-RED]` en `easyeat/frontend/tests/diner/` y confirmar fallo inicial antes de editar código de producción.
2. Implementar componentes hijos marcados `[P]` en paralelo (`[TDD-GREEN]`).
3. Integrar la página principal y ejecutar la suite nueva + suites heredadas del dominio confirmando verde total.
4. Registrar commit atómico de la User Story antes de iniciar la siguiente (excepción documentada de rama única en `spec.md`).

---

## Parallel Opportunities

- **Phase 2**: `T005`, `T006`, `T007`, `T011` y `T012` en paralelo; luego `T008` y `T009` en paralelo tras `T007`.
- **US1**: `T015`, `T016`, `T017`, `T018`, `T019` y `T020` en paralelo tras `T014` (`TDD-RED`).
- **US2**: `T023`, `T024`, `T025` y `T026` en paralelo tras `T022` (`TDD-RED`).
- **US3**: `T030` y `T031` en paralelo tras `T029` (`TDD-RED`).
- **US4**: `T036` y `T037` en paralelo tras `T035` (`TDD-RED`).
- **US5**: `T041` y `T042` en paralelo tras `T040` (`TDD-RED`).

### Parallel Example: User Story 2

```text
# 1. TDD-RED primero:
T022 [US2] [TDD-RED] tests/diner/catalogAndBrandSlotsBento.test.tsx

# 2. TDD-GREEN en paralelo (archivos disjuntos):
T023 [P] [US2] Alertas, alérgenos y estados de catálogo (components/catalog/)
T024 [P] [US2] Búsqueda, CatalogMasterToggle, StickyCategoryNav y ManualFiltersSidebar
T025 [P] [US2] DishCard y DishDetailModal
T026 [P] [US2] EstablishmentSwitchModal y componentes de carrito (components/cart/)

# 3. Integración y validación:
T027 [US2] Reestructurar CatalogPage.tsx
T028 [US2] Ejecutar suite TDD US2 + regresión catálogo/carrito
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (Setup) + Phase 2 (Foundational: bloques Bento, overlays accesibles y `DEFAULT_NEUTRAL_BRANDING`).
2. Completar Phase 3 (`US1`: 5 pantallas públicas/acceso polimórficas + `/descubrimiento` Bento).
3. Validar `US1` de forma independiente (`T021`) y registrar commit atómico.

### Incremental Delivery

1. Setup + Foundational → Base Bento y accesibilidad verificada.
2. `US1` (P1) → Acceso público y Descubrimiento Bento (MVP).
3. `US2` (P1) → Catálogo, alérgenos, carrito y brand slots (resuelve todos los P0/P1/P2 compatibles del critique base).
4. `US3` (P1) → Checkout de 2 columnas y Retorno de pago.
5. `US4` (P2) → Confirmación, seguimiento en vivo y Tarjeta Héroe de PIN.
6. `US5` (P2) → Perfil con pestañas segmentadas Bento y Mis pedidos.
7. Phase 8 → Regresión total `001`–`025`, `tsc`, `build`, validación `360/768/1440 px` y `/impeccable critique` final.

---

## Notes

- **Invariante de alcance**: cero modificaciones en `easyeat/backend/` y `easyeat/frontend/src/api/`; cero modificaciones en pantallas de Mostrador, Chef o Administrador (`026`–`028`).
- **Compatibilidad compartida**: `SecurityPinDisplay.tsx` conserva intacta su interfaz compartida con `CounterTicketCard.tsx`; `Vaciar` en `CartDrawer.tsx` conserva disparo en un clic para no romper aserciones conductuales heredadas.
- **Formato validado**: todas las tareas (`T001`–`T048`) cumplen `- [ ] [TaskID] [P?] [Story?] Description with file path`.
