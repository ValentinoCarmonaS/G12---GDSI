# Tasks: Shopping Cart Management & Diner Profile Demographics

**Input**: Design documents from `/specs/009-shopping-cart/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `quickstart.md`)  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `data-model.md`, `contracts/openapi.yaml`, `contracts/behavioral-contracts.md`  
**Tests**: Strict TDD approach requested by user prompt and Constitution v2.0.0. Tests are written and verified failing before implementation.  
**Organization**: Tasks are grouped by User Story ([US1], [US2], [US3], [US4]) to enable independent implementation, testing, and delivery.

## Format: `- [ ] [ID] [P?] [Story?] Description with exact file path`

- **[P]**: Can run in parallel (different files, no direct dependencies on incomplete tasks)
- **[Story]**: User Story label (`[US1]`, `[US2]`, `[US3]`, `[US4]`) - REQUIRED for User Story phases only

---

## Phase 1: Foundational Infrastructure & Schema (refs #187)

**Purpose**: Core database migrations, JPA entity mappings, and repositories that profile demographics depend on.

**⚠️ CRITICAL**: Blocking prerequisite for backend profile demographic persistence.

- [X] T001 [P] Create Flyway migration `V10__extend_user_demographics_and_avatar.sql` in `easyeat/backend/src/main/resources/db/migration/` adding `age`, `gender`, `address` columns to `users` table with CHECK constraints and creating `user_photos` table with BYTEA data and size constraint
- [X] T002 [P] Create migration test `FlywayMigrationV10Test.java` in `easyeat/backend/src/test/java/com/easyeat/profile/migration/` validating Flyway V10 execution and database CHECK constraints on PostgreSQL
- [X] T003 [P] Implement `Gender.java` enum in `easyeat/backend/src/main/java/com/easyeat/profile/domain/` with standard values `FEMALE`, `MALE`, `NON_BINARY`, and `PREFER_NOT_TO_SAY`
- [X] T004 Extend `User.java` domain entity in `easyeat/backend/src/main/java/com/easyeat/auth/domain/` with `age`, `gender`, and `address` persistent fields
- [X] T005 [P] Implement `UserPhoto.java` JPA entity in `easyeat/backend/src/main/java/com/easyeat/profile/domain/` mapping `user_photos` table with `userId`, `data`, `contentType`, `fileSize`, `fileName`, and timestamps
- [X] T006 [P] Implement `UserPhotoRepository.java` interface in `easyeat/backend/src/main/java/com/easyeat/profile/repository/` providing photo persistence and lookup methods

**Checkpoint**: Core persistence layer ready. Demographic and avatar features can now be implemented.

---

## Phase 2: User Story 4 - Diner Profile Demographics & Avatar Management (COM-1.4.2) (Priority: P2) (refs #188)

**Goal**: Permitir al comensal editar edad (14-120), género, domicilio de contacto (hasta 255 caracteres, aislado sin ruteo) y foto de perfil (JPEG/PNG/WEBP <= 5 MB), reflejándose el avatar en el navbar y permitiendo eliminación diferida con diálogo de confirmación.

**Independent Test**: Subir foto válida (<= 5 MB) y confirmar reflejo inmediato en navbar; intentar subir archivo > 5 MB o formato no admitido y validar rechazo; ingresar edades fuera de 14-120 y validar rechazo; confirmar eliminación de foto en diálogo y verificar persistencia efectiva únicamente al presionar "Guardar cambios".

### Tests for User Story 4 (TDD - Write First) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T007 [P] [US4] Write unit and validation tests in `DemographicsValidationTest.java` in `easyeat/backend/src/test/java/com/easyeat/profile/domain/` validating age limits (14 to 120), address max 255 chars, and valid gender values (INV-5, INV-8)
- [X] T008 [P] [US4] Write unit tests in `UserPhotoServiceTest.java` in `easyeat/backend/src/test/java/com/easyeat/profile/service/` verifying magic bytes MIME validation (JPEG, PNG, WEBP), size boundary at 5.242.880 bytes, and rejection of > 5 MB and empty files (INV-4)
- [X] T009 [P] [US4] Write MockMvc contract tests in `ProfileControllerTest.java` in `easyeat/backend/src/test/java/com/easyeat/profile/controller/` verifying `PUT /api/v1/profile/demographics`, `POST /api/v1/profile/avatar`, `GET /api/v1/profile/avatar`, and `DELETE /api/v1/profile/avatar` with RFC 7807 ProblemDetail error responses
- [X] T010 [P] [US4] Write component tests in `PersonalDataForm.test.tsx` in `easyeat/frontend/tests/components/` verifying age field validation (14-120), gender selection, address text entry, and inline error message rendering
- [X] T011 [P] [US4] Write component tests in `PhotoUploadAndRemoval.test.tsx` in `easyeat/frontend/tests/components/` verifying 5 MB client size check, invalid MIME error, and deferred removal confirmation flow (FR-024, FR-025)

### Implementation for User Story 4

- [X] T012 [P] [US4] Implement DTOs `DemographicsRequest.java`, `AvatarUploadResponse.java`, and update `ExtendedProfileResponse.java` in `easyeat/backend/src/main/java/com/easyeat/profile/dto/`
- [X] T013 [US4] Implement `UserPhotoService.java` in `easyeat/backend/src/main/java/com/easyeat/profile/service/` with magic bytes detection, size boundary validation (<= 5 MB), and BYTEA database storage (depends on T005, T006)
- [X] T014 [US4] Extend `ProfileService.java` in `easyeat/backend/src/main/java/com/easyeat/profile/service/` with `updateDemographics` and avatar management methods preserving baseline user data
- [X] T015 [US4] Extend `ProfileController.java` and implement `ProfileAvatarController.java` in `easyeat/backend/src/main/java/com/easyeat/profile/controller/` exposing demographic and avatar stream endpoints with security authorization
- [X] T016 [P] [US4] Update TypeScript types in `easyeat/frontend/src/types/profile.ts` (`age`, `gender`, `address`, `avatarUrl`) and extend `easyeat/frontend/src/api/profileApi.ts` with `updateDemographics`, `uploadAvatar`, and `removeAvatar`
- [X] T017 [P] [US4] Implement `PhotoDeleteConfirmModal.tsx` in `easyeat/frontend/src/components/profile/` with verification dialog and provisional avatar fallback preview
- [X] T018 [US4] Extend `PersonalDataForm.tsx` in `easyeat/frontend/src/components/profile/` adding age, gender, address fields, avatar upload with 5 MB boundary check, and deferred removal execution on form submission
- [X] T019 [US4] Update `WorkspaceLayout.tsx` in `easyeat/frontend/src/components/common/` to reactively render custom avatar or institutional fallback in the top navigation bar

**Checkpoint**: Perfil demográfico y gestión de avatar 100% funcionales y testeables independientemente.

---

## Phase 3: User Story 1 - Add Dish to Order with Quantity Selector (COM-6.1.1) (Priority: P1) 🎯 MVP (refs #189)

**Goal**: Permitir al comensal seleccionar de 1 a N unidades (acotado al stock real en cocina) desde la vista de detalle del plato y añadirlo al carrito en una sola interacción, acumulando cantidades si el plato ya existía en la orden.

**Independent Test**: Abrir detalle de plato con stock disponible, ajustar selector numérico a 3 unidades, pulsar "Agregar al Carrito", verificar toast de confirmación y presencia de 3 unidades en `sessionStorage`; reabrir y sumar 1 unidad adicional verificando que el total sube a 4 sin duplicar filas; verificar que con stock 0 el botón se presenta deshabilitado.

### Tests for User Story 1 (TDD - Write First) ⚠️

- [ ] T020 [P] [US1] Write component tests in `DishDetailModal.test.tsx` in `easyeat/frontend/tests/components/` verifying numeric selector bounds (1 to availableStock), disabled state when out of stock (INV-3), and add action dispatch
- [ ] T021 [P] [US1] Write context tests in `CartContext.test.tsx` in `easyeat/frontend/tests/context/` verifying `addItem(dish, quantity)` accumulating existing items without duplicate rows (INV-1, INV-3) and persisting in `sessionStorage`

### Implementation for User Story 1

- [ ] T022 [US1] Extend `CartContext.tsx` in `easyeat/frontend/src/context/` updating `addItem` signature to accept `quantity` (default 1), accumulating quantities for existing dishes and broadcasting success toast feedback
- [ ] T023 [US1] Extend `DishDetailModal.tsx` in `easyeat/frontend/src/components/catalog/` adding numeric quantity selector (increment/decrement buttons bounded between 1 and remaining stock) and wiring to `addItem` (depends on T022)

**Checkpoint**: Incorporación de platos con selector numérico funcional y testeable independientemente.

---

## Phase 4: User Story 2 - Floating Cart Button & Exact Monetary Totals (COM-6.1.2) (Priority: P1) (refs #190)

**Goal**: Desplegar botón flotante interactivo en la esquina inferior derecha, visible si y solo si hay al menos 1 ítem en el carrito dentro de vistas del catálogo, exhibiendo cantidad total y subtotal monetario exacto en centavos, y abriendo el modal detallado al hacer click.

**Independent Test**: Agregar platos al carrito, navegar entre categorías del catálogo y verificar presencia continua del botón flotante con totales actualizados; navegar a `/perfil` y verificar que se oculta; vaciar carrito y verificar que se oculta; pulsar botón flotante y verificar apertura de `CartDrawer` con desglose exacto de líneas y total.

### Tests for User Story 2 (TDD - Write First) ⚠️

- [ ] T024 [P] [US2] Write unit tests in `currencyUtils.test.ts` in `easyeat/frontend/tests/utils/` verifying fixed-point integer cent arithmetic (INV-2), ensuring zero floating-point rounding drift across multi-item multiplications and additions
- [ ] T025 [P] [US2] Write component tests in `FloatingCartButton.test.tsx` in `easyeat/frontend/tests/components/` verifying strict bijective visibility (visible if totalCount >= 1 and in catalog view, hidden otherwise, INV-6, INV-7) and click dispatch
- [ ] T026 [P] [US2] Write component tests in `CartDrawer.test.tsx` in `easyeat/frontend/tests/components/` verifying detailed breakdown rendering (item name, unit price, quantity, line subtotal, and exact total)

### Implementation for User Story 2

- [ ] T027 [P] [US2] Implement `currencyUtils.ts` in `easyeat/frontend/src/utils/` providing `toCents`, `fromCents`, `formatCurrency`, and exact line/cart sum calculators (INV-2)
- [ ] T028 [US2] Implement `FloatingCartButton.tsx` in `easyeat/frontend/src/components/cart/` with responsive placement, total items badge, exact monetary subtotal, and click-to-open handler (depends on T027)
- [ ] T029 [US2] Integrate `FloatingCartButton.tsx` into `CatalogPage.tsx` in `easyeat/frontend/src/pages/` with route-conditional visibility (depends on T028)
- [ ] T030 [US2] Update `CartDrawer.tsx` in `easyeat/frontend/src/components/cart/` using `currencyUtils` for exact financial display and styling per design system (depends on T027)

**Checkpoint**: Botón flotante reactivo y precisión monetaria exacta operativos y testeables independientemente.

---

## Phase 5: User Story 3 - Modify Quantities, Stock Guardrails & Network Rollback (COM-6.1.3) (Priority: P1) (refs #191)

**Goal**: Permitir sumar/restar unidades en el modal del carrito con recálculo instantáneo, bloqueo visual al alcanzar stock de cocina, eliminación total al restar en cantidad 1, debounce de 300 ms con actualización optimista y rollback determinista ante fallos de red.

**Independent Test**: Pulsar `+` en modal de carrito y verificar incremento instantáneo; incrementar hasta agotar stock y verificar deshabilitación de `+`; pulsar `-` en cantidad 1 y verificar remoción completa del ítem; simular desconexión y verificar rollback a la cantidad confirmada con mensaje de error.

### Tests for User Story 3 (TDD - Write First) ⚠️

- [ ] T031 [P] [US3] Write integration test in `CartValidationIntegrationTest.java` in `easyeat/backend/src/test/java/com/easyeat/cart/` for `POST /api/v1/cart/validate-stock` verifying batch stock availability evaluation
- [ ] T032 [P] [US3] Write unit test in `useDebouncedCartModifier.test.ts` in `easyeat/frontend/tests/hooks/` verifying 300 ms debounce window, optimistic UI counter, cancellation of trailing timers, and deterministic rollback on simulated network failure (INV-9)
- [ ] T033 [P] [US3] Write component test in `CartItemWithBadge.test.tsx` in `easyeat/frontend/tests/components/` verifying +/- controls, disabled `+` at max available stock (INV-3), and complete item removal when decrementing quantity 1 (AC-3.3)

### Implementation for User Story 3

- [ ] T034 [P] [US3] Implement `CartStockValidationRequest.java` and `CartStockValidationResponse.java` DTOs in `easyeat/backend/src/main/java/com/easyeat/cart/dto/`
- [ ] T035 [US3] Implement `CartValidationController.java` in `easyeat/backend/src/main/java/com/easyeat/cart/controller/` validating real available stock for dishes in restaurant context
- [ ] T036 [P] [US3] Implement `cartValidationApi.ts` in `easyeat/frontend/src/api/` for stock synchronization calls
- [ ] T037 [US3] Implement custom hook `useDebouncedCartModifier.ts` in `easyeat/frontend/src/hooks/` managing 300 ms debounce, local state buffer, and error rollback (depends on T036)
- [ ] T038 [US3] Update `CartItemWithBadge.tsx` and `CartDrawer.tsx` in `easyeat/frontend/src/components/cart/` integrating `useDebouncedCartModifier`, disabling `+` on stock limit, removing item on decrement from 1, and displaying warning badge on insufficient stock (depends on T037)

**Checkpoint**: Modificación interactiva de cantidades con debounce, guardas de stock y rollback 100% operativos.

---

## Phase 6: Polish, End-to-End Validation & Cross-Cutting Verification (refs #192)

**Purpose**: Verificación de escenarios quickstart, documentación técnica, y ejecución de suites de regresión completas.

- [ ] T039 [P] Implement end-to-end integration test `ShoppingCartAndDemographicsQuickstartIntegrationTest.java` in `easyeat/backend/src/test/java/com/easyeat/` verifying all 5 quickstart scenarios from `quickstart.md`
- [ ] T040 [P] Update OpenAPI specification in `specs/009-shopping-cart/contracts/openapi.yaml` and verify contract synchronization with MockMvc tests
- [ ] T041 [P] Update feature documentation in `specs/009-shopping-cart/plan.md` and `README.md` reflecting Flyway V10 and shopping cart capabilities
- [ ] T042 Execute full backend regression test suite (`./mvnw clean test`) ensuring 0 failures across all modules
- [ ] T043 Execute full frontend verification suite (`npx tsc --noEmit && npm test && npm run build`) ensuring clean compilation, type check, and zero failing tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies - can start immediately. BLOCKS User Story 4 backend persistence.
- **User Story 4 (Phase 2)**: Depends on Phase 1 completion. Implements demographics and avatar endpoints and UI.
- **User Story 1 (Phase 3)**: Independent of US4. Requires existing `CartContext` and `DishDetailModal`. Delivers MVP shopping cart addition.
- **User Story 2 (Phase 4)**: Depends on US1 (requires items in cart to be visible). Delivers floating button and exact penny currency calculation.
- **User Story 3 (Phase 5)**: Depends on US2 (modifies items in the cart drawer). Delivers interactive quantity manipulation with debounce and rollback.
- **Polish & Verification (Phase 6)**: Depends on all user stories being complete.

### User Story Dependencies

- **US4 (COM-1.4.2)**: Independent user story for diner profile demographics. Can be developed in parallel with US1/US2/US3 by independent frontend/backend slices once Phase 1 is in place.
- **US1 (COM-6.1.1)**: Can start after Phase 1. Foundation of the cart workflow.
- **US2 (COM-6.1.2)**: Consumes cart items created in US1.
- **US3 (COM-6.1.3)**: Extends cart items display in US2 drawer with +/- quantity controls.

### Within Each User Story

- TDD Tests MUST be written FIRST and verified FAILING before implementation.
- Backend entities and DTOs before services and controllers.
- Backend endpoints before frontend API clients.
- Frontend custom hooks and utils before components.
- Story complete and independently verified before moving to the next phase.

### Parallel Opportunities

- **Phase 1**: T001, T002, T003, T005, T006 can run in parallel.
- **Phase 2 (US4)**: All tests T007, T008, T009, T010, T011 can run in parallel. Backend DTO T012 and frontend modal T017 can run in parallel.
- **Phase 3 (US1)**: Tests T020 and T021 can run in parallel.
- **Phase 4 (US2)**: Tests T024, T025, T026 can run in parallel. Util T027 can run in parallel with tests.
- **Phase 5 (US3)**: Tests T031, T032, T033 can run in parallel. DTO T034 and API client T036 can run in parallel.
- **Phase 6**: T039, T040, T041 can run in parallel.

---

## Parallel Example: User Story 4 Tests

```bash
# Launch all tests for US4 together:
Task: "Write unit and validation tests in DemographicsValidationTest.java"
Task: "Write unit tests in UserPhotoServiceTest.java"
Task: "Write MockMvc contract tests in ProfileControllerTest.java"
Task: "Write component tests in PersonalDataForm.test.tsx"
Task: "Write component tests in PhotoUploadAndRemoval.test.tsx"
```

## Parallel Example: User Story 2

```bash
# Launch tests and math utility in parallel:
Task: "Write unit tests in currencyUtils.test.ts"
Task: "Write component tests in FloatingCartButton.test.tsx"
Task: "Write component tests in CartDrawer.test.tsx"
Task: "Implement currencyUtils.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 + Foundational)

1. Complete Phase 1: Foundational Infrastructure (Flyway V10, entities, repos).
2. Complete Phase 3: User Story 1 (Add Dish with Quantity Selector).
3. **STOP and VALIDATE**: Verify comensal can add dishes to local cart session with selector.

### Incremental Delivery

1. Phase 1 (Foundational) → Database schema ready.
2. Phase 2 (US4) → Profile demographics and avatar working end-to-end.
3. Phase 3 (US1) → Dish addition with quantity selector working (MVP).
4. Phase 4 (US2) → Floating button and exact penny currency calculation working.
5. Phase 5 (US3) → Debounced quantity adjustment, stock boundaries, and network rollback working.
6. Phase 6 (Polish) → E2E quickstart and regression validation clean.

---

## Notes

- All tasks follow strict `- [ ] [ID] [P?] [Story?] Description with exact file path` checklist format.
- Every user story has traceable acceptance criteria, mathematical invariants, and tests written first.
- Strict decimal arithmetic using integer cents avoids IEEE 754 precision issues (INV-2).
- Avatar byte stream and size limit at 5.242.880 bytes enforced via magic bytes and database constraints (INV-4).
- Age restricted strictly to biological range 14-120 (INV-5).

