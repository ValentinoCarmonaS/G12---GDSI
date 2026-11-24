# Investigación técnica y decisiones de arquitectura: Rediseño visual Bento del Comensal (Spec 025)

**Feature**: `specs/025-diner-bento-redesign`  
**Fecha**: 22/11/2026  
**Estado**: Completo (sin `NEEDS CLARIFICATION` pendientes)

---

## D-01 — Adherencia estricta al contrato Bento remediado (`comensal.DESIGN.md`) y tokens `--ee-*`

- **Decisión**: Todas las pantallas y componentes del alcance (`11` pantallas y sus componentes dependientes) consumen exclusivamente utilidades conectadas a los tokens semánticos `--ee-*` definidos en `easyeat/frontend/src/styles/themes.css` y mapeados en `easyeat/frontend/tailwind.config.js` (`bg-ee-surface`, `bg-ee-surface-elevated`, `bg-ee-surface-subtle`, `text-ee-text`, `text-ee-text-secondary`, `border-ee-border-subtle`, `border-ee-border-strong`, tríadas `ee-neutral|info|success|warning|danger`, radios `rounded-ee-sm|md|lg`, tipografías `font-ee-display|sans|mono` y sombra `shadow-ee-elevated`).
- **Racional**: Elimina por completo las clases heredadas heterogéneas (`bg-gray-50`, `bg-amber-600`, `bg-emerald-600`, `text-neutral-500`, `dark:*` aislados) y garantiza cumplimiento automático de WCAG 2.2 AA (`16.44:1` texto principal, `7.02:1` texto secundario, `12.88:1` en primario `#FAD4C0`/`#111827`, `8.08:1` foco `#1E40AF`) y de la política Zero AI-Slop (cero `Inter`, cero degradados púrpura-azul, cero tarjetas anidadas innecesarias).
- **Alternativas consideradas**: Mantener clases Tailwind de color fijas adaptadas manualmente a hexadecimales Bento. Rechazada porque rompe el polimorfismo en `/login` para deep-links de personal (`FR-004`) y duplica la fuente de verdad de Spec `024`.

---

## D-02 — Bloques base reutilizables Bento y accesibilidad de overlays

- **Decisión**: Incorporar tres piezas cohesivas y ligeras en `easyeat/frontend/src/components/diner/` (o `common/` para `AuthCardShell` y `AccessibleOverlay`) que complementan los cinco primitivos de Spec `024` (`Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`):
  1. `BentoCard`: superficie elevada (`--ee-surface-elevated`) o sutil (`--ee-surface-subtle`), borde `--ee-border-subtle`, radio `--ee-radius-lg` / `--ee-radius-md`, sombra `--ee-shadow-elevated` y escala de relleno Bento (`16/24/32` px), evitando tarjetas anidadas mediante variantes semánticas (`elevated`, `subtle`, `interactive`, `hero`).
  2. `AuthCardShell`: estructura centrada y polimórfica para las cinco vistas públicas/de acceso (`/login`, `/register`, `/forgot-password`, `/reset-password`, `/verificar-email`), gobernada al 100 % por `--ee-*`.
  3. `AccessibleModal` / `AccessibleDrawer` + hook `useAccessibleOverlay`: comportamiento unificado de accesibilidad para los 8 modales y paneles deslizables del flujo Comensal (`role="dialog"`, `aria-modal="true"`, cierre por `Escape`, cierre por clic en backdrop, bloqueo de scroll en `body`, gestión de foco inicial y retorno de foco al elemento activador al cerrar).
- **Racional**: Reduce duplicación de lógica de accesibilidad crítica (foco, `Escape`, scroll lock) en 8 overlays distintos y asegura ritmo visual Bento uniforme en 11 pantallas sin introducir librerías externas adicionales ni sobre-ingeniería (Principio IV).
- **Alternativas consideradas**:
  - Estilar cada modal y pantalla directamente con clases inline repetidas: rechazada por riesgo de inconsistencias de foco/Escape en los 8 overlays.
  - Agregar una dependencia externa de headless UI (`@headlessui/react` o `@radix-ui/react-dialog`): rechazada para evitar cambios de dependencias innecesarios y mantener total control sobre `data-testid` y pruebas existentes de RTL.

---

## D-03 — Puntos focales de marca (`FR-008`) y actualización de `DEFAULT_NEUTRAL_BRANDING` a Bento

