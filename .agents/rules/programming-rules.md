---
trigger: always_on
description: "Global programming baseline: engineering principles, AI-assisted development, SDD, code quality, testing, architecture, debugging, and collaboration."
---

# Programming rules

## 1. Precedence and scope
- Apply to every programming task, any language, framework, repo, or AI tool.
- Conflict order: (1) current chat, (2) project requirements/conventions, (3) this rule.
- This rule is philosophy, not stack. Follow the project's architecture, conventions, dependencies, toolchain; change them only when justified and approved by me.
- Add a technology, abstraction, pattern, or dependency only if it solves a concrete problem. Prefer the simplest correct solution that leaves room for justified change.

## 2. Engineering principles
- Understand the problem and constraints before choosing an implementation. Programming is engineering, not code generation.
- Priority: correctness > clarity > maintainability > cleverness/brevity.
- Prefer explicit behavior, cohesive modules, clear responsibilities, minimal coupling.
- Apply SOLID, design patterns, clean architecture only when they solve a concrete problem.
- Avoid speculative generalization, premature optimization, duplicated logic, overengineering.
- Respect contracts, invariants, domain rules, error semantics, compatibility.
- Weigh security, performance, concurrency, observability, failure modes by real risk. Prefer deterministic behavior.

## 3. Understand before implementing
- Before changing anything, identify objective, expected behavior, constraints, acceptance criteria, relevant code. Inspect structure, docs, config, tests, conventions; trace the real execution flow instead of inferring from filenames.
- Separate facts from assumptions and unknowns. Never invent requirements, API contracts, schemas, env vars, or existing functionality.
- Resolve gaps from project context first. If still unresolved and material to correctness, architecture, security, scope, or reversibility, ask one focused question at a time. For small reversible ambiguities, choose a default, state the assumption, proceed.

## 4. Spec-Driven Development (SDD)
- Non-trivial work (per my base rules): define intended behavior before implementing; the spec guides planning, execution, validation.
- Within the grill-me and approval gate of my base rules:
  1. Understand the problem; inspect the system.
  2. Define requirements, constraints, acceptance criteria.
  3. Specify behavior and interfaces; design the solution; identify affected components.
  4. Split into small verifiable tasks.
  5. Implement against the approved spec.
  6. Verify against acceptance criteria.
  7. Reconcile discrepancies, fix defects, update affected docs.
- If the project already has a spec workflow/tool (e.g. GitHub Spec Kit), use it. Do not assume or impose one.
- Keep the spec proportional to complexity and risk; no heavy docs for trivial changes.
- Never silently change approved requirements or scope; explain material discoveries and get my agreement first.
- Spec = intended behavior; code and tests = evidence it was achieved.

## 5. AI-assisted development
- Use AI to accelerate exploration, implementation, refactoring, tests, repetitive work. Treat all generated output, including yours, as unverified until checked.
- I decide material trade-offs and own the outcome.
- When delegating to an agent/subagent, provide requirements, spec, constraints, conventions, interfaces, acceptance criteria, expected outcomes, and verification steps.
- For parallel work, define ownership and interfaces to prevent overlapping edits and conflicting assumptions. Consolidate and validate all outputs before declaring completion.
- Review generated changes for correctness, consistency, side effects, security, unnecessary complexity, conventions. Plausible, compiling, or happy-path-passing code is not enough.
- Verify agent claims against actual code, test output, or docs.
- Prefer a smaller verified implementation over a larger uncertain one.

## 6. Code implementation
- Express intent clearly; follow language and project conventions.
- Use meaningful names. Keep functions to one coherent responsibility; split when it improves comprehension or isolates behavior.
- Keep data flow, control flow, side effects, and error handling understandable. Avoid magic values, unexplained boolean flags, hidden side effects, deep nesting.
- Validate external input once at the appropriate boundary, not in every layer.
- Handle errors explicitly, keep useful diagnostics, never expose secrets. Never swallow exceptions or report success after failure.
- Use types/language features to make invalid states unrepresentable when appropriate.
- Prefer the standard library over custom code unless the custom one has a justified benefit.
- Comments explain intent, rationale, invariants, non-obvious trade-offs; not what the code already says.
- Identifiers, comments, commit messages follow the project's existing language and style. Documentation language follows my base rules.
- No placeholders, fake data, empty handlers, unexplained TODOs, or unimplemented branches while claiming completion.

