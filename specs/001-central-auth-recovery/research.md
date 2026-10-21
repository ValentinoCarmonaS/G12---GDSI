# Research: Central Authentication Service and Password Recovery

**Feature**: `001-central-auth-recovery` | **Date**: 2026-10-17 | **Phase**: 0

All decisions below were resolved via the `/grill-me` interview session conducted during
`/speckit-plan` execution. No outstanding NEEDS CLARIFICATION items remain.

---

## Decision Log

### D-001 · Build Tool

- **Decision**: Maven (`pom.xml`)
- **Rationale**: Standard in academic and corporate Java environments; Spring Boot's own
  parent POM (`spring-boot-starter-parent`) integrates seamlessly; extensive plugin
  ecosystem; lower cognitive overhead for greenfield projects.
- **Alternatives considered**: Gradle (Kotlin DSL) — more expressive build scripts, but
  higher learning curve and less universal in the team's context. Rejected for MVP simplicity.

---

### D-002 · Project Origin

- **Decision**: Greenfield project initialized at `easyeat/backend/` within the SDD monorepo.
- **Rationale**: No prior backend exists. Clean slate prevents legacy coupling. Monorepo
  co-locates docs (`EasyEat - Docs/`), specs (`specs/`), and implementation (`easyeat/backend/`).
- **Alternatives considered**: Separate repository — increases CI/CD complexity for a
  student/academic project. Root-level backend — no namespace isolation for future products.

---

### D-003 · Testing Strategy

- **Decision**: JUnit 5 + Mockito (unit tests) + Testcontainers with PostgreSQL container
  (integration tests) + Spring Security Test (`MockMvc`, `@WithMockUser`) for filter-level
  JWT validation.
- **Rationale**: Testing against real PostgreSQL avoids H2 compatibility gaps with
  PostgreSQL-specific DDL (UUID generation via `gen_random_uuid()`, `TIMESTAMPTZ`,
  `CHECK` constraints). Flyway migrations run against the same dialect in CI and production.
  Spring Security Test enables verifying that the `JwtAuthenticationFilter` correctly
  rejects/accepts requests without running a full server.
- **Alternatives considered**:
  - H2 in-memory — rejected: Flyway migrations with PostgreSQL-specific syntax fail on H2
    without dialect hacks; creates false confidence.
  - Mockito-only — insufficient: no coverage of JPA queries, Flyway schema evolution, or
    database constraint enforcement.

---

### D-004 · Email Testing

- **Decision**: `JavaMailSender` mocked with Mockito for both unit and integration tests.
  No embedded SMTP server in Sprint 1.
- **Rationale**: Simplest approach for MVP. Email delivery correctness is infrastructure-level
  and outside the functional scope of this sprint.
- **Technical debt logged**: GreenMail (embedded SMTP server) should be added in a future
  sprint when the email template contract needs formal validation. Deferred quality improvement.
- **Alternatives considered**: GreenMail — more realistic but adds complexity for marginal
  gain in Sprint 1.

---

### D-005 · JWT Library

- **Decision**: `io.jsonwebtoken:jjwt-api:0.12.x` + `jjwt-impl` + `jjwt-jackson`
- **Rationale**: jjwt 0.12.x is the current stable release with a fluent builder API, native
  HMAC-SHA256 (HS256) support, and active maintenance. The three-artifact split enforces
  clean dependency inversion — the application compiles against `jjwt-api`; implementation
  is a runtime-only detail.
- **Version pin**: `0.12.6` (latest stable at planning date).
- **Alternatives considered**:
  - `com.auth0:java-jwt:4.x` — equally valid; jjwt favored for team familiarity.
  - `io.jsonwebtoken:jjwt:0.9.x` — deprecated API, not suitable for new projects.

---

### D-006 · Password Reset Token Storage

- **Decision**: Separate table `password_reset_tokens` (FK → `users(id)` ON DELETE CASCADE).
- **Rationale**: Keeps `users` single-responsibility. Enables efficient audit of token history
  (PENDING → CONSUMED/REVOKED transitions). Future extension does not require schema changes
  to `users`.
- **Alternatives considered**:
  - Columns in `users` — simpler but couples token lifecycle to the user row; no history;
    concurrent reset requests overwrite without a trace.
  - Redis TTL store — no Redis in the MVP stack.

---

### D-007 · API URL Structure

