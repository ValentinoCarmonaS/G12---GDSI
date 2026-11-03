# Feature Specification: Chef Dish Management — Technical File Creation and Submission

**Feature Branch**: `005-chef-dish-management`

**Created**: 25/10/2026

**Status**: Draft

**Input**: User description: "Chef Dish Management — Technical File Creation and Submission. Covers CHF-2.2.1, CHF-2.2.2, CHF-2.2.3, CHF-2.2.4."

## Clarifications

### Session 2026-10-25

- Q: How many photos can the Chef upload per dish? → A: Exactly one photo per dish. The photo is 100% optional at all times, including for submission to review.
- Q: What image formats and maximum size are allowed? → A: Any standard image format (JPEG, PNG, WebP). Maximum 5 MB per file.
- Q: How are allergen labels interpreted on a dish? → A: Mixed model — "Vegan" and "Vegetarian" are positive compliance labels; all other codes including Celiac/Sin TACC are warning labels (the Chef marks them when the dish CONTAINS the allergen).
- Q: Is the existing dietary restriction taxonomy reused? → A: Yes. The same platform taxonomy from spec 003 is reused. No new taxonomy is created.
- Q: What are the dish categories? → A: Fixed closed list: Entradas, Platos principales, Postres, Bebidas, Ensaladas.
- Q: Is the dish name unique within the establishment? → A: Yes, across all statuses.
- Q: What constitutes "recipe present" for the submission gate? → A: At least 1 ingredient with a valid positive quantity.
- Q: How is allergen completeness verified? → A: An explicit "Confirm allergen section" acknowledgment by the Chef, regardless of how many labels were selected.
- Q: Multiple drafts? → A: Yes, unlimited, no expiry.
- Q: Can the Chef delete drafts? → A: Yes. Once in Pending Price, no deletion or editing allowed.
- Q: Admin notification mechanism? → A: Internal platform alert in the Administrator panel. No external email.
- Q: Draft visibility across Chefs? → A: All Chefs in the same establishment share visibility and edit rights over all establishment drafts.
- Q: Recipe quantity units? → A: The ingredient unit of measure as defined in the inventory. Chef enters only the numeric amount.
- Q: Minimum valid quantity? → A: Strictly positive (greater than zero).
- Q: Duplicate ingredient in recipe? → A: System rejects; may offer to update the existing quantity.
- Q: Discontinued ingredients in recipe selector? → A: Only Active ingredients are shown.
- Q: Placeholder dish seed data decision? → A: Keep and adapt if straightforward; remove conflicting records if adaptation is non-trivial.
- Q: Can the Chef delete a previously uploaded photo from a draft to leave the dish without a photo again? → A: Yes, the Chef can delete the photo at any time while the dish is in Draft status, leaving it without an associated photo.
- Q: What are the maximum character lengths allowed for the dish name and optional description? → A: Mandatory name up to 100 characters; optional description up to 500 characters.
- Q: How should dishes be ordered and filtered in the Chef's management list? → A: Ordered by last updated timestamp descending (most recently edited first), with optional filter by status: "All", "Draft", and "Pending Price".
- Q: If an ingredient included in a draft's recipe is discontinued by the Administrator before submission, can the Chef submit the dish for review? → A: No. The system blocks submission for review if the recipe contains any discontinued ingredient, requiring the Chef to replace or remove it before being able to submit for review.
- Q: Should the Chef's dish management list use 20 items per page pagination or list all items in a single view? → A: 20 items per page pagination with navigation controls, consistent with the catalog pattern established in spec 004.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Basic Dish Identity (Priority: P1)

**CHF-2.2.1**: As a Chef, I want to create a new dish's technical file by providing a name, category, description, and optionally a photo, so that the new culinary offering is identified and can be presented attractively to the diner.

The Chef opens the "New Dish" form in the kitchen workspace. They fill in the dish name (required), select a category from the fixed list, write an optional description, and optionally upload a single photo. The system validates that the name is unique within the establishment (after trimming whitespace). On saving, the system creates a dish draft with "Draft" status and the Chef can continue to the recipe and allergen sections.

**Why this priority**: The dish's basic identity is the root entity. All subsequent sections (recipe, allergen, submission) build on this record. Without it, no technical file exists.

**Independent Test**: Create a new dish with a valid name, category, and description (no photo), verify it appears in the draft list. Then attempt to create another dish with the same name in the same establishment and verify rejection.

**Acceptance Scenarios**:

