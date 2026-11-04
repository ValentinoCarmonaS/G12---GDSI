# Technical Research & Architectural Decisions: Shopping Cart & Diner Demographics

**Feature Branch**: `009-shopping-cart`  
**Date**: 2026-10-31  
**Status**: Completed  
**Specification Reference**: [spec.md](./spec.md)

---

## Research Summary

This technical research phase establishes the architectural and technical design decisions for the extension of the shopping cart functionality and diner profile demographic management, ensuring strict compatibility with previous specifications (`001-central-auth-recovery` through `008-diner-catalog-multi-tenant`), complete compliance with all 9 business invariants (INV-1 to INV-9), and full satisfaction of all 15 acceptance criteria (15/15).

---

## 1. Local Shopping Cart Session Management & Multi-Tenant Isolation

### Context & Problem Statement
Functional requirements (FR-002, FR-004) and acceptance criteria for COM-6.1.1 and COM-6.1.2 require that the shopping cart reside within the diner's local session, remaining reactive without page reloads, and strictly isolated per active establishment (`tenantId`).

### Technical Decision (D-001)
Reuse and extend the existing `CartContext` infrastructure (`easyeat/frontend/src/context/CartContext.tsx`) backed by `sessionStorage` under key `'easyeat_cart'`, incorporating `tenantId` into the session schema:
1. `sessionStorage` holds a structured serialized object:
   ```typescript
   interface StoredCartSession {
     tenantId: string | null;
     items: CartItem[];
   }
   ```
2. When loading or initializing the context, if the active establishment's `tenantId` (retrieved from `TenantContext`) differs from the stored `tenantId` and items are present, the system triggers the `EstablishmentSwitchModal` confirmation dialog (introduced in Spec 008, COM-1.5.1 AC 5) before purging the previous cart session.
3. Isolation across multiple users on the same machine is preserved by the tab/window scope of `sessionStorage`.

### Rationale
- **Non-Invasive Persistence**: `sessionStorage` survives accidental page reloads (F5) within the same browser tab, but is destroyed when closing the tab/window, exactly matching the semantics of a "diner local session" without cluttering backend databases with unconfirmed abandoned carts.
- **Consistency with Spec 008**: Aligns directly with `TenantContext` and the business rule preventing dishes from different restaurants from being mixed into a single order.

### Alternatives Considered & Rejected
- **Relational Database Storage (`carts` / `cart_items` in PostgreSQL)**: Rejected because the specification explicitly defines the cart as a local session (`sessionStorage`); persisting abandoned carts in the database introduces unnecessary I/O overhead, concurrent locks, and cleanup routines (TTL/cron) for this iteration.
- **`localStorage`**: Rejected because it shares state across multiple tabs, which would cause race conditions and conflicts if a diner browses two different restaurants in parallel tabs.

---

## 2. Quantity Selector in Dish Detail & Additive Accumulation

### Context & Problem Statement
COM-6.1.1 (AC-1.1, FR-001, FR-002, FR-003) requires the dish detail view (`DishDetailModal.tsx`) to provide a numeric quantity selector (defaulting to 1 unit) bounded between 1 and available kitchen stock, allowing diners to add multiple units in a single action without creating duplicate rows for the same dish.

### Technical Decision (D-002)
Modify `DishDetailModal.tsx` (`easyeat/frontend/src/components/catalog/DishDetailModal.tsx`) to:
1. Introduce a local state `selectedQuantity` initialized to 1 upon opening the modal for a given dish.
2. Dynamically determine the upper bound for the selector:
   $$\text{maxSelectable} = \max(0, \text{availableKitchenStock} - \text{quantityAlreadyInCart})$$
3. Disable increment controls if `selectedQuantity >= maxSelectable` or if `maxSelectable == 0`.
4. Extend the `addItem` function in `CartContextType` to accept the signature:
   ```typescript
   addItem: (dish: DishCatalogItem | EnrichedDish, quantity?: number, riskConfirmed?: boolean) => void;
   ```
   If the dish already exists with quantity $k$, the new quantity becomes $k + m$, strictly asserting that $k + m \le \text{availableKitchenStock}$ (INV-3).
