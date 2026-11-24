# Modelo de datos visual y trazabilidad de interfaz: Rediseño Bento del Comensal (Spec 025)

**Feature**: `specs/025-diner-bento-redesign`  
**Fecha**: 22/11/2026  
**Fuente de verdad de diseño**: `.specify/memory/designs/comensal.DESIGN.md` (remediado en Spec `024`)

---

## 1. Entidades de interfaz (Key Entities)

### 1.1 `BentoCard` (Bloque modular Bento)

Unidad estructural de presentación para agrupar contenido cohesivo sobre el lienzo `--ee-surface` (`#FFF5E6`) sin anidamiento redundante de tarjetas.

| Campo / Prop | Tipo | Regla de diseño / invariante |
|---|---|---|
| `variant` | `'elevated' \| 'subtle' \| 'interactive' \| 'hero'` | `elevated`: fondo `--ee-surface-elevated` (`#FFFFFF`), borde `--ee-border-subtle` (`#D6C2A8`), sombra `--ee-shadow-elevated`. `subtle`: fondo `--ee-surface-subtle` (`#F5E6D0`) para sub-bloques informativos sin sombra (nunca tarjeta dentro de tarjeta con sombra). `interactive`: añade transición `<= 200ms` en borde/sombra con foco visible `--ee-focus-ring`. `hero`: énfasis de alta jerarquía (búsqueda en descubrimiento o tarjeta héroe de PIN). |
| `padding` | `'none' \| 'sm' \| 'md' \| 'lg'` | Mapeado estricto a escala Bento: `0px`, `16px` (`p-4`), `24px` (`p-6`), `32px` (`p-6 sm:p-8`). |
| `radius` | `'md' \| 'lg'` | `--ee-radius-md` (`14px`) o `--ee-radius-lg` (`20px`). |
| `as` | `'div' \| 'section' \| 'article' \| 'aside'` | Semántica HTML apropiada al contexto (landmarks y encabezados ordenados). |

**Invariante anti-anidamiento (Zero AI-Slop)**: Un `BentoCard` con `variant="elevated"` nunca debe contener otro `BentoCard` con `variant="elevated"` ni sombra duplicada; las subdivisiones internas utilizan separadores (`border-ee-border-subtle`) o bloques planos (`bg-ee-surface-subtle`).

---

### 1.2 `Focused Brand Accent Slot` (Punto focal de marca del establecimiento — Spec 016 + FR-008)

Únicas cuatro zonas autorizadas a renderizar variables `--brand-*` emitidas por `BrandThemeProvider`:

| `slotId` | Pantalla(s) | Componente(s) | Propiedades gobernadas por `--brand-*` | Superficies blindadas bajo `--ee-*` |
|---|---|---|---|---|
| `header` | `/catalogo/*` | Cabecera del local en `CatalogPage` | Banda/acento de identidad, borde de acento e insignia del local (`--brand-primary`, `--brand-primary-contrast`, `--brand-secondary`) | Lienzo `#FFF5E6`, tarjetas de plato `#FFFFFF`, textos generales |
| `category` | `/catalogo/*` | `StickyCategoryNav` | Pestaña/píldora de categoría activa (`--brand-primary`, `--brand-primary-contrast`) | Contenedor sticky, pestañas inactivas, barra de búsqueda |
| `cta` | `/catalogo/*`, `/checkout` | CTA principal de agregar/comprar (`DishCard`, `DishDetailModal`, `CartDrawer`, `CheckoutSummary`) | Fondo y texto contrastado del botón principal de conversión (`--brand-primary`, `--brand-primary-contrast`) | Botones secundarios/ghost/danger, alertas, totales |
| `confirmation` | `/checkout/confirmacion/:orderId`, `/pedidos/:orderId/seguimiento`, `/seguimiento/:orderId` | `OrderSuccessGreenBanner` | Cabecera de confirmación del pedido (`--brand-primary`, `--brand-primary-contrast`) | Tarjeta de seguimiento, alertas de demora, tarjeta héroe de PIN |

#### Matriz WCAG 2.2 AA del respaldo neutro `DEFAULT_NEUTRAL_BRANDING` actualizado a Bento