1. **Given** a Chef on the "New Dish" form, **When** they submit a valid name and category (description and photo omitted), **Then** the system creates a dish draft with status "Draft" and the draft appears in the Chef's dish list.
2. **Given** a Chef uploading a photo, **When** the file is in JPEG, PNG, or WebP format and is 5 MB or smaller, **Then** the photo is accepted and associated with the draft.
3. **Given** a Chef uploading a photo, **When** the file exceeds 5 MB or is in an unsupported format, **Then** the system rejects the file with a clear validation message specifying accepted formats and the size limit. The existing photo (if any) is retained.
4. **Given** a Chef submitting the "New Dish" form, **When** the dish name already exists in the same establishment (regardless of that dish's status), **Then** the system rejects the save and displays a uniqueness violation message.
5. **Given** a Chef submitting the "New Dish" form, **When** the name field is empty or contains only whitespace, **Then** the system prevents saving and highlights the name as a mandatory field.
6. **Given** a Chef submitting the "New Dish" form, **When** no category is selected, **Then** the system prevents saving and requires a category selection from the fixed list.
7. **Given** a saved draft, **When** the Chef reopens and edits any field (name, category, description, or photo), **Then** the changes are persisted and the draft retains "Draft" status.
8. **Given** a Chef requesting to delete a draft, **When** the deletion is confirmed, **Then** the system permanently removes the draft and all associated recipe lines and allergen labels.
9. **Given** a Chef editing a draft that has an associated photo, **When** the Chef removes the photo and saves, **Then** the dish draft is persisted without a photo.
10. **Given** a Chef entering a dish name with more than 100 characters or a description with more than 500 characters, **When** they attempt to save, **Then** the system prevents saving and displays a validation message indicating the character limit.

---

### User Story 2 — Recipe Definition (Priority: P1)

**CHF-2.2.2**: As a Chef, I want to select which ingredients and in what exact quantities per serving make up this dish, so that the system knows exactly how much to deduct from general stock each time one is sold.

The Chef opens the recipe section of a draft. They search for or browse Active ingredients from the establishment's catalog and add each with a per-serving quantity. The unit of measure is inherited from the ingredient's inventory definition. The Chef may add, remove, or update ingredient quantities while the dish is in "Draft" status.

**Why this priority**: The recipe links dishes to inventory and is a required completeness condition for submission. It is co-P1 with basic identity because both are prerequisites for submission.

**Independent Test**: Open a draft, add two Active ingredients with valid positive quantities, save the recipe, verify both appear linked. Remove one, verify it is unlinked.

**Acceptance Scenarios**:

1. **Given** a Chef editing the recipe of a draft, **When** they select an Active ingredient and enter a strictly positive quantity, **Then** the ingredient is linked to the technical file with the specified quantity and the ingredient's unit of measure.
2. **Given** a Chef entering a recipe quantity, **When** the value is zero or negative, **Then** the system rejects the entry and displays a validation message stating the quantity must be greater than zero.
3. **Given** a Chef adding an ingredient that is already in the recipe, **When** the duplicate is attempted, **Then** the system informs the Chef the ingredient is already present and offers to update the existing quantity instead of creating a duplicate entry.
4. **Given** a Chef browsing the ingredient selector, **When** the catalog is displayed, **Then** only Active ingredients are shown; Discontinued and Archived ingredients are excluded.
5. **Given** a Chef editing the recipe, **When** they remove an ingredient already in the recipe, **Then** the ingredient is unlinked from the technical file.
6. **Given** a Chef using the recipe ingredient selector, **When** they search by name or filter by category, **Then** the filtering behavior mirrors the read-only catalog from spec 004 (20 items per page, alphabetically ascending by name).
7. **Given** a dish in "Pending Price" status, **When** a Chef attempts to add, remove, or modify any recipe ingredient, **Then** all modifications are blocked and an informational message is shown.

---

### User Story 3 — Allergen and Dietary Labeling (Priority: P2)

**CHF-2.2.3**: As a responsible Chef, I want to explicitly mark the allergens and dietary compliance of this dish, so that food safety is ensured and the diner's automatic filters work correctly.

The Chef opens the allergen section of a draft. The system presents the full platform taxonomy in two groups. The Chef selects all applicable labels and then explicitly confirms the section. Zero labels selected is a valid state. The Chef may return to modify labels while the dish is in "Draft" status.

| Group | Codes | Label Direction |
|---|---|---|
| Dietary Lifestyles | VEGAN, VEGETARIAN | Positive compliance ("this dish IS suitable for...") |
| Dietary Lifestyles | CELIAC | Warning ("this dish CONTAINS gluten / is NOT Sin TACC") |
| Common Allergens | DAIRY, PEANUTS, TREE_NUTS, EGGS, FISH, SHELLFISH, SOY, WHEAT | Warning ("this dish CONTAINS...") |

**Why this priority**: Allergen labeling is required for the submission gate and feeds the diner-side filtering of spec 003. It is P2 because it logically follows the dish identity (P1).

**Independent Test**: Open the allergen section, select two warning labels, confirm, save. Verify labels are persisted. Reopen, clear all labels, confirm again. Verify zero-label confirmed state is retained.

**Acceptance Scenarios**:

1. **Given** a Chef on the allergen section, **When** the section renders, **Then** the system displays the full dietary taxonomy organized into Dietary Lifestyles and Common Allergens, with clear labeling indicating whether each code is a warning or a positive compliance label.
2. **Given** a Chef selecting labels and confirming the section, **When** zero, one, or multiple labels are selected and confirmed, **Then** the selected labels are persisted as metadata on the dish and the section is marked as confirmed.
3. **Given** a Chef who has not yet confirmed the allergen section, **When** the submission gate is evaluated, **Then** the allergen section is treated as incomplete regardless of label selections.
4. **Given** a Chef editing a previously confirmed allergen section, **When** they add or remove labels and save, **Then** the updated labels are persisted and the section remains confirmed.
5. **Given** a Chef attempting to edit the allergen section of a dish in "Pending Price" status, **When** they try to modify or re-confirm, **Then** the system blocks the modification and the existing labels remain unchanged.

---

### User Story 4 — Submission for Administrator Review (Priority: P2)

**CHF-2.2.4**: As a Chef, I want to send the completed dish draft to the Administrator, so that they evaluate it, assign a final price, and publish it on the official menu.

The Chef views a draft and taps "Send for Review". The system evaluates all completeness conditions. If the dish is complete, it transitions to "Pending Price", becomes read-only for all Chefs, and an internal alert is sent to the Administrator.

**Why this priority**: This is the culminating workflow action. It depends on User Stories 1, 2, and 3 being complete.

**Independent Test**: Complete a draft fully, tap "Send for Review", verify status changes to "Pending Price", all edit actions blocked, internal admin alert created.

**Acceptance Scenarios**:

1. **Given** a Chef viewing a complete draft (name, category, at least one recipe ingredient with valid positive quantity, all recipe ingredients currently Active, confirmed allergen section), **When** they tap "Send for Review", **Then** the system transitions the dish to "Pending Price", disables all edit actions, and shows a success confirmation to the Chef.
2. **Given** a Chef tapping "Send for Review" on a draft where any completeness condition is unmet (including missing basic info, unconfirmed allergens, zero recipe ingredients, or any discontinued recipe ingredient), **When** the transition is evaluated, **Then** the system blocks the transition and clearly identifies each unmet condition.
3. **Given** a dish just transitioned to "Pending Price", **When** the notification is triggered, **Then** the Administrator's panel displays an alert identifying the dish name and the submitting Chef's name.
4. **Given** a dish in "Pending Price" status, **When** any Chef attempts to edit any field or delete the dish, **Then** all such actions are blocked with no data modification.
5. **Given** a Chef viewing the draft list, **When** a draft meets all completeness conditions, **Then** "Send for Review" is visually enabled; otherwise it is visually disabled with an indicator of what is missing.
6. **Given** a dish in "Pending Price" status visible in the draft list, **When** a Chef views it, **Then** the dish is clearly shown as read-only with a pending review indicator.
7. **Given** a Chef viewing the dish management list, **When** the list renders, **Then** dishes are ordered by last updated timestamp descending, paginated at 20 items per page with navigation controls, and the Chef can filter by "All", "Draft", or "Pending Price".

---

### Edge Cases

- **Duplicate name race condition**: If another Chef creates a dish with the same name while the first Chef is composing their draft, the uniqueness check at save time catches the conflict.
- **Ingredient discontinued after recipe inclusion**: An ingredient already added to a draft recipe that is subsequently Discontinued by an Administrator remains visible in the draft recipe, but the system blocks "Send for Review" until the Chef removes or replaces the discontinued ingredient. The Chef cannot re-add it once removed, since Discontinued ingredients are excluded from the selector.
- **Allergen section confirmed with zero labels**: Valid state. Means "this dish contains no known allergens or dietary compliance claims."
- **Concurrent Chef edits on same draft**: Last save wins. No conflict detection or merge required in this iteration.
- **Draft with zero recipe ingredients at submission**: Blocked. Clear message: "The recipe must include at least one ingredient."
- **Name with leading or trailing whitespace**: Trimmed before uniqueness validation and storage.
- **Quantities and unit precision**: Continuous-unit ingredients (g, kg, ml, l) accept decimal quantities with exact precision; discrete-unit ingredients (u) accept whole numbers only, consistent with spec 004.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a Chef to create a new dish technical file (draft) by providing a name (mandatory, up to 100 characters, unique within the tenant as enforced in FR-002), a category (mandatory, from the fixed list), and an optional description (up to 500 characters). Values exceeding these lengths MUST be rejected by form validation.
- **FR-002**: The system MUST enforce dish name uniqueness within the same establishment across all dish statuses. Names MUST be trimmed of leading and trailing whitespace before validation and storage.
- **FR-003**: The fixed dish category list for this iteration MUST be: Entradas, Platos principales, Postres, Bebidas, Ensaladas.
- **FR-004**: The system MUST allow the Chef to upload a single optional photo per dish in JPEG, PNG, or WebP format, with a maximum size of 5 MB. Files exceeding the limit or in unsupported formats MUST be rejected with a descriptive error. The existing photo is retained on rejection. While the dish is in "Draft" status, the system MUST also allow the Chef to explicitly remove an existing photo, leaving the dish without an associated photo.
- **FR-005**: A newly created dish MUST be assigned the status "Draft" (Borrador) upon saving the initial basic data.
- **FR-006**: The system MUST allow the Chef to edit any field of a dish in "Draft" status: name, category, description, photo, recipe, and allergen labels.
- **FR-007**: The system MUST allow the Chef to delete a draft. Deletion MUST permanently remove the dish and all associated recipe lines and allergen labels.
- **FR-008**: The system MUST allow a Chef to link Active ingredients from the establishment's catalog to a dish's recipe, specifying a per-serving quantity for each, where quantity is expressed in the ingredient's own unit of measure.
- **FR-009**: Recipe ingredient quantities MUST be strictly positive (greater than zero). Zero and negative values MUST be rejected.
- **FR-010**: The same ingredient MUST NOT appear more than once in a dish's recipe. If a Chef attempts to add a duplicate, the system MUST notify them and offer to update the existing quantity.
- **FR-011**: Only Active ingredients MUST be available in the recipe ingredient selector. Discontinued and Archived ingredients MUST be excluded.
- **FR-012**: The recipe ingredient selector MUST support filtering by category and text search by ingredient name, with 20 items per page ordered alphabetically ascending (A–Z) by name, consistent with spec 004.
- **FR-013**: The system MUST present the platform's full dietary taxonomy in the dish allergen section, organized into Dietary Lifestyles (VEGAN, VEGETARIAN as positive compliance labels; CELIAC as a warning label) and Common Allergens (DAIRY, PEANUTS, TREE_NUTS, EGGS, FISH, SHELLFISH, SOY, WHEAT as warning labels), reusing the taxonomy codes from the existing `dietary_restrictions` table defined in spec 003.
- **FR-014**: A Chef MUST be able to select zero, one, or multiple labels from the allergen taxonomy for a dish. Zero selected labels is a valid state.
- **FR-015**: The allergen section MUST include an explicit confirmation action ("Confirm allergen section") that the Chef activates when they consider the section complete, regardless of the number of labels selected. This confirmation state MUST be persisted on the dish.
- **FR-016**: The system MUST allow a Chef to submit a dish draft for review via "Send for Review" only when ALL of the following completeness conditions are met: (1) name and category are present; (2) the recipe has at least one ingredient with a valid positive quantity; (3) the allergen section has been explicitly confirmed; and (4) all ingredients in the recipe are currently in Active status (no discontinued ingredients).
- **FR-017**: When "Send for Review" is triggered on an incomplete dish, the system MUST block the status transition and display a clear message identifying each unmet completeness condition.
- **FR-018**: When all completeness conditions are met and "Send for Review" is triggered, the system MUST transition the dish status from "Draft" to "Pending Price" (Pendiente de Precio).
- **FR-019**: Upon transitioning to "Pending Price", the system MUST create an internal platform notification visible in the Administrator's panel, identifying the dish name and the submitting Chef's name.
- **FR-020**: A dish in "Pending Price" status MUST be locked against all Chef modifications: editing any field, adding or removing recipe ingredients, changing allergen labels, and deleting the dish MUST all be blocked.
- **FR-021**: All Chefs of the same establishment MUST share visibility and edit rights over all dish drafts belonging to that establishment. The Chef's dish management list MUST display items ordered by last updated timestamp descending (most recently updated first), paginated at 20 items per page with page navigation controls, and MUST provide filtering by status with options: "All" (Todos), "Draft" (Borrador), and "Pending Price" (Pendiente de Precio).
- **FR-022**: The "Send for Review" action MUST be visually enabled when all completeness conditions are met and visually disabled with an informational indicator when any condition is unmet.
- **FR-023**: The system MUST validate all input fields in real time on dish forms, highlighting missing required fields and invalid values before submission.
- **FR-025**: El sistema MUST permitir al Chef recibir un plato devuelto por el Administrador en estado de rechazo, visualizando el comentario explicativo (10-1000 caracteres) y una propuesta de edición precargada (snapshot).
- **FR-026**: El Chef MUST poder editar libremente la propuesta de edición del Administrador y re-enviar el plato a la bandeja de "En espera" (`PENDING_PRICE`), incrementando el ciclo de revisión sin recibir nunca acceso a costos ni márgenes.


### Key Entities

- **Dish (Technical File / Ficha Técnica)**: Represents a culinary item being authored by a Chef. Key attributes: unique identifier, tenant identifier, name (unique within tenant, trimmed), category (from fixed list), optional description, optional photo reference, status (Draft / Pending Price), allergen section confirmation flag, created-by Chef identifier, timestamps.
- **RecipeIngredientLine**: Represents one ingredient's participation in a dish's recipe. Key attributes: dish reference, ingredient reference (must be Active at time of addition), quantity per serving (strictly positive), unit of measure (inherited from ingredient). Unique per dish–ingredient pair.
- **DishDietaryLabel**: Represents the association between a dish and a dietary/allergen taxonomy code. Key attributes: dish reference, restriction code (from existing `dietary_restrictions` table). A dish may have zero or more labels.
- **DishStatus (state machine, in-scope only)**:
  - **Draft** (Borrador): Initial state. Chef can edit and delete.
  - **Pending Price** (Pendiente de Precio): Terminal state within this feature's scope. Dish is locked. Awaiting Administrator pricing review.
  - States beyond this scope (Approved, Published, Rejected, Inactive) are out of scope and MUST NOT be modeled here.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Chef can create a dish draft and have it appear in the draft list within 30 seconds from opening the form.
- **SC-002**: 100% of "Send for Review" submissions that fail any completeness condition are blocked with a clear per-condition error message. Zero incomplete dishes transition to "Pending Price".
- **SC-003**: 100% of successful "Pending Price" transitions generate a visible internal notification in the Administrator's panel before the Admin's next panel load.
- **SC-004**: 100% of edit attempts on a "Pending Price" dish by any Chef are blocked with no data modification occurring.
- **SC-005**: Zero dishes in the same establishment share the same name at any given time, across all statuses.
- **SC-006**: A Chef can add 10 ingredients to a recipe and confirm the allergen section within 3 minutes from opening a new draft.
- **SC-007**: Zero recipe lines exist with a quantity of zero or below. Zero recipe lines contain the same ingredient twice for the same dish.
- **SC-008**: Allergen labels saved on a dish are correctly applied by the diner-side catalog filtering logic of spec 003 with 100% accuracy (verified at integration test level; diner-side rendering is out of scope here).

---

## Assumptions

- The ingredient catalog (Active ingredients with units of measure) is fully operational as delivered by spec 004. At least one Active ingredient must exist in the establishment for a Chef to define a recipe.
- The dietary restriction taxonomy (`dietary_restrictions` table with codes CELIAC, VEGAN, VEGETARIAN, DAIRY, PEANUTS, TREE_NUTS, EGGS, FISH, SHELLFISH, SOY, WHEAT) is seeded and operational as delivered by spec 003.
- Authentication, authorization (Chef role), and multi-tenant isolation are provided by the existing infrastructure from specs 001, 002, and 004 and are not re-specified here.
- Photo file storage backend is available. The specific mechanism is a planning-phase decision.
- The "internal platform notification" for the Administrator is a new, minimal notification surface. Its visual design and persistence model are planning-phase decisions; the spec requires only that it be visible in the Administrator's panel and identifies dish name and submitting Chef.
- A Chef draft list view (listing all establishment drafts with their completeness state and available actions) is a necessary UI surface implied by this feature. Its layout is a planning-phase decision.
- "Confirming the allergen section" is a deliberate Chef action. The system MUST NOT auto-confirm the section on save.
- Quantity precision rules follow spec 004: continuous-unit ingredients accept exact decimal values; discrete-unit ingredients (u) accept whole numbers only.
- The price field is intentionally absent from the Chef's technical file. Price is assigned exclusively by the Administrator in a subsequent workflow out of scope here.
- The five seed dishes in `R__seed_dev_dishes_and_dietary.sql` are dev-only data. Their compatibility with the new dish entity model will be evaluated during planning.
