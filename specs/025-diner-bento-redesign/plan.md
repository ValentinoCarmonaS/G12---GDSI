# Plan de implementación: Rediseño visual Bento del flujo integral del Comensal

**Branch**: `feat/025-diner-bento-redesign` | **Date**: 22/11/2026 | **Spec**: [specs/025-diner-bento-redesign/spec.md](spec.md)

**Input**: Especificación funcional de `specs/025-diner-bento-redesign/spec.md`, arquitectura fundacional de `specs/024-ui-theme-architecture/spec.md`, constitución vigente `.specify/memory/constitution.md` (v2.1.1), contrato visual `.specify/memory/designs/comensal.DESIGN.md` y verdad de producto en `PRODUCT.md`.

---

## Summary

La especificación `025` rediseña integralmente las **11 pantallas** del recorrido del Comensal (6 pantallas del flujo Comensal y 5 pantallas públicas/de acceso) bajo la estética modular **Bento** definida en `.specify/memory/designs/comensal.DESIGN.md` y en modo **Operate** (Impeccable), como entrega exclusiva de frontend (`single-tier frontend-only`, autorizada expresamente bajo Principio X), sin modificar backend, contratos Axios ni lógica de negocio de `001`–`023`.

El plan se organiza en seis módulos técnicos trazables:

1. **Bloques base Bento, overlays accesibles y capa neutra de marca (`FR-001`–`FR-003`, `FR-008`, `FR-014`)**: creación de tres piezas reutilizables cohesivas (`BentoCard`, `AuthCardShell` y `AccessibleModal`/`AccessibleDrawer` + `useAccessibleOverlay`) que complementan los cinco primitivos de Spec `024`, endurecimiento polimórfico de `ConnectionStatusBadge` y actualización de `DEFAULT_NEUTRAL_BRANDING` a pares Bento WCAG 2.2 AA (`#FAD4C0`/`#111827`, `#80A1C1`/`#111827`, `#9A4A24`/`#FFFFFF`).
2. **US1 — Acceso público y descubrimiento Bento (`FR-004`, `FR-005`)**: migración de `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `VerifyEmailPage` (y sus formularios) a `AuthCardShell` 100 % impulsado por `--ee-*` (preservando polimorfismo en deep-links de personal en `/login`), y rediseño Bento de `EstablishmentDiscoveryPage` + `EstablishmentSearchBar` orientado al flujo de búsqueda en vivo existente.
3. **US2 — Catálogo gastronómico, seguridad alimentaria y carrito Bento (`FR-006`–`FR-009`)**: rediseño estructural mobile-first de `CatalogPage` y sus componentes de catálogo y carrito, con cabecera de marca (`header`), navegación fija de categorías (`category`), barra lateral colapsable de filtros en escritorio / `AccessibleDrawer` en móvil, grilla modular de platos con alertas de alérgenos redundantes (texto + ícono), `DishDetailModal`, `AddToCartRiskModal`, `FloatingCartButton`, `CartDrawer` y `EstablishmentSwitchModal`.
4. **US3 — Checkout Bento de 2 columnas y retorno de pago (`FR-008`, `FR-010`, `FR-011`)**: activación de `BrandThemeProvider` en `CheckoutPage`, grilla Bento de 2 columnas en escritorio con `CheckoutSummary` fijo en `JetBrains Mono` (`tabular-nums`), columna única en móvil, migración Bento de los bloques de retiro/fechas/franjas/contingencias/modales de conflicto y tarjeta Bento centrada en `PaymentReturnPage`.
5. **US4 — Confirmación y seguimiento en vivo con tarjeta héroe de PIN (`FR-008`, `FR-012`)**: activación de `BrandThemeProvider` en `OrderConfirmationTrackingPage`, cabecera con acento de marca (`confirmation`), insignia polimórfica de conexión en vivo, progreso visual por etapas, cuenta regresiva/demoras y tarjeta héroe Bento de alto contraste para el PIN antifraude (`ReadyForPickupCallout` + `SecurityPinDisplay` intacto).
6. **US5 — Perfil del Comensal y Mis pedidos en grilla Bento (`FR-013`)**: pestañas segmentadas Bento (`role="tablist"`) sincronizadas con `/perfil#pedidos`, grilla Bento de 2 columnas en `Mi perfil` (`PersonalDataForm` + `PendingEmailBanner` + `PhotoDeleteConfirmModal` y `DietaryRestrictionsSelector`) y grilla Bento dedicada de tarjetas en `Mis pedidos` (`MyOrdersSection`).