5. Display visual success notification feedback (3-second auto-dismissing toast) stating: `"Added {m} unit(s) of {dish.name} to cart"`.

### Rationale
- **Strict Compliance with Agreement**: Resolves the clarification from the 2026-10-31 grill-me session.
- **Front-End Stock Protection**: Prevents diners from attempting to add more units than physically available before sending the items to the cart.

### Alternatives Considered & Rejected
- **Fixed 1-Unit Addition Without Selector**: Rejected because it directly contradicts approved clarification COM-6.1.1 in `spec.md`.

---

## 3. Reactive Floating Cart Button, Drawer & Exact Monetary Precision

### Context & Problem Statement
COM-6.1.2 (AC-2.1 through AC-2.4, FR-005 through FR-010) and INV-6/INV-7 require:
1. Bijective contextual visibility: visible if and only if the cart has $\ge 1$ item and the diner is on catalog screens (`/catalogo`) or dish detail views. Immediately hidden if the cart is cleared or upon navigating to `/perfil`.
2. Continuous display of total item count ($\text{total\_count} = \sum q_i$) and accumulated subtotal ($\text{subtotal} = \sum p_i \times q_i$) without page reload.
3. Exact monetary decimal precision (INV-2) free from floating-point anomalies.

### Technical Decision (D-003)
1. **Component `FloatingCartButton.tsx`**:
   Create `easyeat/frontend/src/components/cart/FloatingCartButton.tsx` located in the bottom-right corner (`fixed bottom-6 right-6 z-40`), styled with smooth transitions (`transition-all duration-300 transform`).
   - Conditionally rendered: `totalCount > 0 && isCatalogView`.
   - Displays a badge with `totalCount` and formatted subtotal with 2-decimal precision.
   - Clicking triggers cart drawer opening (`setIsCartOpen(true)`).
2. **Fixed-Point Decimal Arithmetic (INV-2)**:
   Implement a financial calculation utility `currencyUtils.ts`:
   ```typescript
   export const toCents = (amount: number): number => Math.round((amount + Number.EPSILON) * 100);
   export const fromCents = (cents: number): number => cents / 100;
   export const calculateLineSubtotal = (price: number, quantity: number): number =>
     fromCents(toCents(price) * quantity);
   export const calculateCartTotal = (items: CartItem[]): number =>
     fromCents(items.reduce((acc, item) => acc + toCents(item.price) * item.quantity, 0));
   ```
   In `CartContext`, `totalAmount` is computed using this integer cent arithmetic, guaranteeing strict precision and eliminating drift (e.g. `0.1 + 0.2` or \$12.33 × 3 = \$36.99).
3. **Itemized Drawer (`CartDrawer.tsx`)**:
   Extend `CartDrawer.tsx` (`easyeat/frontend/src/components/cart/CartDrawer.tsx`) to incorporate unitary increment/decrement controls, real-time line subtotal calculations, and a clear cart action.

### Rationale
- **Compliance with INV-1 and INV-2**: Conversion to integer cents eliminates IEEE 754 precision drift.
- **User Ergonomics**: The floating button does not obstruct form submissions on profile pages and remains accessible throughout the catalog ordering journey.

### Alternatives Considered & Rejected
- **Direct Use of JavaScript `number` Math (`price * qty`)**: Rejected because it produces known floating-point errors (e.g. `36.990000000000006`), violating INV-2.
- **Static Header-Only Cart Button**: Rejected because the specification explicitly mandates an on-screen floating action button (`COM-6.1.2`, FR-005).

---

## 4. Quantity Modification, Stock Guardrails & Deterministic Debounced Rollback

### Context & Problem Statement
COM-6.1.3 (AC-3.1 to AC-3.5, FR-011 to FR-016) and INV-3/INV-9 require:
1. Unitary increment/decrement in the drawer. Decrementing on quantity 1 removes the product entirely.
2. Disabling increment when quantity reaches real available kitchen stock.
3. During rapid modifications, applying optimistic UI updates and batching sync with a 300 ms debounce window to the backend. If network fails or stock is rejected, immediately reverting to the last confirmed count and alerting the user.