| Par de tokens en `DEFAULT_NEUTRAL_BRANDING` | Fondo | Texto | Contraste WCAG | Estado (min 4.5:1) |
|---|---|---|---|---|
| `primaryColor` / `primaryContrastColor` | `#FAD4C0` | `#111827` | `12.88:1` | PASA (AAA) |
| `secondaryColor` / `secondaryContrastColor` | `#80A1C1` | `#111827` | `6.58:1` | PASA (AA) |
| `accentColor` / `accentContrastColor` | `#9A4A24` | `#FFFFFF` | `5.97:1` | PASA (AA) |

Cuando un establecimiento configura colores propios (incluso extremos como `#FFFFFF`, `#000000` o saturados), `BrandThemeProvider` calcula automáticamente el contraste óptimo vía `getOptimalContrastColor(hex)` (garantizando `>= 4.5:1`).

---

### 1.3 `Pickup PIN Hero Block` (Tarjeta héroe de retiro — FR-012, SC-004)

| Sección anatómica | Fuente de datos | Tipografía y estilo | Regla de accesibilidad |
|---|---|---|---|
| Encabezado de estado listo | `message` (default: `¡Tu pedido está listo para retirar!`) | `DM Sans` 700, `24px`, `--ee-success-fg` sobre `--ee-success-bg` | Contraste `>= 4.5:1` (`#14532D` sobre `#DCFCE7` = `8.68:1`), ícono `CheckCircle` `aria-hidden="true"` |
| Bloque de número de ticket | `ticketNumber` (`#123`) | `JetBrains Mono` 700 `tabular-nums`, `36–48px` sobre `--ee-surface-elevated` | Contraste `16.44:1` (`#111827` sobre `#FFFFFF`) |
| Bloque héroe de PIN antifraude | `securityCode` vía `SecurityPinDisplay` | `JetBrains Mono` 700 `tabular-nums`, `36–48px`, `tracking-[0.25em]`, superficie contrastada `--ee-text` (`#111827`) con texto `--ee-surface` (`#FFF5E6`) o superficie elevada con borde `--ee-success-border` | Contraste `>= 15:1`, legible a distancia de brazo en menos de 2 s (`SC-004`), respaldo `- - - -` preservado |
| Resumen de ítems para corroboración | `items` (`dishName`, `quantity`) | `DM Sans` 500 + cantidad en `JetBrains Mono` | Lista semántica `<ul>` sin anidamiento de sombras |

---

### 1.4 Máquina de estados de overlays accesibles (`AccessibleModal` / `AccessibleDrawer`)

```text
[CLOSED]
   │ (clic/Enter/Espacio en disparador)
   ▼
[OPENING → OPEN]
   - Guarda document.activeElement previo (openerRef)
   - Bloquea scroll del body (con contador para evitar fugas si hay overlays secuenciales)
   - Renderiza role="dialog" + aria-modal="true" + aria-labelledby / aria-label
   - Mueve foco al contenedor o primer control enfocable
   │
   ├── (tecla Escape) ──────────────────────────┐
   ├── (clic en backdrop si dismissible=true) ──┤
   ├── (clic en botón cerrar >= 44×44 px) ──────┤
   └── (acción confirmada / cambio de ruta) ────┤
                                                ▼
[CLOSING → CLOSED]
   - Restaura scroll del body
   - Devuelve foco a openerRef.current (si sigue conectado al DOM)
```

---

## 2. Matriz de trazabilidad: 11 pantallas, componentes y requisitos

