---
applyTo: "**/*.{ts,js,py,go,cs,java,rs}"
---

# Code Review Standards (Correctness, Safety & Performance)

When reviewing pull requests, focus strictly on code-level safety, correctness, efficiency, and maintainability:

## 1. Correctness & Precision
- **Data Types & Rounding**: Ensure monetary and precise calculations use exact numeric representations (`Decimal` or integer cents) to prevent IEEE 754 floating-point drift.
- **Null & Boundary Safety**: Ensure collections, optionals, and null/undefined values are guarded before access. Check for off-by-one errors in slices, pagination, and loops.
- **Pure Logic & Immutability**: Ensure domain calculations do not produce unintended side-effects or mutate inputs in-place unless explicitly designed.

## 2. Security & Defensive Code
- **Secrets & Credentials**: Flag any hardcoded tokens, passwords, keys, or sensitive connection strings.
- **Input Validation**: All external parameters, payload bodies, and query arguments must be validated and sanitized at the entrypoint.
- **Injection Safety**: SQL/database queries must use parameterized builders; shell executions must avoid unsanitized string interpolation.
- **Log Hygiene**: Ensure no PII, tokens, or sensitive payload data are logged.

## 3. Reliability & Exception Handling
- **Specific Exception Handling**: Never use bare `except:` or empty catch blocks that swallow errors silently.
- **Resource Lifecycle**: Ensure file handles, connections, and locks are safely released using context managers (`with`, `try-finally`, `using`).
- **Concurrency & State**: Flag race conditions when shared mutable state or in-memory stores are accessed across concurrent threads or async handlers.

## 4. Algorithmic Efficiency & Performance
- **Time Complexity**: Flag unnecessary nested loops ($O(N^2)$) where lookup maps/sets ($O(1)$) or greedy single-pass algorithms can be used.
- **Database & Query Efficiency**: Flag N+1 query patterns, unindexed filters, or queries loading full datasets into memory when streaming or pagination applies.
- **Hot-Path Allocations**: Avoid redundant object allocations, cloning, or heavy JSON re-parsing inside tight loops.

## 5. Code Maintainability
- **Single Responsibility**: Functions should have one clear purpose and avoid sprawling multi-page bodies.
- **No Dead Code**: Flag commented-out code blocks, leftover debug statements (`console.log`, `print`), or unused variables.
