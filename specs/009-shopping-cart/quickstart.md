# Quickstart & End-to-End Validation Guide: Shopping Cart & Diner Demographics

**Feature Branch**: `009-shopping-cart`  
**Date**: 2026-10-31  
**Status**: Completed  
**Specification Reference**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [contracts/openapi.yaml](./contracts/openapi.yaml)

---

## 1. Prerequisites & Environment Setup

### 1.1. Technical Prerequisites
- Java 21 JDK installed and configured in PATH.
- Node.js 18+ and npm installed.
- Docker and Docker Compose active (for local PostgreSQL 16 database with Flyway).
- Git repository checked out on branch `009-shopping-cart`.

### 1.2. Service Initialization
```bash
# 1. Start local PostgreSQL database container
cd easyeat/backend
docker compose up -d postgres

# 2. Build backend and run Flyway migrations (including V12)
./mvnw clean compile flyway:migrate

# 3. Start Spring Boot backend server (port 8080)
./mvnw spring-boot:run

# 4. In a separate terminal, install dependencies and start frontend SPA (port 5173 or 3000)
cd ../frontend
npm install
npm run dev
```

---

## 2. End-to-End Validation Scenarios

### Scenario 1: Quantity Selector in Dish Detail & Add to Cart (COM-6.1.1)
*Objective*: Validate quantity selector in dish detail view, local session addition, and visible toast notification (AC-1.1, AC-1.2, FR-001, FR-002, SC-001).

1. Log in as diner (`comensal@easyeat.com` / `Password123!`).
2. Select an active establishment on `/descubrimiento` and navigate to `/catalogo`.
3. Click on a dish card with available stock (e.g., "Milanesa Napolitana").
4. **Visual Verification**: In the detail modal (`DishDetailModal`), verify the numeric quantity selector defaulting to `1` with `+` and `-` buttons.
5. Set the selector to `3` units and click "Add to Cart".
6. **Expected Result**:
   - A visible success toast is displayed confirming the addition of 3 units.
   - The floating cart button appears in the bottom-right corner showing `3` items and the exact subtotal (INV-1, INV-2, INV-7).
   - Opening the same dish and adding `2` more units increments the total to `5` without duplicating line items (FR-003).

---

### Scenario 2: Reactive Floating Cart Button & Exact Monetary Totals (COM-6.1.2)
*Objective*: Verify bijective contextual visibility of the floating button, exact penny currency arithmetic, and detailed drawer rendering (AC-2.1 to AC-2.4, INV-2, INV-6, INV-7, SC-002, SC-007).

1. With items in the cart, navigate between different catalog categories.
2. **Visual Verification**: The floating button remains visible with updated total item count and exact subtotal (e.g. `5 items | $45.50`).
3. Navigate to `/perfil`.
4. **Visual Verification**: The floating button hides immediately upon entering the profile view.
5. Return to `/catalogo` and click the floating button.
6. **Expected Result**:
   - The cart drawer (`CartDrawer`) slides open displaying the full order breakdown: dish name, unit retail price, line subtotal, and consolidated total.
   - Subtotals strictly match $\text{unit\_price} \times \text{quantity}$ without floating-point rounding drift (INV-2).

---

### Scenario 3: Modify Quantities, Stock Guardrails & Network Rollback (COM-6.1.3)
*Objective*: Validate unitary increment/decrement, visual block on kitchen stock limit, complete removal on quantity 1, and debounced rollback on network drop (AC-3.1 to AC-3.5, INV-3, INV-9, SC-003, SC-004).

1. In the open cart drawer, click `+` on an item.
2. **Verification**: Quantity increases by 1 and totals recalculate immediately.
3. Click `+` until reaching the available kitchen stock.
4. **Verification**: The `+` control disables visually and further clicks are ignored, displaying an inventory limit indicator (INV-3).
5. Click `-` until reaching quantity 1, then click `-` once more.
6. **Verification**: The item is completely removed from the cart without leaving 0 or negative quantities (FR-015).
7. If the cart is now empty, the drawer reflects an empty state and the outer floating button hides immediately (INV-6, SC-009).
8. Add an item, open developer tools Network tab, simulate "Offline", and click `+`.
9. **Expected Result**: The counter updates optimistically, but upon the 300 ms debounce timeout failing, the counter deterministically reverts to the last confirmed count with an error alert toast (INV-9).

---

### Scenario 4: Edit Diner Demographics & Avatar Upload (COM-1.4.2)
*Objective*: Validate demographic fields (age 14..120, gender taxonomy, address $\le 255$) and avatar upload up to 5 MB with immediate navbar reflection (AC-4.1 to AC-4.4, INV-4, INV-5, SC-005, SC-006).

1. Navigate to `/perfil` and locate the demographic editing section.
2. Test age validation:
   - Enter `12` or `125` $\to$ field is marked invalid with an inline error message (INV-5).
   - Enter `28` $\to$ field is marked valid.
3. Select gender (`"Non-binary"` or `"Femenino"`) and enter contact address (e.g., `"Av. Paseo Colón 850, CABA"`).
4. Test profile photo upload:
   - Select a `.pdf` file $\to$ blocked immediately with invalid MIME format error (FR-020).
   - Select a 6 MB image $\to$ blocked with size exceeded error (FR-022, INV-4).
   - Select a valid JPEG/PNG image of 1.5 MB $\to$ preview renders immediately.
5. Click "Save changes".
6. **Expected Result**:
   - Form saves successfully via `PUT /api/v1/profile/demographics` and `POST /api/v1/profile/avatar`.
   - The top navigation bar (`WorkspaceLayout`) reactively reflects the new avatar image without page reload (SC-008).
   - Verify PostgreSQL database:
     ```bash
     docker exec -it easyeat-postgres psql -U postgres -d easyeat_db -c "SELECT u.age, u.gender, u.address, p.file_size, p.content_type FROM users u LEFT JOIN user_photos p ON u.id = p.user_id WHERE u.email = 'comensal@easyeat.com';"
     ```

---

### Scenario 5: Deferred Avatar Deletion (COM-1.4.2 AC-4.5)
*Objective*: Validate confirmation dialog and deferred deletion on form submission (FR-024, FR-025, SC-008).

1. In `/perfil`, click the "Delete photo" button on the custom avatar.
2. **Verification**: A verification modal (`PhotoDeleteConfirmModal`) appears.
3. Confirm deletion in the modal.
4. **Verification**: Form preview provisionally displays the default institutional avatar.
5. Cancel the form or navigate to `/catalogo` without clicking "Save changes".
6. Return to `/perfil` $\to$ previous custom photo is preserved intact.
7. Repeat the delete flow, confirm in modal, and click "Save changes".
8. **Expected Result**:
   - `DELETE /api/v1/profile/avatar` executes on the backend.
   - The default SVG avatar is restored across the navbar and profile header (SC-008).

---

## 3. Automated Test Verification Commands

```bash
# 1. Run all backend slice, contract, and migration tests for profile and cart
cd easyeat/backend
mvn test -Dtest="DemographicsAvatarMigrationTest,ProfileControllerTest,UserPhotoServiceTest,DemographicsValidationTest,GlobalExceptionHandlerProfileTest,JwtAuthenticationFilterTest"

# 2. Run all frontend unit and component tests
cd ../frontend
npm test -- PersonalDataForm.test.tsx PhotoUploadAndRemoval.test.tsx --run

# 3. Verify TypeScript compilation
npx tsc --noEmit
```