- **Decisión**:
  1. Aplicar `--brand-primary` y `--brand-primary-contrast` (junto con `--brand-secondary` / `--brand-accent` cuando corresponda) de forma uniforme en los cuatro puntos focales designados (`data-brand-slot="header|category|cta|confirmation"`): cabecera/banner del local en Catálogo, indicador de categoría activa en `StickyCategoryNav`, botón principal de compra/agregar y cabecera de confirmación en `OrderSuccessGreenBanner`.
  2. Actualizar `DEFAULT_NEUTRAL_BRANDING` en `easyeat/frontend/src/hooks/useRestaurantBranding.ts` a pares Bento neutros verificados por WCAG 2.2 AA:
     - `primaryColor: '#FAD4C0'`, `primaryContrastColor: '#111827'` (`12.88:1`, cumple AAA)
     - `secondaryColor: '#80A1C1'`, `secondaryContrastColor: '#111827'` (`6.58:1`, cumple AA)
     - `accentColor: '#9A4A24'`, `accentContrastColor: '#FFFFFF'` (`5.97:1`, cumple AA)
  3. Mantener intacto `AdminBrandingPage.tsx` (fuera de alcance; corresponde a Spec `028`).
- **Racional**: La inspección de código (`Select-String`) confirmó que ninguna prueba heredada de `001`–`024` codifica `#10B981` literalmente; `tests/theme/brandLayerIsolation.test.tsx` compara dinámicamente contra `DEFAULT_NEUTRAL_BRANDING.primaryColor`. Además, el respaldo heredado (`#FFFFFF` sobre `#10B981`) presentaba un contraste de `2.54:1` (fallo WCAG 2.2 AA). La actualización corrige ese defecto de contraste, garantiza coherencia Bento en establecimientos sin personalización y simplifica los cuatro slots de marca para consumir siempre `--brand-*` sin ramas condicionales por `isDefaultFallback`.
- **Alternativas consideradas**: Dejar `DEFAULT_NEUTRAL_BRANDING` en esmeralda `#10B981` y ramificar cada slot según `isDefaultFallback`. Descartada por decisión explícita del responsable del proyecto para mantener un contrato único de `--brand-*` y corregir el contraste del fallback neutro.

---

## D-04 — Activación de `BrandThemeProvider` en Checkout y Confirmación/Seguimiento

- **Decisión**: Reutilizar `BrandThemeProvider` (Spec `016`, intacto en contrato) envolviendo el contenido de `CheckoutPage` (pasando el `establishmentId` ya resuelto desde `searchParams`, `items[0]?.tenantId` o `sessionStorage`) y de `OrderConfirmationTrackingPage` (pasando `order?.establishmentId` una vez cargado o el tenant activo).
- **Racional**: `BrandThemeProvider` ya aísla las variables `--brand-*` en `[data-brand-layer="establishment"]`, limpia los estilos al desmontar y recurre automáticamente a `DEFAULT_NEUTRAL_BRANDING` cuando `establishmentId` está ausente o falla la red. No requiere endpoints nuevos ni cambios de API.
- **Alternativas consideradas**: Duplicar llamadas a `useRestaurantBranding` en cada página. Rechazada por duplicar lógica de limpieza y contraste ya verificada en `BrandThemeProvider`.

---

## D-05 — Pantallas públicas y de acceso con soporte nativo para deep-links de personal

- **Decisión**: Migrar `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage` y `VerifyEmailPage` (junto con `LoginForm`, `RegisterForm`, `ForgotPasswordForm`, `ResetPasswordForm`) a `AuthCardShell` + primitivos `Button` y `FeedbackState` + campos de formulario estilizados exclusivamente con `--ee-*`.
- **Racional**: `ThemeScopeProvider` (Spec `024`) ya aplica `data-theme="mostrador|chef|admin"` en `/login` cuando recibe `location.state.from` hacia una ruta de personal. Al eliminar colores duros (`bg-gray-50`, `bg-white`, `bg-amber-600`) y usar exclusivamente `--ee-*`, la tarjeta centrada se adapta automáticamente al tema oscuro de Mostrador, brutalista de Chef o glassmórfico de Administrador sin lógica condicional por rol.
- **Alternativas consideradas**: Crear layouts separados por rol para `/login`. Rechazada porque viola el polimorfismo de Spec `024` (`FR-016`) y agrega código innecesario.

---

## D-06 — Catálogo Bento mobile-first, seguridad alimentaria y filtros responsivos

