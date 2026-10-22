# Requirements Quality & Engineering Rigor Checklist: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Purpose**: Comprehensive audit of specification completeness, clarity, edge-case coverage, testability, and baseline consistency for `specs/003-diner-profile-dietary/spec.md`.  
**Created**: 2026-10-21  
**Feature**: [spec.md](../spec.md)  

**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact.  
**Marker Semantics**: `[x]` indicates that the specification criterion has been audited and verified as complete, deterministic, and unambiguous. `[ ]` indicates an unresolved ambiguity, omission, or gap requiring specification amendment before task generation.

---

## 1. Clarity & Unambiguity

- [x] CHK001 - Are all functional requirements (FR-001 to FR-027) drafted using deterministic RFC 2119 keywords (`MUST`, `MUST NOT`) without vague or subjective qualifiers (e.g., "convenient", "intuitive", "as appropriate")? [Clarity, Spec §Requirements]
- [x] CHK002 - Is the first name and last name alphabetic validation rule strictly quantified to allow only letters and spaces while explicitly rejecting digits, punctuation, and whitespace-only submissions? [Clarity, Spec §FR-002, User Story 1]
- [x] CHK003 - Is the optional phone number format strictly specified with concrete length and character boundaries (8 to 15 digits, allowing optional leading '+' and standard formatting separators)? [Clarity, Spec §FR-003, Clarifications]
- [x] CHK004 - Is the email verification link expiration explicitly defined with a precise 24-hour time-to-live window? [Clarity, Spec §FR-005, §FR-009]
- [x] CHK005 - Is the catalog dietary filtering logic mathematically defined with a strict conjunction rule (AND logic / intersection) across all active profile restrictions? [Clarity, Spec §FR-018, User Story 3]
- [x] CHK006 - Is the catalog master toggle default state explicitly defined as ON for diners with active restrictions and completely hidden for diners with zero restrictions? [Clarity, Spec §FR-019, §FR-025, Clarifications]
- [x] CHK007 - Is the ephemeral lifetime of the master toggle OFF state clearly scoped to the active browsing session, resetting upon page reload, tab closure, or restaurant navigation? [Clarity, Spec §FR-023, §Edge Cases]

---

## 2. Completeness & Domain Coverage

- [x] CHK008 - Are the closed taxonomy entries for dietary lifestyles (Celiac, Vegan, Vegetarian) and common allergens (Dairy, Peanuts, Tree Nuts, Eggs, Fish, Shellfish, Soy, Wheat) exhaustively enumerated? [Completeness, Spec §FR-012, User Story 2]
- [x] CHK009 - Is the asynchronous email change workflow fully specified, including pending badge display, resend action, cancellation action, and target email replacement? [Completeness, Spec §FR-005, §FR-007, §FR-008]
- [x] CHK010 - Are requirements defined for displaying inline warning badges on conflicting dishes when the master toggle is switched to OFF without preventing addition to cart? [Completeness, Spec §FR-020, §FR-021]
- [x] CHK011 - Is the mandatory checkout risk acknowledgment step explicitly specified when the cart contains one or more non-compliant dishes prior to payment processing? [Completeness, Spec §FR-022, Clarifications]
- [x] CHK012 - Are requirements defined for real-time live cart re-evaluation when dietary preferences are updated in the diner profile? [Completeness, Spec §FR-015, User Story 2]
- [x] CHK013 - Is the immutability of submitted in-flight kitchen orders explicitly stated, preventing profile updates from altering active kitchen KDS tickets? [Completeness, Spec §FR-016, Clarifications]
- [x] CHK014 - Are public catalog browsing requirements defined for unauthenticated guests, including calls-to-action inviting sign-in or registration to activate health filters? [Completeness, Spec §FR-026, User Story 3]
- [x] CHK015 - Are demographic profile updates (avatar, age, gender) and catalog price/category filters explicitly bounded as out-of-scope for this iteration? [Completeness, Spec §Assumptions]

---

## 3. Edge Cases & Boundary Conditions

