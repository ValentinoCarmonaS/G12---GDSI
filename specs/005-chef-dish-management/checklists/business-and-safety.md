# Business Rules, Food Safety & Lifecycle Gating Quality Checklist: Chef Dish Management

**Purpose**: Formal requirements-quality audit and gating checklist for Chef Dish Management (`specs/005-chef-dish-management/spec.md`), focusing on business invariants, food safety labeling, recipe integrity, and review transition gating.  
**Created**: 25/10/2026  
**Feature**: [spec.md](../spec.md)  

**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact.  
**Marker Semantics**: `[x]` indicates that the reviewer has audited the specification requirement and verified that it is complete, deterministic, and unambiguous. `[ ]` indicates that the criterion requires further reviewer validation, specification clarification, or amendment.  

---

## 1. Food Safety & Allergen Labeling Requirements

- [x] CHK001 - Are allergen labeling semantics explicitly differentiated between positive lifestyle compliance claims (`VEGAN`, `VEGETARIAN`) and negative/warning containment declarations (`CELIAC`, `DAIRY`, `PEANUTS`, etc.)? [Clarity, Spec §FR-013, §User Story 3]
- [x] CHK002 - Is the Celiac / Sin TACC restriction explicitly defined as a warning label ("contains gluten / NOT Sin TACC") rather than an affirmative gluten-free certification claim? [Clarity, Food Safety, Spec §FR-013, §Clarifications]
- [x] CHK003 - Does the specification define an explicit, intentional Chef acknowledgment action ("Confirm allergen section") that is required regardless of how many allergen tags are selected? [Completeness, Spec §FR-015, §User Story 3]
- [x] CHK004 - Is the empty allergen state (zero labels selected) explicitly specified as a valid confirmed condition representing "no known allergens", preventing ambiguity between "unspecified" and "allergen-free"? [Clarity, Edge Cases, Spec §FR-014, §Edge Cases]
- [x] CHK005 - Are the dietary restriction taxonomy codes strictly mapped to the preexisting platform taxonomy defined in spec 003 without introducing redundant or competing codes? [Consistency, Spec §FR-013, §Assumptions]
- [x] CHK006 - Is the modification of confirmed allergen labels explicitly prohibited once a dish transitions to "Pending Price"? [Security & Invariants, Spec §FR-020, §User Story 3]

---

## 2. Recipe & Inventory Integrity Requirements

- [x] CHK007 - Are recipe ingredient quantities required to be strictly positive numbers (`quantity > 0`), with zero and negative numbers explicitly rejected? [Clarity, Invariants, Spec §FR-009, §User Story 2]
- [x] CHK008 - Does the specification define that recipe measurement units are inherited from the active ingredient catalog rather than allowing custom or free-text units? [Consistency, Spec §FR-008, §Assumptions]
- [x] CHK009 - Is quantity precision explicitly defined for continuous decimal units (`g`, `kg`, `ml`, `l`) versus discrete integer units (`u`), in alignment with spec 004? [Clarity, Consistency, Spec §Edge Cases, §Assumptions]
- [x] CHK010 - Is duplicate ingredient prevention strictly specified per recipe line, with clear handling to update existing quantities instead of creating duplicate rows? [Clarity, Data Integrity, Spec §FR-010, §User Story 2]
- [x] CHK011 - Does the specification mandate that only `Active` ingredients appear in the recipe ingredient selector, excluding `Discontinued` and `Archived` items? [Coverage, Spec §FR-011, §User Story 2]
- [x] CHK012 - Is the behavior explicitly defined for when an ingredient already present in a draft recipe is subsequently discontinued by an Administrator before submission? [Edge Cases, Spec §FR-016, §Edge Cases]
- [x] CHK013 - Does the recipe ingredient selector specify deterministic search and pagination behavior (20 items per page, alphabetical A-Z sorting) consistent with spec 004? [Consistency, Spec §FR-012]

---

## 3. Workflow Gating & State Immutability Requirements