- **Decisión**:
  - Organizar `CatalogPage` en bloques Bento diferenciados: bloque de cabecera de establecimiento (`data-brand-slot="header"`), avisos operativos (`OperatingHoursBanner`, `EstablishmentBlockingNotice`), barra de búsqueda + `CatalogMasterToggle`, `StickyCategoryNav` (`data-brand-slot="category"`), layout principal de 2 zonas en escritorio (barra lateral colapsable de `ManualFiltersSidebar` + grilla modular de `DishCard`) y panel deslizable accesible (`AccessibleDrawer`) en móvil para filtros manuales.
  - En seguridad alimentaria (`AllergenWarningBadge`, `AllergenNeutralPill`, `AddToCartRiskModal`, `EmptyCatalogDietaryState`), usar las tríadas WCAG 2.2 AA (`--ee-danger-*`, `--ee-warning-*`, `--ee-neutral-*`) con ícono + etiqueta textual obligatoria (nunca solo color).
  - En carrito (`FloatingCartButton`, `CartDrawer`, `CartItemWithBadge`, `EstablishmentSwitchModal`), usar `AccessibleDrawer` / `AccessibleModal`, cifras tabulares en `font-ee-mono tabular-nums`, objetivos táctiles `>= 44×44 px` en controles de cantidad/cierre y `data-brand-slot="cta"` en el CTA principal de agregar/comprar.
- **Racional**: Cumple `FR-006`, `FR-007`, `FR-008`, `FR-009` y `SC-003`/`SC-004`, resolviendo la sobrecarga visual actual de `CatalogPage` (760 líneas con estilos mezclados) y preservando intactos todos los hooks (`useCatalog`, `useCart`, `useRealtimeAvailability`, `useCatalogFilter`) y `data-testid` existentes.
- **Alternativas consideradas**: Mantener los filtros manuales apilados siempre sobre el catálogo en móvil. Rechazada porque empuja los platos fuera del primer viewport en pantallas de `360px` e incumple la especificación (`FR-006`).

---

## D-07 — Checkout Bento de 2 columnas, contingencias y Retorno de pago

- **Decisión**:
  - Estructurar `CheckoutPage` en grilla Bento de 2 columnas en escritorio (`lg:grid-cols-[minmax(0,1fr)_380px]`, columna izquierda con bloques `FulfillmentModeSelector`, `OperatingDatePicker` + `TimeSlotGrid`, y medio de pago; columna derecha fija `lg:sticky lg:top-24` con `CheckoutSummary` y totales en `font-ee-mono tabular-nums`), colapsando a columna única en móvil.
  - Migrar todos los estados de contingencia (`PendingOrderCountdown`, `PendingAccreditationBanner`, `InProcessPaymentPanel`, `PaymentAlertBanner`, `ConflictNotificationModal`, `ConcurrencyAlertModal`) a `BentoCard`, `FeedbackState`, `StatusBadge`, `Button` y `AccessibleModal`.
  - Migrar `PaymentReturnPage` a tarjeta Bento centrada dentro de `WorkspaceLayout`, utilizando `StatusBadge`, `FeedbackState`, `LoadingSkeleton` y `Button`.
- **Racional**: Da jerarquía inmediata al resumen económico y reduce errores al seleccionar modalidad/franja horaria (`FR-010`, `FR-011`). Preserva intactos `useCheckoutFulfillment`, `useTimeSlotPolling`, `orderApi`, `paymentApi` y `checkoutApi`.
- **Alternativas consideradas**: Flujo wizard paso a paso en Checkout. Rechazada porque alteraría el flujo de interacción esperado por las pruebas conductuales existentes (`CheckoutPage.test.tsx`, `CheckoutPaymentFlow.test.tsx`).

---

## D-08 — Confirmación, seguimiento en vivo y Tarjeta Héroe de PIN (`FR-012`)

- **Decisión**:
  - Rediseñar `OrderConfirmationTrackingPage` y `components/tracking/*` (`OrderSuccessGreenBanner`, `LiveTrackingWidget`, `ScheduledSlotCountdown`, `ReadyForPickupCallout`, `DeliveredOrderCallout`) en bloques Bento.
  - Mantener el nombre de archivo y `data-testid="order-success-green-banner"` por compatibilidad con pruebas heredadas, rediseñando su superficie como cabecera Bento con `data-brand-slot="confirmation"`, número de ticket en `font-ee-mono tabular-nums` y sin `animate-pulse` continuo ni blur decorativo excesivo (modo Operate).
  - Convertir `ConnectionStatusBadge.tsx` a tokens `--ee-warning-*` (y suprimir `animate-pulse` bajo `prefers-reduced-motion`) para que sea 100 % polimórfico y WCAG 2.2 AA.
  - Elevar `ReadyForPickupCallout` a **Tarjeta Héroe Bento de PIN** (`Pickup PIN Hero Block`) con contraste reforzado (`--ee-success-bg` / `--ee-success-fg` / `--ee-success-border` y contenedor de PIN de alto contraste con `SecurityPinDisplay` en `text-4xl sm:text-5xl font-ee-mono`), manteniendo intacto el contrato de `SecurityPinDisplay.tsx` (compartido con `CounterTicketCard` de Mostrador).