- [x] CHK016 - Is the empty catalog state explicitly specified when an establishment has zero dishes satisfying the diner's active restriction conjunction, including an action button to switch the toggle OFF? [Edge Cases, Spec §FR-024, User Story 3]
- [x] CHK017 - Does the spec specify behavior when a diner inputs consecutive email change requests before the first is verified, mandating immediate revocation of prior tokens? [Edge Cases, Spec §FR-008, §Edge Cases]
- [x] CHK018 - Is email normalization explicitly required (trimming whitespace and lowercase conversion) prior to uniqueness checks and token generation? [Edge Cases, Spec §Edge Cases]
- [x] CHK019 - Does the spec define error handling for network or database persistence failures during dietary updates, prohibiting silent failures or optimistic local state? [Edge Cases, Spec §FR-014, User Story 2]
- [x] CHK020 - Is the scenario defined where a diner switches the toggle OFF, adds a non-compliant dish to the cart, and switches the toggle back to ON, preserving the cart item with its permanent warning badge? [Edge Cases, Spec §Edge Cases, User Story 3]
- [x] CHK021 - Are debouncing or idempotent state requirements specified for rapid toggling of the master filter during asynchronous data loading to prevent UI flickering or stale data races? [Edge Cases, Spec §Edge Cases]
- [x] CHK022 - Does the spec specify that expired (> 24h) or consumed verification tokens must be rejected with explicit user prompts to request a fresh link? [Edge Cases, Spec §User Story 1 Scenario 10]

---

## 4. Cross-Feature Baseline Consistency (001, 002, 004)

- [x] CHK023 - Is active session revocation upon email change aligned with the single-active-session policy and `token_version` invalidation mechanism established in iterations 001 and 002? [Consistency, Spec §FR-009, Plan §Baseline Alignment]
- [x] CHK024 - Does the specification maintain independence between diner name fields (`first_name`, `last_name`) and the staff `full_name` column introduced in iteration 004? [Consistency, Plan §D-009, Spec §FR-001]
- [x] CHK025 - Are public catalog browsing requirements (FR-026) reconciled with the route guards and `SecurityConfig` rules introduced in iteration 002, ensuring unauthenticated guests are not forcibly redirected to login? [Consistency, Spec §FR-026, Plan §D-010]
- [x] CHK026 - Is multi-tenant data isolation preserved by ensuring diner accounts have `tenant_id IS NULL` while catalog queries are strictly partitioned by establishment path identifier? [Consistency, Spec §Key Entities, Plan §Baseline Alignment]
- [x] CHK027 - Are past completed order records and receipts guaranteed to remain immutable snapshots of historical diner contact details, preventing drift from subsequent profile modifications? [Consistency, Spec §FR-011, User Story 1]

---

## 5. Measurability & Acceptance Criteria Quality

- [x] CHK028 - Are success criteria quantified with objective, measurable metrics (e.g., 100% exclusion of non-compliant dishes, 100% explicit risk consent, 0% unverified optimistic state)? [Measurability, Spec §SC-001..SC-005]
- [x] CHK029 - Is UI response latency for catalog master toggle switching and filter evaluation bounded with a measurable performance target (< 1 second / 0ms perceived)? [Measurability, Spec §SC-007, Plan §Performance Goals]
- [x] CHK030 - Is the user retention goal for empty catalog states objectively defined and measurable (< 2% session abandonment measured via toggle action button)? [Measurability, Spec §SC-006]
- [x] CHK031 - Are dual client-server validation requirements specified so that backend services independently validate name formats, phone syntax, email uniqueness, and restriction queries? [Measurability, Spec §FR-027]

---

## Notes

- Mark items `[x]` only after reviewer evaluation confirms the requirement-quality criterion is satisfied in the specification.
- Leave items unchecked when they still require clarification, correction, or reviewer evaluation.
- `/speckit-tasks` and `/speckit-implement` read this checklist state as a quality gate and must not modify markers.
- Items are numbered sequentially (CHK001 through CHK031) for cross-artifact traceability.
