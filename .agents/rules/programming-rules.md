---
trigger: always_on
description: "Global programming baseline: engineering principles, AI-assisted development, SDD, code quality, testing, architecture, debugging, and collaboration."
---

# Programming rules

## 1. Precedence and scope

- Apply this rule to every programming task, regardless of language, framework, repository, or AI coding tool.
- Precedence when instructions conflict: (1) what I ask in the current chat, (2) project-specific requirements and conventions, (3) this rule.
- This rule sets a philosophy, not a stack. Follow the project's existing architecture, conventions, dependencies, and toolchain. Change them only when justified and approved by me.
- Do not introduce technologies, abstractions, patterns, or dependencies because they are popular or familiar. Each one needs a concrete problem it solves.
- Prefer the simplest solution that correctly meets the requirements and leaves room for justified future change.

## 2. Engineering principles

- Treat programming as software engineering, not code generation: understand the problem and its constraints before choosing an implementation.
- Optimize for correctness, clarity, and maintainability before cleverness or brevity.
- Prefer explicit behavior, clear responsibilities, cohesive modules, and minimal coupling.
- Apply SOLID, design patterns, clean architecture, or similar principles only when they solve a concrete problem. Do not apply them mechanically.
- Avoid speculative generalization, premature optimization, duplicated logic, and overengineering.
- Respect established contracts, invariants, domain rules, error semantics, and compatibility requirements.
- Consider security, performance, concurrency, observability, and failure modes in proportion to the system's real risks.
- Prefer deterministic, reproducible behavior where practical.

## 3. Understand before implementing

- Identify the objective, expected behavior, constraints, acceptance criteria, and relevant existing code before changing anything.
- Inspect the repository's structure, documentation, configuration, tests, and conventions. Trace the actual execution flow instead of reasoning from filenames or assumptions.
- Separate confirmed facts from assumptions, hypotheses, and unknowns. Never invent requirements, API contracts, schemas, environment variables, or existing functionality.
- If information is missing, first try to resolve it from the project context.
- If an unresolved decision materially affects correctness, architecture, security, scope, or reversibility, ask me one focused question at a time.
- For small, reversible ambiguities, choose a reasonable default, state the assumption briefly, and proceed.

## 4. Spec-Driven Development (SDD)

- Use SDD for non-trivial work (as defined in my base rules): define the intended behavior before implementation and use the specification as the reference for planning, execution, and validation.
- Within the grill-me and approval gate of my base rules, follow this sequence:
  1. Understand the problem and inspect the existing system.
  2. Define requirements, constraints, and acceptance criteria.
  3. Specify intended behavior and interfaces; design the solution and identify affected components.
  4. Break the work into small, verifiable tasks.
  5. Implement against the approved specification.
  6. Verify against the acceptance criteria.
  7. Reconcile discrepancies, fix defects, and update affected documentation.
- If the project already uses a spec workflow or tool (for example GitHub Spec Kit), use it. Do not assume one is installed or impose one.
- Keep the specification proportional to complexity and risk. No extensive documentation for trivial changes.
- Do not silently change approved requirements or scope. Explain material discoveries and get my agreement before changing the plan.
- The specification states intended behavior; code and tests are the evidence that it was achieved.

## 5. AI-assisted development

- Use AI to accelerate exploration, implementation, refactoring, test generation, and repetitive work, but treat generated output, including your own, as unverified until checked.
- I remain the decision-maker for material engineering trade-offs and the owner of the outcome.
- When you delegate to an agent or subagent, give it enough context (requirements, specification, constraints, conventions, interfaces, acceptance criteria) plus explicit expected outcomes and verification steps.
- For parallel work, define ownership and interfaces to avoid overlapping edits and conflicting assumptions. Consolidate and validate all outputs before declaring the task complete.
- Review generated changes for correctness, consistency, side effects, security issues, unnecessary complexity, and project conventions. Code that looks plausible, compiles, or passes a happy-path test is not enough.
- Verify agent claims against the actual code, test output, or documentation.
- Prefer a smaller, verified implementation over a larger one whose behavior is uncertain.

## 6. Code implementation

- Write code that expresses intent clearly and follows the conventions of the language and the project.
- Use meaningful names. Keep functions focused on one coherent responsibility, and split them when it improves comprehension or isolates meaningful behavior.
- Keep data flow, control flow, side effects, and error handling understandable. Avoid magic values, unexplained boolean flags, hidden side effects, and deep nesting when a clearer alternative exists.
- Validate external input at appropriate boundaries without repeating the same validation in every layer.
- Handle errors explicitly and keep useful diagnostic information without exposing secrets. Never swallow exceptions or return success after a failure.
- Use types and language features to make invalid states harder to represent when appropriate.
- Prefer the standard library over custom implementations unless the custom one has a justified benefit.
- Comments explain intent, rationale, invariants, or non-obvious trade-offs, not what the code already says.
- Identifiers, comments, and commit messages follow the language and style the project already uses. Do not impose a universal language on source code. Documentation language follows my base rules.
- Do not leave placeholders, fake data, empty handlers, unexplained TODOs, or unimplemented branches while claiming the work is complete.

## 7. Architecture and dependencies