- **Racional**: Garantiza reconocimiento del estado y del PIN en menos de 2 segundos (`SC-004`) sin romper la compatibilidad con el rol Mostrador ni los 9 archivos de pruebas de `src/test/tracking/`.
- **Alternativas consideradas**: Renombrar `OrderSuccessGreenBanner.tsx` o cambiar sus `data-testid`. Rechazada para preservar cero regresiones en pruebas heredadas (`FR-016`).

---

## D-09 — Perfil del Comensal con pestañas segmentadas Bento y grilla de 2 columnas (`FR-013`)

- **Decisión**:
  - Mantener en `ProfilePage` el `role="tablist"` (`Mi perfil` y `Mis pedidos`) sincronizado con `/perfil#pedidos`, estilizado como control segmentado Bento (`bg-ee-surface-subtle`, pestaña activa en `bg-ee-surface-elevated` con borde `--ee-border-strong` y sombra sutil, objetivo táctil `>= 44px`).
  - Dentro de `Mi perfil`: grilla Bento de 2 columnas en escritorio (`lg:grid-cols-2`, 1 columna en móvil) con `BentoCard` de identidad/datos personales (`PersonalDataForm` + `PendingEmailBanner` + `PhotoDeleteConfirmModal` vía `AccessibleModal`) a la izquierda y `BentoCard` de `DietaryRestrictionsSelector` a la derecha.
  - Dentro de `Mis pedidos`: grilla Bento dedicada (`grid-cols-1 md:grid-cols-2`) de tarjetas en `MyOrdersSection` con `StatusBadge`, total en `font-ee-mono tabular-nums`, `LoadingSkeleton`, `EmptyState` y `FeedbackState` con reintento.
- **Racional**: Cumple `FR-013` y preserva intactas todas las pruebas de perfil (`ProfilePage`, `PersonalDataForm`, `DietaryRestrictionsSelector`, `MyOrdersSection`, `AppRoutesProfileCatalog.test.tsx`).
- **Alternativas consideradas**: Mostrar perfil y pedidos simultáneamente sin pestañas. Rechazada por decisión explícita en `Clarify` (`spec.md`) para preservar el contrato `role="tablist"` y `/perfil#pedidos`.

---

## D-10 — Disciplina Impeccable modo Operate y estrategia de pruebas no regresivas

- **Decisión**:
  - Todas las vistas del Comensal siguen el modo **Operate**: escaneabilidad inmediata, `font-ee-mono tabular-nums` en importes, subtotales, cuentas regresivas, números de ticket y PIN, micro-transiciones `<= 200ms` (`duration-150` / `duration-200`) anuladas por `@media (prefers-reduced-motion: reduce)`, cero ornamentos superfluos y cero tarjetas anidadas innecesarias.
  - Estrategia de verificación por capas (Principio III y XI):
    1. Suite heredada completa Vitest + RTL (`001`–`024`) sin debilitar aserciones conductuales (solo actualización acotada si alguna aserción estuviera acoplada a una clase CSS obsoleta).
    2. Nuevas pruebas de contrato y accesibilidad de Spec `025` cubriendo bloques Bento (`BentoCard`, `AuthCardShell`, `AccessibleModal`/`AccessibleDrawer`), los 4 brand slots con branding neutro y extremo, las 11 pantallas en sus estados clave y la verificación de contraste WCAG 2.2 AA.
    3. Validación estática (`tsc --noEmit`, `npm run build`) y auditoría visual Impeccable (`/critique`) en navegador real a `360px`, `768px` y `1440px`.
- **Racional**: Alinea directamente el plan con `SC-001`–`SC-006` y la Constitución v2.1.1 (Principios III y XI).
- **Alternativas consideradas**: Omitir pruebas específicas de los nuevos bloques Bento y confiar solo en pruebas heredadas. Rechazada porque no garantizaría cobertura trazable de `FR-001`–`FR-016`.