| Historia | Pantalla / Ruta | Componentes impactados | Bloques / Primitivos consumidos | Requisitos |
|---|---|---|---|---|
| **US1** | `LoginPage` (`/login`) | `LoginForm` | `AuthCardShell`, `Button`, `FeedbackState` | `FR-001`–`FR-004`, `FR-014`–`FR-016` |
| **US1** | `RegisterPage` (`/register`) | `RegisterForm` | `AuthCardShell`, `Button`, `FeedbackState` | `FR-001`–`FR-004`, `FR-014`–`FR-016` |
| **US1** | `ForgotPasswordPage` (`/forgot-password`) | `ForgotPasswordForm` | `AuthCardShell`, `Button`, `FeedbackState` | `FR-001`–`FR-004`, `FR-014`–`FR-016` |
| **US1** | `ResetPasswordPage` (`/reset-password`, `/auth/reset-password`) | `ResetPasswordForm` | `AuthCardShell`, `Button`, `FeedbackState` | `FR-001`–`FR-004`, `FR-014`–`FR-016` |
| **US1** | `VerifyEmailPage` (`/verificar-email`) | — | `AuthCardShell`, `Button`, `FeedbackState`, `LoadingSkeleton`, `StatusBadge` | `FR-001`–`FR-004`, `FR-014`–`FR-016` |
| **US1** | `EstablishmentDiscoveryPage` (`/descubrimiento`) | `EstablishmentSearchBar` | `BentoCard`, `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState` | `FR-001`–`FR-003`, `FR-005`, `FR-014`–`FR-016` |
| **US2** | `CatalogPage` (`/catalogo/*`) | `OperatingHoursBanner`, `EstablishmentBlockingNotice`, `CatalogSearchFilter`, `CatalogMasterToggle`, `StickyCategoryNav`, `ManualFiltersSidebar`, `DishCard`, `DishDetailModal`, `AllergenWarningBadge`, `AllergenNeutralPill`, `EmptyCatalogDietaryState`, `EmptyFilterResultsNotice`, `EstablishmentSwitchModal`, `FloatingCartButton`, `CartDrawer`, `CartItemWithBadge`, `AddToCartRiskModal`, `BrandThemeProvider` | `BentoCard`, `AccessibleModal`, `AccessibleDrawer`, `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`, Brand Slots (`header`, `category`, `cta`) | `FR-001`–`FR-003`, `FR-006`–`FR-009`, `FR-014`–`FR-016` |
| **US3** | `CheckoutPage` (`/checkout`) | `FulfillmentModeSelector`, `OperatingDatePicker`, `TimeSlotGrid`, `CheckoutSummary`, `PendingOrderCountdown`, `PendingAccreditationBanner`, `InProcessPaymentPanel`, `PaymentAlertBanner`, `ConflictNotificationModal`, `ConcurrencyAlertModal`, `BrandThemeProvider` | `BentoCard`, `AccessibleModal`, `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState`, Brand Slot (`cta`) | `FR-001`–`FR-003`, `FR-008`, `FR-010`, `FR-014`–`FR-016` |
| **US3** | `PaymentReturnPage` (`/checkout/payment-return`) | `PendingAccreditationBanner` | `BentoCard`, `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton` | `FR-001`–`FR-003`, `FR-011`, `FR-014`–`FR-016` |
| **US4** | `OrderConfirmationTrackingPage` (`/checkout/confirmacion/:orderId`, `/pedidos/:orderId/seguimiento`, `/seguimiento/:orderId`) | `OrderSuccessGreenBanner`, `LiveTrackingWidget`, `ScheduledSlotCountdown`, `ReadyForPickupCallout`, `DeliveredOrderCallout`, `PendingAccreditationBanner`, `ConnectionStatusBadge`, `SecurityPinDisplay` (consumo intacto), `BrandThemeProvider` | `BentoCard`, `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, Brand Slot (`confirmation`), Pickup PIN Hero | `FR-001`–`FR-003`, `FR-008`, `FR-012`, `FR-014`–`FR-016` |
| **US5** | `ProfilePage` (`/perfil`, `/perfil#pedidos`) | `PersonalDataForm`, `PendingEmailBanner`, `PhotoDeleteConfirmModal`, `DietaryRestrictionsSelector`, `MyOrdersSection` | `BentoCard`, `AccessibleModal`, `Button`, `StatusBadge`, `FeedbackState`, `LoadingSkeleton`, `EmptyState` | `FR-001`–`FR-003`, `FR-013`–`FR-016` |

---

## 3. Reglas de consistencia tipográfica y numérica (Modo Operate)

- **Encabezados y cuerpo**: `DM Sans` (`font-ee-display` / `font-ee-sans`), pesos `400`, `500`, `600`, `700`.
- **Importes, precios, subtotales, totales, números de ticket/pedido, cuentas regresivas y PIN**: `JetBrains Mono` (`font-ee-mono tabular-nums`).
- **Etiquetas técnicas / metadatos en mayúsculas** (`RESTAURANTE ACTIVO`, `TICKET`, `CÓDIGO DE RETIRO`, `PEDIDO #...`): `JetBrains Mono`, `12px` (`text-xs`), `uppercase tracking-wider`.
- **Duración máxima de micro-transiciones**: `<= 200ms` (`transition-colors duration-150`, `transition-shadow duration-150`, `transition-transform duration-200`), suprimidas automáticamente bajo `@media (prefers-reduced-motion: reduce)` ya declarado en `themes.css`.