### Technical Decision (D-004)
1. **Optimistic Updates & Debounce**:
   In `CartContext` or a dedicated hook `useDebouncedCartModifier`, each click on `+` or `-` updates local state immediately while preserving `lastSyncedQuantity`.
2. **Debounce Timer (300 ms)**:
   Upon registering changes, a 300 ms debounce timer is armed. When it elapses, the client executes inventory validation against the catalog service (`/api/v1/establishments/{tenantId}/catalog/availability` or `/api/v1/cart/validate-stock`).
3. **Deterministic Rollback Mechanism**:
   If the network request fails (HTTP 5xx, timeout, connection drop `TypeError: Failed to fetch`) or returns insufficient stock:
   - The item quantity in `CartContext` reverts to `lastSyncedQuantity`.
   - Cart grand total is immediately recalculated.
   - A visible alert toast is triggered: `"Connection error: quantities for {dish.name} were reset"`.
4. **Complete Removal on Quantity 1**:
   Clicking `-` on an item with quantity 1 removes the item from the `items` array. If the cart becomes empty, the drawer displays the empty state and the floating button hides automatically (INV-6).

### Rationale
- **Fluid User Experience**: Diners experience immediate UI responsiveness without button lag or freezing.
- **Data Integrity**: Rollback prevents diners from assuming unavailable stock was reserved or checking out inconsistent quantities.

### Alternatives Considered & Rejected
- **Synchronous Blocking Network Call on Each Click**: Rejected because it would freeze the UI and introduce severe race conditions during rapid clicking.
- **Silently Retaining Modified Quantity on Failure**: Rejected because it violates INV-9 and causes checkout submission failures.

---

## 5. Diner Demographics, Validation & BYTEA Avatar Storage

### Context & Problem Statement
COM-1.4.2 (AC-4.1 to AC-4.6, FR-018 to FR-032) and INV-4, INV-5, INV-8 require:
1. Profile photo: Accepted formats JPEG, PNG, WEBP. Size strictly $> 0$ and $\le 5\text{ MB}$ (5,242,880 bytes).
2. Explicit rejection messages for oversize files or unsupported MIME types.
3. Photo removal with deferred confirmation: confirming the verification dialog updates the preview to provisionally show the default avatar, but server deletion and navbar update consolidate only upon clicking "Save changes".
4. Age: integer between 14 and 120 years inclusive. Strict rejection of negative, decimal, or alphanumeric values.
5. Gender: closed taxonomy ("Female", "Male", "Non-binary", "Prefer not to say").
6. Address: free-form text up to 255 characters, treated strictly as contact data without routing or external logistics.
7. Immediate reflection of updated avatar on top navigation bar (`WorkspaceLayout.tsx`).

### Technical Decision (D-005)
1. **Relational Persistence Model (PostgreSQL - Flyway `V12`)**:
   - Create `user_photos` table identical to the proven pattern from `dish_photos` (`V7`):
     ```sql
     CREATE TABLE IF NOT EXISTS user_photos (
         user_id      UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
         file_name    VARCHAR(255) NOT NULL,
         content_type VARCHAR(50)  NOT NULL,
         file_size    BIGINT       NOT NULL,
         data         BYTEA        NOT NULL,
         created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
         updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
         CONSTRAINT chk_user_photos_content_type
             CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
         CONSTRAINT chk_user_photos_file_size
             CHECK (file_size > 0 AND file_size <= 5242880)
     );
     ```
   - Extend `users` table with domain columns and `CHECK` constraints:
     ```sql
     ALTER TABLE users
         ADD COLUMN IF NOT EXISTS age        INTEGER,
         ADD COLUMN IF NOT EXISTS gender     VARCHAR(50),
         ADD COLUMN IF NOT EXISTS address    VARCHAR(255);

     ALTER TABLE users
         ADD CONSTRAINT chk_users_age_range
             CHECK (age IS NULL OR (age >= 14 AND age <= 120)),
         ADD CONSTRAINT chk_users_gender_valid
             CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY', 'Femenino', 'Masculino', 'No binario', 'Prefiero no responder')),
         ADD CONSTRAINT chk_users_address_length
             CHECK (address IS NULL OR length(address) <= 255);
     ```