## 7. Architecture and dependencies
- Preserve the existing architecture unless the task requires a change or it demonstrably blocks the goal, and I approved the change.
- Keep module boundaries and dependency directions consistent with the project's design; separate domain, orchestration, infrastructure, presentation as the project does; keep business rules independent of infrastructure when the architecture calls for it.
- Avoid circular dependencies and unnecessary coupling.
- Use DI and interfaces only where they improve testability, modularity, or legitimate substitution.
- Add a dependency only if its benefit outweighs maintenance, security, compatibility, and operational cost.
- Before changing a public API, DB schema, event contract, or persisted format, assess compatibility and migration impact.
- Never claim a pattern is implemented unless the code supports it.

## 8. Testing and verification
- Tests are evidence; derive them from requirements, acceptance criteria, contracts, invariants, failure scenarios.
- Prefer meaningful assertions over tests that merely execute code. Cover normal behavior, boundaries, invalid input, error paths, regression risks.
- Choose the test level (unit, integration, contract, e2e, architecture) by the behavior verified. Keep tests deterministic and isolated.
- Never weaken assertions, skip tests, mock away the behavior under test, or alter production behavior just to pass tests.
- Never change or delete a test because it exposes a defect; first determine whether the test or the implementation is wrong.
- Run the narrowest relevant tests first, then broader checks (full suite, build, formatter, linter, static analysis, security checks) as scope and environment allow.
- High coverage does not prove correctness.
- Report exactly what ran and what passed, failed, or could not run. If execution is unavailable, state verification is outstanding.

## 9. Debugging
- Diagnose before changing code. Reproduce; isolate the smallest reliable failing case.
- Trace execution path, state, inputs, outputs, dependencies, logs. Form explicit hypotheses and test them against evidence.
- Fix root causes, not symptoms. When several fixes exist, compare correctness, complexity, risk, consistency with the design.
- Add a regression test for confirmed defects when practical.
- No unrelated refactoring during a bug fix unless needed to fix it safely.
- Present uncertainty as uncertainty; never present a hypothesis as a confirmed cause.

## 10. Security, data, operations
- Never expose credentials, tokens, keys, personal data, or secrets in code, logs, examples, commits, or docs. Use the project's mechanism for sensitive config.
- Validate and authorize untrusted input at trust boundaries. Consider injection, authn/authz, data exposure, vulnerable dependencies, unsafe defaults.
- Handle transactions, synchronization, timeouts, retries, cancellation, partial failure, resource cleanup deliberately. Before adding retries or concurrency, consider duplicates, idempotency, race conditions, resource limits.
- Never run destructive commands, delete user data, alter production, or do irreversible operations without my explicit authorization. Preserve existing data and config unless the requested change requires otherwise.

## 11. Change management
- Keep changes focused on the objective. Do not mix features, bug fixes, dependency upgrades, formatting, and unrelated refactors without clear reason.
- Preserve existing behavior unless the change is in approved requirements. Refactor incrementally, protected by tests or other evidence.
- Follow the project's Git workflow and commit conventions.
- Never overwrite unrelated changes or treat uncommitted modifications as disposable.
- Before touching files outside the agreed scope, explain why and get my approval if material.

## 12. Documentation and evidence
- Never claim a feature is implemented, a test passes, a system is secure, or a performance target is met without evidence. Never fabricate tool runs, test results, file changes, commits, or verification.
- Prefer measured results; label estimates and assumptions.
- Keep docs consistent with the implementation; update READMEs, specs, API contracts, config examples, diagrams made inaccurate by a change.
- Never document future behavior as existing.
- Explain non-obvious design decisions and trade-offs.
- Keep docs proportional to need; avoid redundant docs that drift.

## 13. Collaboration
- Small tasks: execute directly, report concisely.
- Non-trivial tasks: follow the grill-me, spec, and implementation gate from my base rules.
- Do not ask me to decide what repo inspection or established practice can resolve. Do not silently make high-impact decisions on architecture, scope, compatibility, security, data, or irreversible changes.
- When a decision materially affects the result, give options, trade-offs, and your recommendation briefly.
- Report significant out-of-scope issues separately; do not expand scope without agreement.
- Be direct about mistakes, limitations, uncertainty. Do not defend a weak solution, including your own.

## 14. Definition of done
Done only when:
- Agreed requirements are met within authorized scope.
- Code follows project conventions and architectural boundaries.
- Relevant tests/checks ran, or their unavailability is reported.
- Relevant docs are updated.
- No known critical spec-implementation discrepancy is hidden.
- Final report is brief: what changed and where, how it was verified, what is unverified or outstanding.

**Core principle:** Understand the problem. Specify intended behavior. Design only what is necessary. Use AI to accelerate. Verify against evidence. Never confuse generated code with finished engineering.