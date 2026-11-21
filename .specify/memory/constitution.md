<!--
Sync Impact Report
- Version change: 2.0.0 -> 2.1.0
- Modified principles: none
- Added sections:
  - Fundamental Principle XI: Role-Scoped Visual Governance and Non-Regressive UI Refactoring
  - Frontend & Visual Governance Constraints (EasyEat) under Additional Constraints
  - UI critique and regression verification gates under Development Workflow and Quality Gates
- Removed sections: none
- Templates requiring updates: none
- Deferred items:
  - TODO(DESIGN_CONTRACTS_REMEDIATION): Remediate comensal.DESIGN.md (uses prohibited Inter typography) and mostrador.DESIGN.md (uses #111827 text on #111111 surface, failing WCAG 2.2 AA contrast) before UI implementation for those roles.
-->

# SDD Project Constitution

## Drafting Conventions

The words MUST, MUST NOT, SHOULD, and MAY are to be interpreted as described in RFC 2119:

- **MUST / MUST NOT**: absolute requirement or prohibition.
- **SHOULD**: strong recommendation; omitted only with documented justification.
- **MAY**: optional.

Each principle includes its **Compliance** criteria (verifiable during a review or in the plan's Constitution Check) and its **Rationale**.

## Fundamental Principles

### I. The Specification Is the Source of Truth

- When customer, user, or stakeholder requirements exist, the resulting functional specification MUST be the authority on what behavior the software must provide.
- The implementation MUST adapt to the specification and MUST NOT silently redefine it.
- In the event of a discrepancy between code and specification, the agent MUST take the specification as the authority, and identify and resolve the discrepancy explicitly.

**Compliance**: any detected discrepancy is recorded and resolved, either by correcting the code or by amending the specification with human approval.

**Rationale**: ensure that delivered software faithfully reflects agreed-upon needs, without deviations introduced during implementation.

### II. Specification Before Implementation

- In projects using Spec-Driven Development, the agent MUST NOT begin implementing a user story until completing the specification and preparation process defined in the Development Workflow.
- A user story can ONLY enter implementation when it exists as a defined and verifiable unit of work.

**Compliance**: no implementation task exists without a prior specification, plan, and tasks artifact backing it.

**Rationale**: prevent unplanned work, ensuring that design precedes code.

### III. Layered Verification Proportional to Risk

- Validation MUST be progressive, and each level DOES NOT replace the previous one:
  1. tests verifying that the implemented behavior works;
  2. verification that such behavior complies with the specification;
  3. regression tests that preserve validated behavior;
  4. tests sufficiently expressive to serve as executable documentation.
- Edge cases, invalid states, boundaries, expected errors, and domain constraints MUST be addressed explicitly when they are part of the guaranteed behavior. A feature is not considered verified simply because its happy path works.
- The testing strategy MUST be proportional to the risk and criticality of the software, combining techniques as appropriate such as unit, integration, and higher-level tests, coverage analysis, mutation testing, or chaos engineering. These techniques are examples, not individual obligations.
- No coverage metric MUST be used as a substitute for test quality.

**Compliance**: each functional requirement has at least one traceable test, and relevant edge cases have explicit tests.

**Rationale**: ensure progressive confidence levels, detect defects early, and verify expected behavior.

### IV. Simple, Cohesive, and Justified Design

- Design MUST prioritize high cohesion, low coupling, readability, clear separation of concerns, minimal accidental complexity, and maintainability. KISS MUST be applied as a design principle.
- Simplicity means a clear, predictable structure consistent with applicable best practices. It DOES NOT mean ease of implementation or lack of rigor.
- Abstractions, design patterns, frameworks, and architectural mechanisms are tools, not goals. An abstraction SHOULD be introduced when it reduces complexity, improves maintainability, represents a real domain concept, facilitates planned variability, or avoids significant future cost. A design pattern MUST be used only when justified by a concrete need.
- Duplication MUST NOT be eliminated automatically if doing so introduces more complexity than the existing duplication.
- When an object-oriented paradigm is appropriate, encapsulation, abstraction, polymorphism, and delegation SHOULD be favored, and inheritance used only when it accurately represents the conceptual relationship and adds value.

**Compliance**: every introduced abstraction or pattern has a documented justification in the plan or review.

**Rationale**: maximize long-term maintainability and avoid premature abstractions and overengineering.

### V. Bounded Scope

- The agent MUST NOT implement features, behaviors, configurations, or changes not backed by at least one of these sources: specification, official project documentation, explicit user requirement, documented architectural decision, or explicit instruction from the current prompt. An unsolicited enhancement is out of scope.
- The agent MUST NOT perform unsolicited refactoring during the implementation of a story. Refactoring is improving internal structure without modifying external behavior, preserving existing functional guarantees.
- If the agent identifies a refactoring opportunity, it MUST explain where it is, why it considers it beneficial, and the expected benefits and risks, and MUST request a decision through the designated escalation mechanism (see Principle VIII). It MUST NOT perform it until receiving explicit authorization.

**Compliance**: every change in the diff corresponds to a documented requirement, task, or decision.

**Rationale**: maintain focus of changes and prevent regressions caused by unexpected "enhancements".

### VI. Controlled Technical Debt

- Detected technical debt MUST be identified and prioritized.
- During feature implementation, unrelated technical debt MUST NOT derail ongoing work.
- When there is no active feature, relevant technical debt SHOULD be addressed as soon as reasonably possible according to its priority and impact.
- Debt resolution involving refactoring MUST be processed as explicitly specified and authorized work, in accordance with Principle V.

**Compliance**: a technical debt registry exists with priority and impact for each item.

**Rationale**: acknowledge that technical debt exists and manage it without disrupting active stories.

### VII. Traceability, Consistency, and Documentation

- When the project uses engineering artifacts, consistency MUST be maintained across the chain: requirements → specification → design/plan → tasks → implementation → tests → documentation.
- Any modification affecting an artifact MUST trigger a review of dependent artifacts where applicable.
- If the agent detects a contradiction between artifacts, it MUST NOT hide it or resolve it unilaterally when it involves a product, domain, or architectural decision, and MUST escalate it (Principle VIII).
- Documentation MUST preserve all information necessary to understand, maintain, verify, or evolve the system: decisions, constraints, requirements, models, and trade-offs. Code comments SHOULD be used only when providing information that the code itself does not make evident.

**Compliance**: each task references its originating requirement, and a change in an artifact records the review of its dependents.

**Rationale**: keep artifacts coherent over time so they remain a reliable source of knowledge.

### VIII. Evidence Over Assumption and Human Escalation

- The agent MUST prioritize available evidence (repository, specifications, documentation, tests, configuration, and tooling) before assuming behavior.
- When a statement cannot be reasonably verified, the agent MUST state so.
- In the presence of significant ambiguity, a conflict between sources, or more than one technically valid interpretation that could alter behavior, the agent MUST halt the affected implementation and escalate the decision to a human via the designated mechanism, presenting: the detected ambiguity, possible interpretations, the consequences of each, and its technical recommendation.
- Implementation can ONLY proceed once the ambiguity is resolved.
- The concrete escalation mechanism is declared in Additional Constraints.

**Compliance**: every product, domain, or architectural decision made in the presence of ambiguity is recorded with its resolution.

**Rationale**: prevent the agent from fabricating context or unilaterally making decisions that require human judgment.

### IX. Engineering Over Convenience

- The agent's decisions MUST prioritize correctness, maintainability, traceability, verifiability, and architectural consistency over immediate convenience.
- Implementation speed DOES NOT justify skipping specification, validation, tests, review, or established architectural criteria.

**Compliance**: no quality gate of the Development Workflow is bypassed due to deadlines without a documented exception explicitly approved by a human.

**Rationale**: prevent the structural quality of the project from degrading in exchange for short-term speed.

### X. Full-Stack End-to-End Integrity (Vertical Slices)

- A feature specification MUST NOT isolate frontend or backend delivery without specifying and implementing its counterpart (vertical slice).
- The agent MUST NOT accept, specify, plan, or implement a user story or feature that exclusively targets only the frontend or only the backend without direct, explicit human authorization.
- If an incoming feature request or draft specification attempts to implement a single layer in isolation, the agent MUST immediately halt execution and escalate the decision to the human user via the designated escalation mechanism (`grill-me`) to clarify the true end-to-end intent and contracts before any planning or implementation proceeds.
- No automatic exceptions are permitted for setup, foundational, or infrastructure tasks; any single-tier work MUST be explicitly approved by the human user.

**Compliance**: every approved specification defines both frontend and backend scope, contracts, and verification criteria, or includes an explicit, documented human authorization for single-tier execution.

**Rationale**: prevent fragmented, non-functional silos and orphan user stories where frontend interfaces lack functional backend contracts or backend endpoints lack frontend consumer verification.

### XI. Role-Scoped Visual Governance and Non-Regressive UI Refactoring

- Every user-facing role interface MUST strictly implement the typography, semantic color palette, spacing, and WCAG 2.2 AA contrast rules defined in its corresponding role-scoped design contract.
- Visual implementations MUST uphold intentional craft and MUST NOT introduce generic boilerplate patterns (default fallback typography, generic purple-to-blue gradients, nested cards-in-cards, or low-contrast text on colored surfaces).
- Accessibility standards (WCAG 2.2 AA contrast) and anti-boilerplate prohibitions MUST take precedence over any conflicting token in a design contract. If a design contract contains a conflicting or non-compliant token, UI implementation for that role MUST halt and escalate for design-contract remediation before proceeding.
- UI refactoring MUST preserve existing API contracts, client-side state business logic, and behavioral test guarantees without altering or weakening existing test assertions.

**Compliance**: every UI change references its governing role design contract, passes visual critique and WCAG 2.2 AA contrast checks, and passes the full behavioral test suite with zero regressions or altered API and business-logic contracts.

**Rationale**: ensure cohesive, accessible, role-tailored interfaces while protecting verified end-to-end functionality from regressions during visual refactoring.

## Additional Constraints

This section contains the mechanisms and decisions declared by the project. They are NOT universal principles: they may be modified without altering the Fundamental Principles, although they remain subject to the amendment procedure.

- **Human escalation mechanism**: the `grill-me` skill. Any reference to "designated escalation mechanism" in the principles resolves to this skill.
- **Technological constraints (Frontend & Visual Governance — EasyEat)**:
  - **Role-Scoped Design Contracts**: Every EasyEat role UI (`Comensal`, `Mostrador`, `Chef`, `Admin`) MUST strictly implement the typography, semantic color palette, spacing, and WCAG 2.2 AA contrast rules defined in `.specify/memory/designs/<role>.DESIGN.md` (`comensal.DESIGN.md`, `mostrador.DESIGN.md`, `chef.DESIGN.md`, `admin.DESIGN.md`).
  - **Shared & Authentication Views**: Public and diner authentication/recovery routes MUST inherit `comensal.DESIGN.md`; staff authentication and role-redirect routes MUST inherit `admin.DESIGN.md` or their target role's `.DESIGN.md`.
  - **Zero AI-Slop Policy**: Default `Inter` typography, generic purple-to-blue gradients, nested cards-in-cards, and low-contrast gray text on colored surfaces are prohibited. All UI work MUST be audited via Impeccable `/critique`.
  - **Conflict Precedence**: WCAG 2.2 AA contrast and Zero AI-Slop rules supersede conflicting tokens in `.specify/memory/designs/<role>.DESIGN.md`. Known defects (`Inter` in `comensal.DESIGN.md`; `#111827` text on `#111111` surface in `mostrador.DESIGN.md`) MUST be remediated before implementing UI for those roles.
  - **Business Logic & Test Invariant**: UI refactoring MUST NOT alter Axios API contracts, state business logic, or break any existing Vitest + React Testing Library test suites from specs `001`–`023`.

## Development Workflow and Quality Gates

Minimum process prior to implementing a user story, in this order:

1. defined constitution;
2. functional specification (including full-stack vertical slice verification or human escalation in case of single-tier requests);
3. ambiguity resolution;
4. technical planning, including the compliance verification with this constitution;
5. specification quality checklist;
6. task decomposition;
7. synchronization with the work management system, where applicable;
8. consistency and coverage analysis prior to implementation.

Atomic, traceable, and reversible delivery:

- Each user story MUST be developed on an independent branch. When the workflow creates a branch per feature, each feature MUST contain a single user story.
- Changes MUST be organized into atomic, traceable, and reasonably reversible commits. Each commit MUST represent a logical unit of change, maintain a clear relationship with the corresponding specification or task, have a descriptive message aligned with versioning best practices, and avoid mixing functional changes, refactorings, and unrelated modifications.
- A user story can ONLY be considered integrated after passing the Pull Request and validation defined by the project.
- For any change affecting UI components or styles, integration additionally requires passing the Impeccable `/critique` visual audit against the governing `.DESIGN.md` (with WCAG 2.2 AA and Zero AI-Slop precedence) and passing the full Vitest + React Testing Library test suites (specs `001`–`023`) with unchanged Axios API contracts and state business logic.

## Governance

- **Technology independence**: principles MUST be expressed in terms of engineering and behavior, not specific technologies. This constitution MUST NOT mandate a language, framework, database, architecture, pattern, cloud provider, or tool, unless explicitly declared by the project in Additional Constraints. Concrete technological decisions are defined in specification and planning artifacts.
- **Precedence**: the constitution takes precedence over any other project practice in the event of a conflict.
- **Amendment procedure**: any modification MUST be documented, justified, and approved, and include a migration plan when affecting existing artifacts.
- **Semantic versioning**: MAJOR for incompatible principle removals or redefinitions; MINOR for adding principles or substantial expansion; PATCH for clarifications, wording, and non-semantic corrections.
- **Periodic compliance review**: MUST be performed in each feature planning cycle via the plan's Constitution Check, and at least once per quarter across the entire project, verifying that plans and reviews comply with the principles.
- **Mandatory principles vs. project decisions**: a mandatory principle applies regardless of technology and domain, and resides in Fundamental Principles. Any decision depending on a specific technology, tool, or context resides in Additional Constraints or in the specification and plan artifacts.

**Version**: 2.1.0 | **Ratified**: 2026-10-06 | **Last Amended**: 2026-11-21