---

## Technical Context

**Language/Version**: TypeScript 5.6.3, React 18.3.1, CSS3 (Tailwind CSS 3.4.13 con utilidades semánticas `ee-*` de Spec `024`).  
**Primary Dependencies**: `react-router-dom` 6.27.0, `lucide-react` 0.453.0, `axios` 1.7.7 (intacto), `@fontsource/dm-sans` y `@fontsource/jetbrains-mono` (ya instalados y configurados en Spec `024`). Sin nuevas dependencias externas.  
**Storage**: N/A (capa exclusivamente frontend; se preserva el uso existente de `sessionStorage` en `AuthContext`, `TenantContext`, `CartContext` y `CatalogFilterContext` sin cambios de esquema).  
**Testing**: Vitest 2.1.3, React Testing Library 16.0.1, `@testing-library/user-event` 14.5.2, `@testing-library/jest-dom` 6.5.0, JSDOM 25.0.1, `tsc --noEmit`, `vite build`, auditoría visual Impeccable (`/critique`) y verificación en navegador real a `360px`, `768px` y `1440px`.  
**Target Platform**: Navegadores web modernos en móvil (`>= 360px`), tableta (`768px`) y escritorio (`>= 1280px` / `1440px`), servido como SPA estática sobre Nginx.  
**Project Type**: Aplicación web full-stack (`easyeat/backend` + `easyeat/frontend`) con incremento autorizado estrictamente en `easyeat/frontend/`.  
**Performance Goals**: Micro-transiciones `<= 200ms` anuladas bajo `prefers-reduced-motion`, cero dependencias nuevas de bundle, cero desbordamiento horizontal en `360/768/1440 px` y cero FOUC en transiciones de tema o marca.  
**Constraints**: WCAG 2.2 AA estricto (`>= 4.5:1` texto normal, `>= 3.0:1` texto grande/componentes/foco), objetivos táctiles `>= 44×44 px` en acciones principales, política Zero AI-Slop, modo **Operate** sobrio, cero cambios en backend/Axios/reglas de negocio de `001`–`023`, cero cambios en pantallas de Mostrador/Chef/Admin (`026`–`028`).  
**Scale/Scope**: 11 pantallas, ~35 componentes de UI del Comensal/acceso, 3 bloques base reutilizables Bento/overlay, 4 puntos focales de marca (`--brand-*`), 1 actualización de `DEFAULT_NEUTRAL_BRANDING` y cobertura completa de pruebas automatizadas.

---

## Constitution Check

*GATE: Evaluado antes de Phase 0 y reevaluado tras completar Phase 1.*

### Evaluación Pre-Diseño (antes de Phase 0)

