# Tasks: Central Authentication Service and Password Recovery

**Feature**: `001-central-auth-recovery`
**Input**: Design documents from `specs/001-central-auth-recovery/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, `research.md`)
**Target Repository Path**: `easyeat/backend/`
**Testing Strategy**: Strict TDD ordering (write failing test first -> implement minimal code -> verify with Testcontainers/MockMvc).

---

## Phase 1: Setup (Shared Infrastructure) — [GitHub Issue #1](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/1)

**Purpose**: Project initialization, build configuration, and local runtime dependencies.

- [X] T001 Initialize Maven single-project build configuration in `easyeat/backend/pom.xml` with Java 21, Spring Boot 3.3.4 parent, Spring Security 6, JJWT 0.12.6, Caffeine, Flyway 10, PostgreSQL driver, and Testcontainers.
- [X] T002 [P] Configure local Docker services in `easyeat/backend/docker-compose.yml` for PostgreSQL 16 (port 5432) and Mailpit (SMTP port 1025, Web UI port 8025).
- [X] T003 [P] Configure base application configuration in `easyeat/backend/src/main/resources/application.yml` with JWT settings, BCrypt cost 12, rate limit settings, and RFC 9457 ProblemDetail enabled.
- [X] T004 [P] Configure local development profile in `easyeat/backend/src/main/resources/application-dev.yml` pointing to local Docker PostgreSQL and Mailpit SMTP.
- [X] T005 [P] Configure test profile in `easyeat/backend/src/test/resources/application-test.yml` with fixed test JWT signing secret and dynamic Testcontainers datasource.
- [X] T006 Create main Spring Boot application class in `easyeat/backend/src/main/java/com/easyeat/EasyEatApplication.java`.

---

## Phase 2: Foundational (Blocking Prerequisites) — [GitHub Issue #2](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/2)

**Purpose**: Core infrastructure, database migrations, security baselines, and shared error handling that MUST be complete before any user story implementation can begin.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T007 Create Flyway database migration `V1__create_users.sql` in `easyeat/backend/src/main/resources/db/migration/V1__create_users.sql` defining `users` table with UUID PK (`gen_random_uuid()`), unique lowercase email check, and role/tenant integrity constraint.
- [ ] T008 [P] Create Flyway database migration `V2__create_password_reset_tokens.sql` in `easyeat/backend/src/main/resources/db/migration/V2__create_password_reset_tokens.sql` defining `password_reset_tokens` table with FK to users (CASCADE) and unique index on `token_hash`.
- [ ] T009 [P] Implement `ClockConfig.java` in `easyeat/backend/src/main/java/com/easyeat/config/ClockConfig.java` providing an injectable `java.time.Clock` bean for deterministic time validation.
- [ ] T010 [P] Implement `User.java` JPA entity, `UserRole.java`, and `UserStatus.java` enums in `easyeat/backend/src/main/java/com/easyeat/auth/domain/User.java`.
- [ ] T011 [P] Implement `UserRepository.java` in `easyeat/backend/src/main/java/com/easyeat/auth/repository/UserRepository.java` with `findByEmail(String email)` and `existsByEmail(String email)`.
- [ ] T012 Implement baseline `SecurityConfig.java` in `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java` with `BCryptPasswordEncoder(12)`, stateless session creation policy, and permitAll for public `/api/v1/auth/**` endpoints.
- [ ] T013 Implement centralized RFC 9457 exception handler `GlobalExceptionHandler.java` in `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` returning `ProblemDetail` responses for validation errors, conflict, bad request, and unauthorized errors.

**Checkpoint**: Foundation ready. User story implementation can now proceed in priority order.

---

## Phase 3: User Story 1 - Comensal Registration (Priority: P1) [US1] 🎯 MVP — [GitHub Issue #3](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/3)

**Goal**: Enable new diners to create a platform account by submitting a valid, unique email address and a compliant password, receiving a confirmation and establishing their identity in the system.

**Independent Test**: Submit valid registration details and verify account is persisted with `role = COMENSAL`, `tenant_id = null`, `status = ACTIVE`, and hashed password; verify duplicate email returns 409 Conflict.

### Tests for User Story 1 (TDD - Write First)

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation.**

- [ ] T014 [P] [US1] Write failing unit tests for `@MaxBytes(72)` custom validator and registration DTO constraints in `easyeat/backend/src/test/java/com/easyeat/auth/validation/MaxBytesValidatorTest.java`.
- [ ] T015 [P] [US1] Write failing MockMvc WebMvc contract tests for `POST /api/v1/auth/register` (201 Created, 400 Bad Request for invalid password/email, 409 Conflict for duplicate email) in `easyeat/backend/src/test/java/com/easyeat/auth/controller/AuthControllerRegistrationTest.java`.

### Implementation for User Story 1

- [ ] T016 [US1] Implement `@MaxBytes` annotation and `MaxBytesValidator.java` in `easyeat/backend/src/main/java/com/easyeat/auth/validation/MaxBytes.java` verifying that UTF-8 byte length of passwords does not exceed 72 bytes.
- [ ] T017 [P] [US1] Implement registration DTOs `RegisterRequest.java` and `UserSummaryResponse.java` in `easyeat/backend/src/main/java/com/easyeat/auth/dto/RegisterRequest.java`.
- [ ] T018 [US1] Implement registration business logic in `AuthService.java` in `easyeat/backend/src/main/java/com/easyeat/auth/service/AuthService.java` normalizing email (`toLowerCase().trim()`), checking uniqueness, and hashing password with BCrypt cost 12.
- [ ] T019 [US1] Implement `register` endpoint in `AuthController.java` in `easyeat/backend/src/main/java/com/easyeat/auth/controller/AuthController.java` handling `POST /api/v1/auth/register` and returning 201 Created with `UserSummaryResponse`.
- [ ] T020 [US1] Write and run Testcontainers integration test in `easyeat/backend/src/test/java/com/easyeat/auth/integration/RegistrationIntegrationTest.java` verifying end-to-end comensal persistence, lowercase DB constraint adherence, and duplicate email rejection.

**Checkpoint**: User Story 1 is fully functional and testable independently (MVP deliverable).

---

## Phase 4: User Story 2 - Unified Login with JWT (Priority: P1) [US2] — [GitHub Issue #4](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/4)

**Goal**: Authenticate users of all platform roles (`COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`) via email and password, issuing a signed JWT with role and tenant claims, protected by rate limiting and anti-enumeration timing mitigation.

**Independent Test**: Submit valid credentials for each role and verify JWT is issued with correct claims; submit bad credentials/non-existent emails and verify timing-resistant 401; submit 5 bad credentials and verify 429 lockout.

### Tests for User Story 2 (TDD - Write First)

- [ ] T021 [P] [US2] Write failing unit tests for `JwtService` (token signing, claims extraction: `sub`, `role`, `tenant_id`, `token_version`, expiration validation, signature tamper rejection) in `easyeat/backend/src/test/java/com/easyeat/auth/security/JwtServiceTest.java`.
- [ ] T022 [P] [US2] Write failing unit tests for `RateLimitingService` (per-email 5-failure lockout, per-IP limit, tracking non-existent emails) in `easyeat/backend/src/test/java/com/easyeat/auth/security/RateLimitingServiceTest.java`.
- [ ] T023 [P] [US2] Write failing MockMvc tests for `POST /api/v1/auth/login` (200 OK with `AuthTokenResponse`, 401 Unauthorized for bad password/non-existent user, 403 Forbidden for SUSPENDED account, 429 Too Many Requests on lockout) in `easyeat/backend/src/test/java/com/easyeat/auth/controller/AuthControllerLoginTest.java`.

### Implementation for User Story 2

- [ ] T024 [P] [US2] Implement login DTOs `LoginRequest.java` and `AuthTokenResponse.java` in `easyeat/backend/src/main/java/com/easyeat/auth/dto/LoginRequest.java`.
- [ ] T025 [US2] Implement `JwtService.java` in `easyeat/backend/src/main/java/com/easyeat/auth/security/JwtService.java` using `jjwt` with HMAC-SHA256, 8-hour expiration, and deterministic claims (`sub`, `role`, `tenant_id`, `token_version`).
- [ ] T026 [US2] Implement in-process rate limiter `RateLimitingService.java` in `easyeat/backend/src/main/java/com/easyeat/auth/security/RateLimitingService.java` backed by Caffeine caches (per-email 5 attempts/15 min, per-IP 50 attempts/1 hr).
- [ ] T027 [US2] Implement login logic in `AuthService.java` in `easyeat/backend/src/main/java/com/easyeat/auth/service/AuthService.java` with rate limit check before credentials, dynamic `DUMMY_HASH` comparison on non-existent users for timing attack mitigation, status check (`ACTIVE`), and token issuance.
- [ ] T028 [US2] Implement `JwtAuthenticationFilter.java` in `easyeat/backend/src/main/java/com/easyeat/auth/security/JwtAuthenticationFilter.java` extracting Bearer token, verifying signature, and asserting that database `token_version` matches and account is `ACTIVE`.
- [ ] T029 [US2] Register `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter` in `SecurityConfig.java` in `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java`.
- [ ] T030 [US2] Implement `login` endpoint in `AuthController.java` in `easyeat/backend/src/main/java/com/easyeat/auth/controller/AuthController.java` handling `POST /api/v1/auth/login`.
- [ ] T031 [US2] Write and run Testcontainers integration tests in `easyeat/backend/src/test/java/com/easyeat/auth/integration/LoginIntegrationTest.java` verifying unified login for all 4 roles, account suspension blocking (403), rate-limiting lockout (429), and timing resistance.

**Checkpoint**: User Stories 1 AND 2 are fully functional and integrated.

---

## Phase 5: User Story 3 - Password Recovery for Comensales (Priority: P2) [US3] — [GitHub Issue #5](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/5)

**Goal**: Allow comensales to request an email password reset and complete the update with a secure, 15-minute single-use token, invalidating all prior active sessions upon successful reset.

**Independent Test**: Request reset for a known email, verify asynchronous email dispatch, confirm token with new password, and verify prior session tokens are rejected.

### Tests for User Story 3 (TDD - Write First)

- [ ] T032 [P] [US3] Write failing unit tests for `PasswordResetService` (15-minute token generation, SHA-256 hash storage, 60-second cooldown per email, atomic consumption, session revocation via `token_version` increment) in `easyeat/backend/src/test/java/com/easyeat/auth/service/PasswordResetServiceTest.java`.
- [ ] T033 [P] [US3] Write failing MockMvc tests for `POST /api/v1/auth/password-reset/request` (always 200 OK generic confirmation for existing and non-existent emails) and `POST /api/v1/auth/password-reset/confirm` (200 OK on success, 400 Bad Request on expired/consumed token) in `easyeat/backend/src/test/java/com/easyeat/auth/controller/PasswordResetControllerTest.java`.

### Implementation for User Story 3

- [ ] T034 [P] [US3] Implement `PasswordResetToken.java` JPA entity in `easyeat/backend/src/main/java/com/easyeat/auth/domain/PasswordResetToken.java`.
- [ ] T035 [US3] Implement `PasswordResetTokenRepository.java` in `easyeat/backend/src/main/java/com/easyeat/auth/repository/PasswordResetTokenRepository.java` with atomic query `consumeToken(tokenHash, now)` and revocation of prior unconsumed tokens for the user.
- [ ] T036 [P] [US3] Implement password recovery DTOs `PasswordResetRequest.java` and `PasswordResetConfirmRequest.java` in `easyeat/backend/src/main/java/com/easyeat/auth/dto/PasswordResetRequest.java`.
- [ ] T037 [US3] Implement `EmailService.java` in `easyeat/backend/src/main/java/com/easyeat/auth/service/EmailService.java` wrapping `JavaMailSender` with async reset email dispatch (`@Async`).
- [ ] T038 [US3] Implement `PasswordResetService.java` in `easyeat/backend/src/main/java/com/easyeat/auth/service/PasswordResetService.java` with anti-enumeration response, atomic consumption, password update, and `token_version` increment.
- [ ] T039 [US3] Implement recovery endpoints in `AuthController.java` in `easyeat/backend/src/main/java/com/easyeat/auth/controller/AuthController.java` for `POST /api/v1/auth/password-reset/request` and `POST /api/v1/auth/password-reset/confirm`.
- [ ] T040 [US3] Write and run Testcontainers integration tests in `easyeat/backend/src/test/java/com/easyeat/auth/integration/PasswordRecoveryIntegrationTest.java` verifying full reset lifecycle, double-consumption rejection, cooldown enforcement, and prior session token revocation.
- [ ] T041 [US3] Write concurrency test in `easyeat/backend/src/test/java/com/easyeat/auth/integration/ConcurrentResetConsumptionTest.java` executing concurrent confirm requests to prove atomic single-use token consumption under race conditions.

**Checkpoint**: User Stories 1, 2, and 3 are functional and independently testable.

---

## Phase 6: User Story 4 - Password Recovery for Administrators (Priority: P2) [US4] — [GitHub Issue #6](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/6)

**Goal**: Enable restaurant administrators to reset their passwords via email using the centralized recovery mechanism, ensuring tenant access is preserved while invalidating prior admin sessions.

**Independent Test**: Initiate reset flow with an administrator account's email, complete password update, validate that previous admin JWTs are rejected, and verify new login asserts correct tenant scope.

### Tests for User Story 4 (TDD - Write First)

- [ ] T042 [P] [US4] Create dev/test seed migration `V2__seed_dev_tenant.sql` in `easyeat/backend/src/main/resources/db/dev/V2__seed_dev_tenant.sql` with synthetic tenant ID and admin user (active only in dev/test profiles).
- [ ] T043 [US4] Write failing integration test for administrator recovery in `easyeat/backend/src/test/java/com/easyeat/auth/integration/AdminPasswordRecoveryIntegrationTest.java` verifying that admin reset flows through centralized recovery service and invalidates active admin session tokens.

### Implementation and Verification for User Story 4

- [ ] T044 [US4] Verify and configure administrator password reset end-to-end integration in `easyeat/backend/src/test/java/com/easyeat/auth/integration/AdminPasswordRecoveryIntegrationTest.java` ensuring `tenant_id` claim is preserved on subsequent logins after reset.

**Checkpoint**: All 4 user stories (COM-1.1.1, COM-1.2.1, COM-1.3.1, ADM-1.2.1) are complete and verified.

---

## Phase 7: Polish & Cross-Cutting Concerns — [GitHub Issue #7](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/7)

**Purpose**: Security audit logging, proxy header protection, contract conformance, and full end-to-end quickstart validation.

- [ ] T045 [P] Implement `SecurityAuditLogger.java` in `easyeat/backend/src/main/java/com/easyeat/auth/security/SecurityAuditLogger.java` recording structured JSON audit events (LOGIN_SUCCESS, LOGIN_FAILURE, LOCKOUT, RESET_REQUEST, RESET_CONFIRM) with masked emails and client IPs.
- [ ] T046 Configure trusted proxy headers strategy (`server.forward-headers-strategy=native` and `server.tomcat.remoteip.internal-proxies`) in `easyeat/backend/src/main/java/com/easyeat/config/ProxyConfig.java` to prevent client IP spoofing.
- [ ] T047 Write OpenAPI contract conformance test in `easyeat/backend/src/test/java/com/easyeat/auth/contract/OpenApiContractTest.java` asserting that live endpoint schemas and status codes conform to `specs/001-central-auth-recovery/contracts/openapi.yaml`.
- [ ] T048 Execute full test suite (`mvn clean verify`) and execute quickstart validation scenarios from `specs/001-central-auth-recovery/quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1: Setup (T001–T006)
       ↓
Phase 2: Foundational (T007–T013)  ← Blocks all stories
       ↓
Phase 3: User Story 1 (T014–T020)  ← MVP Target
       ↓
Phase 4: User Story 2 (T021–T031)
       ↓
Phase 5: User Story 3 (T032–T041)
       ↓
Phase 6: User Story 4 (T042–T044)
       ↓
Phase 7: Polish & Audit (T045–T048)
```

### User Story Dependencies

- **User Story 1 (P1)**: Depends only on Foundational (Phase 2). No dependency on other stories.
- **User Story 2 (P1)**: Depends on Foundational (Phase 2) and `User` entity from US1. Can be tested independently.
- **User Story 3 (P2)**: Depends on Foundational (Phase 2) and `User` entity from US1.
- **User Story 4 (P2)**: Depends on US3 centralized password recovery service and seeded tenant data.

### Parallel Opportunities

- **Phase 1 (Setup)**: `T002`, `T003`, `T004`, `T005` can all be executed in parallel.
- **Phase 2 (Foundational)**: `T008`, `T009`, `T010`, `T011` can be executed in parallel once `pom.xml` is configured.
- **Within US1**: Failing tests `T014`, `T015` can be written in parallel; DTOs `T017` in parallel with validator `T016`.
- **Within US2**: Unit tests `T021`, `T022`, and MockMvc tests `T023` can be written in parallel.
- **Within US3**: Unit tests `T032` and MockMvc tests `T033` can be written in parallel.
- **Phase 7 (Polish)**: `T045` (Audit Logger) and `T046` (Proxy Config) can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (`T001`–`T006`)
2. Complete Phase 2: Foundational (`T007`–`T013`)
3. Complete Phase 3: User Story 1 (`T014`–`T020`)
4. **STOP and VALIDATE**: Run `mvn test -Dtest=RegistrationIntegrationTest`
5. Deploy/Demo initial comensal registration capability.

### Incremental Delivery

- **Increment 1 (MVP)**: Setup + Foundational + Registration (`T001`–`T020`).
- **Increment 2**: Unified Login + JWT Filter + Rate Limiting (`T021`–`T031`).
- **Increment 3**: Comensal Password Recovery with atomic consumption (`T032`–`T041`).
- **Increment 4**: Administrator Recovery + Seeded Tenants (`T042`–`T044`).
- **Increment 5**: Audit Logging, Proxy Security, OpenAPI Conformance (`T045`–`T048`).

---

## Notes

- All 48 tasks strictly follow `- [ ] [TaskID] [P?] [Story?] Description with exact file path`.
- Every User Story follows the TDD triad: write failing tests first → implement production code → verify with integration tests.
- Zero plaintext credentials or raw tokens permitted in logs or responses.
