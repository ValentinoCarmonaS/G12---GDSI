# Quickstart & Execution Guide: Chef Dish Technical Files

**Branch**: `005-chef-dish-management` | **Date**: 25/10/2026 | **Specification**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/005-chef-dish-management/spec.md)

This guide describes the end-to-end verifiable scenarios demonstrating the correct operation of the culinary technical file creation and submission workflow for Chefs, as well as the generation of internal alerts for the Administrator.

---

## 1. Prerequisites and environment setup

### 1.1. Database and preceding supplies
1. Active PostgreSQL instance or Testcontainers container available for automated execution.
2. Flyway migrations applied up to `V6__chef_dish_management.sql`.
3. Development seed data applied (`R__seed_dev_users.sql`, `R__seed_dev_dishes_and_dietary.sql`).
4. At least one authenticated Chef user (`chef@easyeat.com`) and one Administrator (`admin@easyeat.com`) in tenant `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`.
5. At least one ingredient in `'ACTIVE'` status in the `ingredients` table (provided by iteration 004).

### 1.2. Startup commands
- **Backend (Spring Boot)**:
  ```bash
  cd easyeat/backend
  mvn spring-boot:run
  ```
- **Frontend (Vite / React)**:
  ```bash
  cd easyeat/frontend
  npm run dev
  ```

---

## 2. End-to-End (E2E) validation scenarios

### Scenario 1: Basic draft creation with photo (CHF-2.2.1)
1. **Action**: The Chef logs in and navigates to `/cocina/platos/nuevo`.
2. **Step 1 of Stepper**:
   - Enters name: `"Ojo de Bife con Papas Rusticas"`.
   - Selects category: `'Platos principales'`.
   - Enters description: `"Corte vacuno a las brasas con hierbas aromaticas"`.
   - Attaches a 2.5 MB JPEG photo.
3. **Verification**:
   - The API responds `201 Created` via `POST /api/v1/dishes`.
   - `POST /api/v1/dishes/{id}/photo` is invoked, persisting the binary in `dish_photos`.
   - Querying `GET /api/v1/dishes/{id}/photo` returns the image bytes with `Content-Type: image/jpeg`.
   - The dish appears in the `/cocina/platos` list with badge `'Borrador'`.

### Scenario 2: Name uniqueness validation and whitespace trimming (CHF-2.2.1)
1. **Action**: The Chef attempts to create another dish with the name `"  Ojo de Bife con Papas Rusticas   "`.
2. **Verification**:
   - The backend trims whitespace (`TRIM`) and detects collision with the existing dish.
   - The API returns `409 Conflict` with a `ProblemDetail` signaling the uniqueness violation.

### Scenario 3: Recipe composition (BOM) and duplicate rejection (CHF-2.2.2)
1. **Action**: In Step 2 of the Stepper:
   - Opens the ingredient selector (filtering only `'ACTIVE'` ingredients).
   - Selects `"Ojo de Bife"` (unit: `'kg'`) and enters `0.400`.
   - Selects `"Papa"` (unit: `'kg'`) and enters `0.300`.
   - Attempts to add `"Ojo de Bife"` again.
2. **Verification**:
   - The interface warns that the ingredient is already present and offers to update the quantity.
   - `PUT /api/v1/dishes/{id}/recipe` is sent.
   - Verification confirms the database stores two records in `dish_recipe_ingredients` with strictly positive quantities.
   - If a quantity of `0` or negative is entered, the endpoint returns `422 Unprocessable Entity`.

### Scenario 4: Allergen declaration and formal confirmation (CHF-2.2.3)
1. **Action**: In Step 3 of the Stepper:
   - Views the taxonomy organized into "Dietary Lifestyles" and "Common Allergens".
   - Marks the warning `CELIAC` (contains gluten) if applicable or leaves zero if compliant.
   - Clicks the explicit button `"Confirmar declaracion de alergenos"`.
2. **Verification**:
   - `PUT /api/v1/dishes/{id}/allergens` is sent with `confirmed = true`.
   - The backend persists the labels in `dish_dietary_restrictions` and sets `dishes.allergens_confirmed = true`.

### Scenario 5: Submission rejection with incomplete recipe or discontinued ingredient (CHF-2.2.4)
1. **Action**: The Chef advances to Step 4 without having added ingredients to the recipe or with an ingredient whose status in `ingredients` changed to `'DISCONTINUED'`.
2. **Verification**:
   - In the interface, the `"Enviar a revision"` button remains visually disabled with a checklist indicating missing items.
   - If a call to `POST /api/v1/dishes/{id}/submit-review` is forced, the backend returns `422 Unprocessable Entity` explicitly enumerating unmet conditions.

### Scenario 6: Successful submission and internal administrative alert generation (CHF-2.2.4)
1. **Action**: With all conditions met (valid name and category, at least 1 active ingredient with positive quantity, and allergens confirmed), the Chef clicks `"Enviar a revision"`.
2. **Verification**:
   - `POST /api/v1/dishes/{id}/submit-review` returns `200 OK` with `status: "PENDING_PRICE"`.
   - A record is inserted in the `admin_notifications` table with `type: 'DISH_SUBMITTED_FOR_REVIEW'` referencing the dish and Chef.
   - The Administrator queries `GET /api/v1/admin/notifications?unreadOnly=true` and observes the new alert.
   - Clicking mark as read (`PATCH /api/v1/admin/notifications/{id}/read`) sets `is_read = true`.

### Scenario 7: Edit locking and immutability in "Pending Price" (CHF-2.2.4)
1. **Action**: With the dish in `PENDING_PRICE` status, the Chef attempts to:
   - Modify name or description (`PUT /api/v1/dishes/{id}`).
   - Delete the dish (`DELETE /api/v1/dishes/{id}`).
   - Change recipe ingredients (`PUT /api/v1/dishes/{id}/recipe`).
   - Modify allergens (`PUT /api/v1/dishes/{id}/allergens`).
   - Upload or delete photos.
2. **Verification**:
   - The interface renders all forms in view-only / disabled mode.
   - Each direct API attempt is rejected with HTTP status code `409 Conflict`.

---

## 3. Automated verification commands

### Backend tests (JUnit / Testcontainers)
```bash
cd easyeat/backend
mvn clean test -Dtest="com.easyeat.dish.**,com.easyeat.catalog.**"
```

### Frontend tests (Vitest / Testing Library)
```bash
cd easyeat/frontend
npm run test
```