- **Decision**:
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/password-reset/request`
  - `POST /api/v1/auth/password-reset/confirm`
- **Rationale**: `/api` prefix distinguishes REST from static resources. `/v1` enables
  non-breaking API evolution. Sub-resource paths express resource + action clearly.
- **Alternatives considered**: Paths without versioning — breaks backward compatibility
  without a migration window.

---

### D-008 · Package Base

- **Decision**: `com.easyeat` as the root package; auth module at `com.easyeat.auth.*`.
- **Structure**: Feature-based packages (by feature, not by layer). All auth classes
  colocate under `com.easyeat.auth.{controller, dto, entity, repository, service, security}`.
- **Rationale**: Clean product domain namespace. Feature-based organization maximizes cohesion.
- **Alternatives considered**: `com.gdsi.easyeat` — couples namespace to academic context.

---

### D-009 · Backend Physical Location

- **Decision**: `easyeat/backend/` within the SDD monorepo.
- **Rationale**: Monorepo simplifies cross-referencing specs ↔ implementation. `easyeat/`
  namespace isolates this product from future products in the same SDD repo.
- **Alternatives considered**: Separate repository — higher complexity for spec traceability.

---

### D-010 · Rate Limiting

- **Decision**: Caffeine in-memory cache with two independent limiters:
  1. Per-email login lock: 5 failed attempts / 15-minute sliding window.
  2. Per-IP login spray: 50 failed attempts / 1-hour window.
  Same strategy applied to password reset requests (FR-013).
- **Implementation pattern**:
  - `Cache<String, AtomicInteger>` with `expireAfterWrite(15, MINUTES)` for email counter.
  - Rate check occurs BEFORE password verification (prevents timing oracle on lockout state).
  - `RemovalListener` logs lockout audit events (SC-008).
- **Technical debt logged**: Caffeine is per-process. Horizontal scaling requires Redis.
- **Alternatives considered**: Resilience4j — distributed but requires additional infrastructure.

---

### D-011 · JWT Secret Management

- **Decision**: Environment variable `JWT_SECRET` (minimum 256 bits), injected via
  `application.yml` with `${JWT_SECRET}` Spring placeholder.
- **Test environment**: Fixed 256-bit hex string in `application-test.yml` (tracked in repo
  for reproducibility — NOT a production secret).
- **Rationale**: Secrets MUST NOT be hardcoded in source control (12-factor). Minimum 256
  bits required for HS256 HMAC security margin (RFC 7518 §3.2).
- **Alternatives considered**: Random-on-start — invalidates all tokens on restart. Hardcoded
  — security violation.

---

### D-012 · Spring Configuration Profiles

- **Decision**: Three layers:
  - `application.yml` — shared defaults (JWT TTL, BCrypt cost, rate-limit config).
  - `application-dev.yml` — local dev database (not committed with credentials).
  - `application-test.yml` — Testcontainers JDBC URL + fixed `JWT_SECRET`.
- **Rationale**: Fidelity (tests against real PostgreSQL) + isolation (no cross-contamination
  between dev and test).
- **Alternatives considered**: Single yml — no secret isolation. H2 for tests — rejected.

---

## Technology Version Pinning

| Dependency | Version | Managed By |
|---|---|---|
| Java | 21 (LTS) | Project requirement |
| Spring Boot | 3.3.4 | Parent POM |
| Spring Security | 6.3.x | Spring Boot 3.3.4 BOM |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | Explicit `<dependency>` |
| Caffeine | 3.1.x | Spring Boot BOM |
| Flyway | 10.x | Spring Boot BOM |
| PostgreSQL JDBC | 42.x | Spring Boot BOM |
| Testcontainers BOM | 1.20.x | Explicit BOM import |
| JUnit 5 | 5.10.x | Spring Boot BOM |
| Mockito | 5.x | Spring Boot BOM |

---

## Open Technical Debt (Documented, Deferred)

| ID | Item | Priority | Trigger to Resolve |
|---|---|---|---|
| TD-001 | GreenMail for embedded SMTP verification in integration tests | Low | When email template HTML/content requires formal validation |
| TD-002 | Redis-backed distributed rate limit state | Medium | When horizontal scaling (2+ instances) is required |
| TD-003 | `Retry-After` header dynamic calculation on rate-limit response | Low | When API clients need machine-readable backoff timing |