2. **Backend Service & Controllers**:
   - Extend `ProfileController.java` and `ProfileService.java` in `com.easyeat.profile`:
     - `PUT /api/v1/profile/demographics`: Transactionally updates `age`, `gender`, `address`.
     - `POST /api/v1/profile/avatar`: Validates size ($\le 5,242,880$ bytes) and magic bytes (JPEG/PNG/WEBP) via `UserPhotoService`, persisting the binary in `user_photos`.
     - `DELETE /api/v1/profile/avatar`: Removes record from `user_photos`.
     - `GET /api/v1/profile/avatar`: Streams binary avatar for authenticated user with `Content-Type` and `Cache-Control` headers.
   - Extend `ProfileResponse` to include `age`, `gender`, `address`, `avatarUrl`.
3. **Deferred Removal Flow in Frontend (`PersonalDataForm.tsx`)**:
   - Form maintains transient state: `pendingPhotoFile`, `removePhotoPending: boolean`, `previewAvatarUrl`.
   - Confirming modal sets `removePhotoPending = true`, `previewAvatarUrl = null`, showing the default avatar.
   - Clicking "Save changes" executes `DELETE /api/v1/profile/avatar` if `removePhotoPending === true`; uploads `pendingPhotoFile` if present; and updates `PUT /api/v1/profile/demographics`.
   - Navigating away without saving discards transient state, preserving the previous avatar.
4. **Navbar Avatar Update in `WorkspaceLayout.tsx`**:
   - `WorkspaceLayout.tsx` consumes user profile from `useProfile()` or `useAuth()`. Renders circular `<img>` if `avatarUrl` exists, or default institutional SVG avatar if null.

### Rationale
- **Architectural Coherence**: Reuses the exact pattern of `DishPhotoService` and `dish_photos` for BYTEA storage and magic byte validation.
- **Database-Level Defense**: PostgreSQL `CHECK` constraints prevent corrupt data persistence even if frontend or controller checks are bypassed.
- **Address Decoupling**: Plain text contact string without external mapping or routing dependencies (INV-8).

### Alternatives Considered & Rejected
- **External S3 / Cloud Bucket Storage**: Rejected because EasyEat has no cloud bucket infrastructure or S3 credentials; relational BYTEA storage (`V7`) is standardized across the codebase.
- **Immediate Avatar Deletion Upon Dialog Confirmation**: Rejected because it violates the specification's deferred confirmation requirement (FR-025).

---

## 6. Coexistence & Compatibility with Previous Specifications

| Specification | Reused Interface / Component | Compatibility Strategy |
|---|---|---|
| **Spec 001 / 002** | `User`, `UserRole`, `RoleRouteGuard`, `SecurityConfig` | Catalog, profile, and cart endpoints remain restricted to `ROLE_COMENSAL`. No changes to authentication or JWT token infrastructure. |
| **Spec 003** | `CartContext`, `AllergenWarningBadge`, `AddToCartRiskModal`, `ProfileResponse` | Cart items retain dietary compliance flags. `ProfileResponse` extended with optional demographic fields without breaking existing consumers. |
| **Spec 004 / 005 / 006** | `DishRecipeIngredient`, `Ingredient`, `dish_photos` | Dish stock calculation relies on existing kitchen BOM inventory without schema modifications. |
| **Spec 007** | `Dish.retail_price` (active approved PVP) | Shopping cart strictly consumes approved retail price (`retail_price`). |
| **Spec 008** | `TenantContext`, `EstablishmentSwitchModal`, `dinerCatalogApi`, `useRealtimeAvailability` | Cart is bound to active `tenantId`. Switching restaurants triggers confirmation modal to flush cart (COM-1.5.1 AC 5). |

---

## Technical Inquiry Resolution Summary

All identified architectural and technical areas have been resolved with concrete evidence from the repository and approved specifications:
- **Pending Clarifications**: 0.
- **Constitution v2.0.0 Alignment**: 100% PASS.
- **Next Phase**: Execution of tasks T020–T043.