| Principio constitucional | Estado | Evidencia / Justificación |
|---|---|---|
| **I. Specification as Source of Truth** | PASA | Toda decisión responde directamente a `US1`–`US5`, `FR-001`–`FR-016` y `SC-001`–`SC-006` de `specs/025-diner-bento-redesign/spec.md`. |
| **II. Specification Before Implementation** | PASA | Spec `025` clarificada vía `grill-me` y `/speckit-clarify`; este paso produce únicamente artefactos de diseño/planificación sin código fuente ni `tasks.md`. |
| **III. Layered Verification Proportional to Risk** | PASA | Se planifican pruebas unitarias (bloques Bento, overlays, brand slots/contraste), pruebas de integración por pantalla (`US1`–`US5`), regresión completa de `001`–`024`, build/typecheck y auditoría visual Impeccable en `360/768/1440 px`. |
| **IV. Simple, Cohesive, and Justified Design** | PASA | Solo se agregan 3 piezas reutilizables (`BentoCard`, `AuthCardShell`, `AccessibleModal`/`AccessibleDrawer`) que eliminan duplicación de accesibilidad y estructura en 11 pantallas y 8 overlays sin añadir librerías externas. |
| **V. Bounded Scope** | PASA | Exclusiones estrictas aplicadas: cero cambios en backend, Axios, hooks de negocio o pantallas de Mostrador/Chef/Admin (`026`–`028`, incluido `AdminBrandingPage.tsx` y `FirstLoginPasswordPage.tsx`). |
| **VI. Controlled Technical Debt** | PASA | Remueve estilos ad-hoc heredados y corrige el contraste del fallback `DEFAULT_NEUTRAL_BRANDING` (`2.54:1` -> `12.88:1`) sin introducir deuda técnica. |
| **VII. Traceability, Consistency, Documentation** | PASA | Cadena completa `spec.md` → `research.md` → `data-model.md` → `contracts/*` → `quickstart.md` → `plan.md`, con `PRODUCT.md` sincronizado vía `/impeccable init`. |
| **VIII. Evidence Over Assumption & Escalation** | PASA | Se inspeccionaron las pantallas, `useRestaurantBranding.ts`, `BrandThemeProvider.tsx`, `SecurityPinDisplay.tsx`, `ConnectionStatusBadge.tsx` y referencias en tests; cada decisión arquitectónica abierta se resolvió con el responsable vía `grill-me`. |
| **IX. Engineering Over Convenience** | PASA | Ratios WCAG 2.2 AA verificados matemáticamente, preservación de `SecurityPinDisplay` compartido con Mostrador y sin atajos en accesibilidad de teclado/foco. |
| **X. Full-Stack Integrity (Vertical Slices)** | PASA (Excepción autorizada) | Excepción `single-tier frontend-only` y trabajo en rama única `feat/025-diner-bento-redesign` con commits atómicos por historia de usuario autorizados expresamente el 22/11/2026 y documentados en `spec.md`. |
| **XI. Role-Scoped Visual Governance & Non-Regressive UI** | PASA | Cumple estrictamente `.specify/memory/designs/comensal.DESIGN.md` (remediado en `024`), política Zero AI-Slop, auditoría Impeccable `/critique` obligatoria y preservación total de contratos Axios, lógica de negocio y aserciones conductuales `001`–`024`. |

### Reevaluación Post-Diseño (tras Phase 1)

- **Aislamiento de capas y compatibilidad**: `DEFAULT_NEUTRAL_BRANDING` actualizado a pares Bento WCAG 2.2 AA mantiene el 100 % de compatibilidad con `brandLayerIsolation.test.tsx`; `SecurityPinDisplay.tsx` mantiene su interfaz exacta para no afectar `CounterTicketCard` (Spec `026`).
- **Polimorfismo en `/login`**: `AuthCardShell` y los formularios de autenticación usan exclusivamente `--ee-*`, garantizando herencia automática de Mostrador, Chef y Administrador en deep-links de personal.
- **Puertas de calidad**: cero `NEEDS CLARIFICATION`, cero violaciones sin justificar, sin hooks `before_plan`/`after_plan` en `.specify/extensions.yml`.

---

## Project Structure

### Documentation (this feature)

```text
specs/025-diner-bento-redesign/
├── spec.md                                 # Especificación funcional clarificada
├── plan.md                                 # Este archivo (plan arquitectónico /speckit-plan)
├── research.md                             # Phase 0: decisiones técnicas D-01 a D-10
├── data-model.md                           # Phase 1: entidades Bento, brand slots, PIN hero y trazabilidad
├── quickstart.md                           # Phase 1: guía de validación ejecutable SC-001 a SC-006
├── contracts/
│   ├── bento-ui-blocks.contract.ts         # Contrato de BentoCard, AuthCardShell y overlays accesibles
│   ├── brand-accent-slots.contract.ts      # Contrato de los 4 brand slots y fallback neutro Bento
│   └── diner-screens-audit.contract.ts     # Manifiesto de auditoría de las 11 pantallas (360/768/1440 px)
└── tasks.md                                # Phase 2 (NO creado en /speckit-plan; generado en /speckit-tasks)
```

### Source Code (repository root)