- [x] CHK014 - Are all mandatory gate conditions for "Send for Review" exhaustively enumerated: valid name, category, at least one ingredient with positive quantity, all ingredients Active, and confirmed allergen section? [Completeness, Spec §FR-016, §User Story 4]
- [x] CHK015 - Does the specification require specific per-condition diagnostic feedback when "Send for Review" is blocked due to unmet completeness criteria? [Clarity, UX Quality, Spec §FR-017, §User Story 4]
- [x] CHK016 - Is the state transition from "Draft" to "Pending Price" defined as a locking boundary that freezes all dish attributes, recipe lines, and allergen labels against any further Chef modifications? [Completeness, Invariants, Spec §FR-018, §FR-020, §User Story 4]
- [x] CHK017 - Is draft deletion explicitly restricted to "Draft" status and prohibited once a dish reaches "Pending Price"? [Consistency, Spec §FR-007, §FR-020, §User Story 1]
- [x] CHK018 - Does the specification mandate an internal platform notification for the Administrator upon submission, identifying both the dish name and the submitting Chef? [Completeness, Spec §FR-019, §User Story 4]
- [x] CHK019 - Are states beyond "Pending Price" (e.g., Approved, Published, Rejected) explicitly marked as out of scope to avoid scope leakage? [Boundary, Spec §Key Entities, §Assumptions]

---

## 4. Data Identity & Edge Case Coverage

- [x] CHK020 - Is dish name uniqueness defined across the entire tenant and across all dish statuses, preventing duplicate names even against existing drafts? [Clarity, Spec §FR-002, §User Story 1]
- [x] CHK021 - Is name whitespace sanitization explicitly specified (trimming leading/trailing spaces) prior to uniqueness validation and storage? [Edge Cases, Spec §FR-002, §Edge Cases]
- [x] CHK022 - Are maximum length limits defined for both mandatory dish names (≤ 100 characters) and optional descriptions (≤ 500 characters)? [Clarity, Spec §FR-001, §User Story 1]
- [x] CHK023 - Are image upload constraints explicitly specified regarding accepted formats (JPEG, PNG, WebP) and maximum file size (5 MB)? [Clarity, Spec §FR-004, §User Story 1]
- [x] CHK024 - Does the specification define explicit photo removal behavior, allowing a Chef to revert an uploaded photo back to an empty state while in "Draft" status? [Completeness, Spec §FR-004, §User Story 1]
- [x] CHK025 - Are the dish categories strictly restricted to a fixed closed list (`Entradas`, `Platos principales`, `Postres`, `Bebidas`, `Ensaladas`) for this iteration? [Consistency, Spec §FR-003]
- [x] CHK026 - Is draft ownership defined as shared among all Chefs of the same tenant, preventing cross-tenant visibility while allowing intra-kitchen collaboration? [Coverage, Spec §FR-021]
- [x] CHK027 - Does the specification define conflict resolution for concurrent Chef edits on the same draft ("last save wins") without requiring complex locking in this phase? [Edge Cases, Spec §Edge Cases]
- [x] CHK028 - Is the Chef dish management list specified with deterministic sorting (last updated timestamp descending) and 20-item pagination with status filtering? [Clarity, Spec §FR-021, §User Story 4]

---

## 5. Acceptance Criteria & Testability

- [x] CHK029 - Do all user stories include structured acceptance scenarios formulated in `Given-When-Then` format with verifiable preconditions and observable outcomes? [Testability, Spec §User Scenarios 1-4]
- [x] CHK030 - Are all success criteria (SC-001 through SC-008) defined with objective, measurable metrics without implementation language or framework references? [Measurability, Spec §Success Criteria]
- [x] CHK031 - Is the non-blocking nature of optional fields (description, photo) objectively verifiable through independent test scenarios? [Testability, Spec §User Story 1]
- [x] CHK032 - Is the disposition of development seed data (`R__seed_dev_dishes_and_dietary.sql`) explicitly specified, requiring adaptation or removal of conflicting records without breaking existing test suites? [Traceability, Spec §FR-024, §Assumptions]

---

## Notes

- Mark items `[x]` only after reviewer confirms the requirements-quality criterion is satisfied
- Leave items unchecked (`[ ]`) when they still require clarification, correction, or reviewer evaluation
- `/speckit-implement` reads checklist checkbox state as a gate and must not modify markers
- Items are numbered sequentially (CHK001 to CHK032) for deterministic traceability across reviews