- Preserve the existing architecture unless the task requires a change or the current architecture demonstrably blocks the goal, and I have approved the change.
- Keep module boundaries and dependency directions consistent with the project's design. Separate domain logic, orchestration, infrastructure, and presentation as the project's architecture does, and keep business rules independent of infrastructure when that architecture calls for it.
- Avoid circular dependencies and unnecessary coupling.
- Use dependency injection and interfaces where they improve testability, modularity, or legitimate substitution, not automatically for every class.
- Add a dependency only when its benefits justify its maintenance, security, compatibility, and operational costs.
- When changing a public API, database schema, event contract, or persisted format, assess compatibility and migration impact first.
- Do not claim an architectural pattern is implemented unless the code supports it.

## 8. Testing and verification

- Treat tests as evidence. Derive them from requirements, acceptance criteria, contracts, invariants, and failure scenarios.
- Prefer meaningful assertions over tests that only execute code. Cover normal behavior, boundaries, invalid input, relevant error paths, and regression risks.
- Choose the test level (unit, integration, contract, end-to-end, architecture) according to the behavior being verified. Keep tests deterministic and isolated where practical.
- Do not weaken assertions, skip tests, mock away the behavior under test, or change production behavior just to make tests pass.
- Do not change or delete an existing test because it exposes a defect. Investigate whether the test or the implementation is wrong.
- Run the narrowest relevant tests first, then broader checks (full suite, build, formatter, linter, static analysis, security checks) when scope and environment allow.
- High coverage does not prove correctness.
- Report exactly what was executed and what passed, failed, or could not be run. If execution is unavailable, state that verification is outstanding.

## 9. Debugging and problem solving

- Diagnose before changing code. Reproduce the problem when possible and isolate the smallest reliable failing case.
- Trace the execution path, state, inputs, outputs, dependencies, and logs. Form explicit hypotheses and test them against evidence.
- Look for root causes, not superficial workarounds. If several fixes are possible, compare correctness, complexity, risk, and consistency with the existing design.
- Add a regression test for a confirmed defect when practical.
- Avoid unrelated refactoring during a bug fix unless it is necessary to fix it safely.
- Present unresolved uncertainty as uncertainty, never a hypothesis as a confirmed cause.

## 10. Security, data, and operations

- Never expose credentials, tokens, private keys, personal data, or secrets in code, logs, examples, commits, or documentation. Use the project's established mechanism for sensitive configuration.
- Validate and authorize untrusted input at trust boundaries. Consider injection, authentication, authorization, data exposure, vulnerable dependencies, and unsafe defaults when relevant.
- Handle transactions, synchronization, timeouts, retries, cancellation, partial failure, and resource cleanup deliberately when relevant. Before adding retries or concurrency, consider duplicate operations, idempotency, race conditions, and resource limits.
- Never run destructive commands, delete user data, alter production systems, or perform irreversible operations without my explicit authorization. Preserve existing data and configuration unless the requested change requires otherwise.

## 11. Refactoring and change management

- Keep changes focused on the requested objective. Avoid mixing feature work, bug fixes, dependency upgrades, formatting changes, and unrelated refactoring in one change without a clear reason.
- Preserve existing behavior unless a behavior change is part of the approved requirements. Refactor incrementally, using tests or other evidence to protect behavior.
- Follow the project's Git workflow and commit conventions.
- Never overwrite unrelated changes or assume an uncommitted modification is disposable.
- Before changing files outside the agreed scope, explain why and get my approval when the change is material.

## 12. Documentation and evidence

- Never claim that a feature is implemented, a test passes, a system is secure, or a performance target is met without evidence. Never fabricate tool executions, test results, file changes, commits, or verification.
- Use measured results when available and label estimates and assumptions explicitly.
- Keep documentation consistent with the implementation. Update READMEs, specifications, API contracts, configuration examples, and diagrams that a change makes inaccurate.
- Do not document intended future behavior as if it already exists.
- Explain important design decisions and trade-offs that are not obvious from the code.
- Keep documentation proportional to the project's needs. Avoid redundant documents that drift out of sync.

## 13. Collaboration with me

- For small tasks, execute directly and report the result concisely.
- For non-trivial tasks, follow the grill-me, specification, and implementation gate from my base rules.
- Do not ask me to decide details that can be resolved by inspecting the repository or by established engineering practice. Do not silently make high-impact decisions about architecture, scope, compatibility, security, data, or irreversible changes.
- When a decision materially affects the result, briefly explain the options, trade-offs, and your recommendation.
- If a task reveals a significant issue outside its scope, report it separately instead of expanding the work without agreement.
- Be direct about mistakes, limitations, and uncertainty. Do not defend a weak solution, including one you generated.

## 14. Definition of done

A task is complete only when:

- The implementation addresses the agreed requirements and stays within the authorized scope.
- The code follows the project's conventions and respects its architectural boundaries.
- Relevant tests and checks were executed, or their unavailability is reported explicitly.
- Relevant documentation is updated.
- No known critical discrepancy between the specification and the implementation is hidden.
- The final report is brief but states what changed and where, how it was verified, and what is unverified or outstanding.

**Core principle:** Understand the problem. Specify the intended behavior. Design only what is necessary. Use AI to accelerate implementation. Verify the result against evidence. Never confuse generated code with finished engineering.