```text
PRODUCT.md                                  # Actualizado en /impeccable init (v2.1.1 + referencia Spec 025)

easyeat/frontend/src/
├── hooks/
│   └── useRestaurantBranding.ts            # Actualización de DEFAULT_NEUTRAL_BRANDING a pares Bento WCAG 2.2 AA
├── components/
│   ├── diner/                              # NUEVO: bloques base cohesivos de Spec 025
│   │   ├── BentoCard.tsx                   # Superficie modular Bento (elevated/subtle/interactive/hero)
│   │   ├── AuthCardShell.tsx               # Tarjeta centrada polimórfica (--ee-*) para las 5 vistas públicas
│   │   ├── useAccessibleOverlay.ts         # Hook unificado: Escape, foco inicial/retorno, scroll lock
│   │   ├── AccessibleModal.tsx             # Modal accesible WCAG 2.2 AA para diálogos del Comensal
│   │   ├── AccessibleDrawer.tsx            # Panel deslizable accesible (CartDrawer y filtros móviles)
│   │   └── index.ts                        # Barrel export
│   ├── common/
│   │   ├── ConnectionStatusBadge.tsx       # Migrado a tokens --ee-warning-* (polimórfico, reduced-motion)
│   │   ├── SecurityPinDisplay.tsx          # Intacto en contrato (compartido con Mostrador)
│   │   └── BrandThemeProvider.tsx          # Intacto en contrato; reutilizado en Checkout y Tracking
│   ├── auth/                               # US1: LoginForm, RegisterForm, ForgotPasswordForm, ResetPasswordForm
│   ├── catalog/                            # US2: 14 componentes de catálogo, filtros, alérgenos y descubrimiento
│   ├── cart/                               # US2: FloatingCartButton, CartDrawer, CartItemWithBadge, AddToCartRiskModal
│   ├── checkout/                           # US3: 10 componentes de checkout, franjas, contingencias y conflictos
│   ├── tracking/                           # US4: OrderSuccessGreenBanner, LiveTrackingWidget, ScheduledSlotCountdown,
│   │                                       #      ReadyForPickupCallout (Pickup PIN Hero), DeliveredOrderCallout
│   └── profile/                            # US5: PersonalDataForm, PendingEmailBanner, PhotoDeleteConfirmModal,
│                                           #      DietaryRestrictionsSelector, MyOrdersSection
└── pages/
    ├── LoginPage.tsx                       # US1
    ├── RegisterPage.tsx                    # US1
    ├── ForgotPasswordPage.tsx              # US1
    ├── ResetPasswordPage.tsx               # US1
    ├── VerifyEmailPage.tsx                 # US1
    ├── EstablishmentDiscoveryPage.tsx      # US1
    ├── CatalogPage.tsx                     # US2
    ├── CheckoutPage.tsx                    # US3
    ├── PaymentReturnPage.tsx               # US3
    ├── OrderConfirmationTrackingPage.tsx   # US4
    └── ProfilePage.tsx                     # US5

easyeat/frontend/tests/diner/               # NUEVO: pruebas trazables de Spec 025
├── bentoBlocksAndOverlays.test.tsx         # BentoCard, AuthCardShell, AccessibleModal/Drawer (Escape, foco)
├── authAndDiscoveryBento.test.tsx          # US1: 5 vistas públicas + polimorfismo /login staff + Descubrimiento
├── catalogAndBrandSlotsBento.test.tsx      # US2: filtros desktop/móvil, alérgenos, CartDrawer, 4 brand slots
├── checkoutAndReturnBento.test.tsx         # US3: grilla 2-col, BrandThemeProvider, contingencias, payment-return
├── trackingPinHeroBento.test.tsx           # US4: BrandThemeProvider, estados en vivo, Pickup PIN Hero
└── profileBentoTabs.test.tsx               # US5: tablist segmentado Bento, grilla 2-col, Mis pedidos
```

**Structure Decision**: Se mantiene intacta la organización de `easyeat/frontend/src/`, agregando únicamente `src/components/diner/` para los bloques Bento/overlays compartidos por el flujo del Comensal y `tests/diner/` para la cobertura trazable de Spec `025`, sin tocar `easyeat/backend/`, `src/api/` ni pantallas de otros roles.

---

## Architecture & Implementation Blueprint

### Módulo 0 — Fundaciones Bento del Comensal y fallback de marca (`FR-001`–`FR-003`, `FR-008`, `FR-014`)

1. Implementar `BentoCard`, `AuthCardShell`, `useAccessibleOverlay`, `AccessibleModal` y `AccessibleDrawer` en `easyeat/frontend/src/components/diner/` conforme a `contracts/bento-ui-blocks.contract.ts`.
2. Actualizar `DEFAULT_NEUTRAL_BRANDING` en `easyeat/frontend/src/hooks/useRestaurantBranding.ts` con `BENTO_NEUTRAL_BRANDING_COLORS` (`#FAD4C0`/`#111827`, `#80A1C1`/`#111827`, `#9A4A24`/`#FFFFFF`).
3. Migrar `ConnectionStatusBadge.tsx` a tokens `--ee-warning-*` sin `animate-pulse` agresivo (respetando `prefers-reduced-motion`).

### Módulo 1 — US1: Acceso público y descubrimiento Bento (`FR-004`, `FR-005`)

1. Migrar `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `VerifyEmailPage` y los cuatro formularios de `src/components/auth/` a `AuthCardShell`, `Button`, `FeedbackState`, `LoadingSkeleton` e inputs gobernados por `--ee-*` (objetivos táctiles `>= 44px`, foco visible `--ee-focus-ring`).
2. Rediseñar `EstablishmentDiscoveryPage` y `EstablishmentSearchBar` en composición Bento orientada a búsqueda: tarjeta Bento de restaurante activo (cuando existe), bloque héroe Bento de búsqueda (activación desde 3 caracteres) con estados de carga (`LoadingSkeleton`), vacío (`EmptyState`) y error con reintento (`FeedbackState`), y grilla Bento de 3 bloques informativos sin degradados ni tarjetas anidadas.

### Módulo 2 — US2: Catálogo gastronómico, seguridad alimentaria y carrito Bento (`FR-006`–`FR-009`)

1. Reestructurar `CatalogPage` en bloques Bento sobre `BrandThemeProvider`: cabecera del establecimiento (`data-brand-slot="header"`), avisos operativos (`OperatingHoursBanner`, `EstablishmentBlockingNotice`), barra de búsqueda (`CatalogSearchFilter`) y alternador maestro (`CatalogMasterToggle`), navegación fija (`StickyCategoryNav` con `data-brand-slot="category"`).
2. Integrar `ManualFiltersSidebar` como barra lateral Bento colapsable en escritorio (`lg:`) y dentro de `AccessibleDrawer` (`side="left"`) disparado por botón accesible en móvil, mostrando contador de filtros activos.
3. Rediseñar `DishCard`, `DishDetailModal`, `AllergenWarningBadge`, `AllergenNeutralPill`, `EmptyCatalogDietaryState`, `EmptyFilterResultsNotice` y `AddToCartRiskModal` con precios en `font-ee-mono tabular-nums`, advertencias de alérgenos con texto + ícono en contraste WCAG 2.2 AA y CTA principal con `data-brand-slot="cta"`.
4. Migrar `FloatingCartButton`, `CartDrawer`, `CartItemWithBadge` y `EstablishmentSwitchModal` a `AccessibleDrawer` / `AccessibleModal`, objetivos táctiles `>= 44×44 px` en controles de cantidad/cierre y subtotales en `JetBrains Mono`.

### Módulo 3 — US3: Checkout Bento de 2 columnas y Retorno de pago (`FR-008`, `FR-010`, `FR-011`)

1. Envolver `CheckoutPage` en `BrandThemeProvider` con el `establishmentId` resuelto y estructurar su layout en grilla Bento de 2 columnas en escritorio (`lg:grid-cols-[minmax(0,1fr)_380px]`, `CheckoutSummary` fijo a la derecha con totales en `font-ee-mono tabular-nums` y CTA con `data-brand-slot="cta"`) y columna única en móvil.
2. Migrar `FulfillmentModeSelector`, `OperatingDatePicker`, `TimeSlotGrid` (objetivos `>= 44×44 px` y navegación por teclado), `PendingOrderCountdown`, `PendingAccreditationBanner`, `InProcessPaymentPanel`, `PaymentAlertBanner`, `ConflictNotificationModal` y `ConcurrencyAlertModal` a bloques Bento, `AccessibleModal` y primitivos de Spec `024`.
3. Rediseñar `PaymentReturnPage` como tarjeta Bento centrada con estado semántico inequívoco (`StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `Button`).

### Módulo 4 — US4: Confirmación, seguimiento en vivo y Tarjeta Héroe de PIN (`FR-008`, `FR-012`)

1. Envolver `OrderConfirmationTrackingPage` en `BrandThemeProvider` (`order?.establishmentId`) y estructurar la vista en bloques Bento.
2. Rediseñar `OrderSuccessGreenBanner` con `data-brand-slot="confirmation"`, número de ticket en `font-ee-mono tabular-nums` y estética Operate sobria (preservando `data-testid="order-success-green-banner"`).
3. Rediseñar `LiveTrackingWidget` (progreso por pasos, `ConnectionStatusBadge` y avisos de demora accesibles), `ScheduledSlotCountdown`, `DeliveredOrderCallout` y `ReadyForPickupCallout` como **Tarjeta Héroe Bento de PIN** consumiendo `SecurityPinDisplay` sin alterar su contrato compartido con Mostrador.

### Módulo 5 — US5: Perfil del Comensal y Mis pedidos en grilla Bento (`FR-013`)

1. Estilizar el `role="tablist"` de `ProfilePage` como pestañas segmentadas Bento sincronizadas con `/perfil#pedidos`.
2. Organizar la pestaña `Mi perfil` en grilla Bento de 2 columnas en escritorio (`lg:grid-cols-2`, 1 columna en móvil): `PersonalDataForm` + `PendingEmailBanner` + `PhotoDeleteConfirmModal` (`AccessibleModal`) a la izquierda y `DietaryRestrictionsSelector` (estados activo/inactivo redundantes más allá del color) a la derecha.
3. Rediseñar `MyOrdersSection` como grilla Bento dedicada de tarjetas de pedido con `StatusBadge`, total en `font-ee-mono tabular-nums`, `LoadingSkeleton`, `EmptyState` y `FeedbackState` con reintento.

---

## Control estricto de exclusiones

- **Prohibido** modificar cualquier archivo bajo `easyeat/backend/` o `easyeat/frontend/src/api/`.
- **Prohibido** alterar reglas de negocio, estado de hooks de dominio o contratos de datos heredados de `001`–`023`.
- **Prohibido** modificar pantallas o componentes exclusivos de Mostrador, Chef o Administrador (`CounterWorkspacePage`, `KitchenWorkspacePage`, `Chef*`, `Admin*` incluido `AdminBrandingPage`, `FirstLoginPasswordPage`, `FinancialHealthChart`).
- **Prohibido** debilitar aserciones conductuales en suites heredadas de `001`–`024`.

---

## Complexity Tracking

| Elemento | Por qué es necesario | Alternativa más simple rechazada |
|---|---|---|
| Tres bloques base en `src/components/diner/` (`BentoCard`, `AuthCardShell`, `AccessibleModal`/`AccessibleDrawer`) | Evita duplicar lógica crítica de accesibilidad (`Escape`, retorno de foco, scroll lock) en 8 overlays y garantiza polimorfismo limpio `--ee-*` en las 5 vistas públicas + consistencia Bento en las 11 pantallas. | Duplicar clases Tailwind y manejadores de teclado en cada una de las 11 páginas y 8 overlays (alto riesgo de inconsistencias WCAG 2.2 AA). |
| Actualización de `DEFAULT_NEUTRAL_BRANDING` a colores Bento | Elimina un fallo real de contraste WCAG 2.2 AA (`#FFFFFF` sobre `#10B981` = `2.54:1`) y permite que los 4 brand slots consuman siempre `--brand-*` sin ramas condicionales por fallback. | Mantener `#10B981` y añadir condicionales `isDefaultFallback` en los 4 slots (más complejo y conserva un contraste inválido en el fallback). |
