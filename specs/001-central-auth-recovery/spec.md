# Feature Specification: Central Authentication Service and Password Recovery

**Feature Branch**: `001-central-auth-recovery`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Feature: Central Authentication Service and Password Recovery (Servicio Central de Autenticación JWT y Recuperación de Claves) del proyecto EasyEat. Define the functional specification for the core authentication and credential recovery capabilities of the EasyEat multi-tenant platform, corresponding to Sprint 1's initial delivery activity: Endpoints de registro (COM-1.1.1) y login unificado con hash bcrypt (COM-1.2.1), emisión de tokens JWT con claims de rol/tenant y servicio transaccional de reseteo de claves por correo electrónico (COM-1.3.1, ADM-1.2.1)."

## Clarifications

### Session 2026-10-14

- Q: What level of security audit logging must the authentication and credential recovery service provide? → A: Structured console/logger audit events (event type, timestamp, IP, userId/email) for all security events (login success/failure, rate-limit lockouts, reset requests and confirmations) without logging sensitive passwords or raw tokens and without requiring database audit tables.

### Session 2026-10-23 (Backlog-US 2.0 Reconciliation)

- Q: What is the delivery scope of feature 001 regarding frontend presentation vs backend services? → A: Full End-to-End (E2E) delivery. The specification mandates both backend REST endpoints and frontend user interfaces (Comensal registration, password recovery request/confirm, and login navigation links) to satisfy the user-verifiable acceptance criteria of Backlog-US 2.0.
- Q: How is the comensal post-login redirect to "su establecimiento por defecto" (COM-1.2.1) handled in Sprint 1? → A: Comensales are routed to the general `/catalog` view. Dynamic restaurant selection and local preference persistence are formally deferred to Sprint 2 (`COM-1.5.1`).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comensal Registration with Email and Password (Priority: P1)

As a new comensal (customer/diner),
I want to register an account on the platform by providing my email address and a secure password,
So that I can establish my identity in the system and begin placing food orders.

**Why this priority**: Account creation is the primary customer onboarding entry point. Without registered comensal profiles, personalized ordering, order history, and dietary preferences cannot exist.

**Independent Test**: Can be fully tested by submitting valid registration details and verifying that a new account is persisted, a success notification is presented, and immediate login with those credentials succeeds.

**Acceptance Scenarios**:

1. **Given** a new visitor without an existing account on the registration page, **When** they input a valid, unregistered email address and a password meeting the security complexity criteria, and submit "Registrarse", **Then** the system creates the account, presents a success confirmation message, and redirects the user to the Login page.
2. **Given** a visitor on the registration page, **When** they enter an email address already registered on the platform and submit "Registrarse", **Then** the system rejects account creation and displays an error message indicating that the email address is already associated with an existing account.
3. **Given** a visitor on the registration page, **When** they submit a syntactically invalid email format, **Then** the system blocks submission and displays an error notification specifying that the email format is invalid.
4. **Given** a visitor on the registration page, **When** they enter a password failing the security complexity policy (fewer than 8 characters, or missing at least one uppercase letter, one lowercase letter, or one numeric digit), **Then** the system prevents account creation and visually highlights the specific password rules that were not met.
5. **Given** a visitor on the registration page, **When** they leave the email field, password field, or both empty and press "Registrarse", **Then** the system prevents form submission and marks the empty fields as mandatory with validation messages.
6. **Given** a visitor on the registration page who decides not to complete registration, **When** they click "Ya tengo una cuenta" or "Iniciar sesión", **Then** the system cancels the registration flow without storing data and redirects the visitor to the Login page.

---

### User Story 2 - Unified Login with Email and Password (Priority: P1)

As a registered user (Comensal, Encargado, Chef, or Administrador),
I want to log in using my email and password through a single unified authentication mechanism,
So that I can securely access the platform with an authenticated session asserting my identity, assigned role, and tenant context.

**Why this priority**: Unified authentication is the security backbone for all platform actors. It enforces multi-tenant boundaries and eliminates duplicate login logic across roles (`COM-1.2.1`, `ENC-1.1.1`, `CHF-1.1.1`, `ADM-1.1.1`).

**Independent Test**: Can be tested independently by submitting valid credentials for each role and verifying that an authenticated session token is issued containing the correct `user_id`, `role`, and `tenant_id` claims, allowing access to the appropriate role-specific views.

**Acceptance Scenarios**:

1. **Given** an active user account with correct email and password, **When** the user submits their credentials on the login page, **Then** the system authenticates the user, issues a secure session token valid for 8 hours containing user identity, role, and tenant context (where applicable), and redirects the user to their designated view (Comensal home/catalog at `/catalog` pending dynamic establishment selection in Sprint 2 via `COM-1.5.1`, Mostrador dispatch view, Kitchen KDS, or Admin Back-Office).
2. **Given** a user on the login page, **When** they submit a registered email with an incorrect password, **Then** the system rejects access, preserves the entered email in the input field, and displays a generic security error message.
3. **Given** a user on the login page, **When** they submit an email address that does not exist in the database with any password, **Then** the system rejects access and displays the exact same generic security error message as an incorrect password, preventing user enumeration.
4. **Given** a user on the login page, **When** they submit the form with empty email, empty password, or both, **Then** the system blocks the request and highlights the required fields.
5. **Given** a user attempting to log in, **When** their account status is marked as suspended, disabled, or inactive by an administrator, **Then** the system blocks authentication and displays an explicit notification that the profile is not authorized to operate.
6. **Given** a user entering their email with leading/trailing whitespaces or uppercase characters, **When** they submit the correct password, **Then** the system normalizes the email (trimmed and lowercased) and successfully authenticates the user.
7. **Given** any account or client IP, **When** 5 consecutive failed login attempts occur within a rolling window, **Then** the system temporarily throttles/locks access for 15 minutes, returning a generic retry-after message.
8. **Given** a user on the login page who forgot their password, **When** they click "Olvidé mi contraseña" or the password recovery link, **Then** the system interrupts the login flow and navigates the user to the password recovery request screen.

---

### User Story 3 - Password Recovery via Email for Comensales (Priority: P2)

As a comensal who forgot their password,
I want to request a password reset link sent to my registered email address,
So that I can securely regain access to my account without administrative intervention.

**Why this priority**: Self-service credential recovery is essential for user retention and operational scalability, preventing abandoned accounts when passwords are forgotten.

**Independent Test**: Can be tested by requesting a reset for a known email, verifying the delivery of a time-limited token, following the link to submit a new password, and confirming that old credentials fail while new credentials succeed.

**Acceptance Scenarios**:

1. **Given** a registered comensal on the password recovery page, **When** they enter their registered email and submit the request, **Then** the system generates a single-use cryptographically secure reset token with a 15-minute expiration, sends a recovery email with the link, and displays a confirmation message.
2. **Given** a user on the recovery page, **When** they enter an email address that is not registered, **Then** the system displays the exact same confirmation message without sending an email, preserving anti-enumeration guarantees.
3. **Given** a user on the recovery page, **When** they submit an invalid email syntax or leave the field blank, **Then** the system prevents submission and highlights the validation error.
4. **Given** a user who recently requested a recovery email, **When** they immediately attempt another request for the same email before the cooldown period has elapsed, **Then** the system throttles the action to prevent spamming and abuse.
5. **Given** a comensal navigating to a reset link, **When** the 15-minute validity window has expired, **Then** the system rejects token verification and notifies the user that the link has expired, prompting them to request a new one.
6. **Given** a comensal navigating to a valid reset link, **When** they enter and confirm a new password that complies with the complexity policy, **Then** the system updates the stored cryptographic hash, invalidates the token, revokes all previously issued active sessions for that account, and redirects to login.
7. **Given** a reset link that has already been successfully used once, **When** a user clicks or submits it again, **Then** the system rejects the token as already consumed.
8. **Given** a comensal who has initiated the password recovery flow on the recovery request view, **When** they decide not to proceed and click "Cancelar" or "Volver al inicio de sesión", **Then** the system aborts the recovery process without dispatching emails and navigates the user back to the login screen.

---

### User Story 4 - Password Recovery via Email for Administrators (Priority: P2)

As an establishment administrator (Owner / General Manager),
I want to reset my account password via email using the centralized recovery mechanism,
So that I never permanently lose access to managing my restaurant business and tenant data.

**Why this priority**: Administrative lockouts halt business configuration, menu approval, and staff management. Reusing the centralized recovery mechanism guarantees consistent security without duplicated infrastructure.

**Independent Test**: Can be tested by initiating the reset flow with an administrator account's email, completing the password update, and validating that administrative access is restored with the new password.

**Acceptance Scenarios**:

1. **Given** an administrator on the back-office login screen who selects "Olvidé mi contraseña", **When** they submit their registered administrator email, **Then** the system routes the request through the centralized recovery service, dispatching a secure 15-minute single-use reset link.
2. **Given** an administrator who accesses the valid reset link and submits a new password compliant with security policies, **When** the reset completes, **Then** the administrator's password is updated, all previous admin sessions are invalidated immediately, and login with the new credentials succeeds.

---

### Edge Cases

- **Account Status**: What happens if an account is suspended or disabled while an active session exists or during a password reset request? The system MUST reject authentication and block password reset consumption for suspended accounts.
- **Timing Attacks & Enumeration**: How does the system ensure non-existent emails take similar execution time as existing emails during login and recovery? Password hashing comparison and lookup MUST execute timing-resistant workflows.
- **Token Tampering & Malformed Input**: What happens when an attacker modifies the payload or signature of a session JWT or reset token? The system MUST immediately reject the token as invalid and terminate the operation.
- **Concurrent Session Revocation**: What happens to existing open sessions when a user resets their password on another device? All prior session tokens MUST be deterministically invalidated through a token version / password modification timestamp check.
- **Case-Insensitive Email Matching**: How does the system handle mixed-case email entries (e.g., `User@Example.COM` vs `user@example.com`)? All emails MUST be normalized to lowercase and trimmed before storage, lookup, or validation.
- **Network or Delivery Failures**: What happens if the transactional email delivery fails? The recovery token record MUST remain bounded by its 15-minute lifespan and allow a subsequent retry once the cooldown window passes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow new comensales to register using a valid, unique email address and a password.
- **FR-002**: The system MUST normalize all email addresses (trim leading/trailing whitespace and convert to lowercase) prior to validation, uniqueness checks, persistence, and authentication lookup.
- **FR-003**: The system MUST enforce a password complexity policy requiring a minimum of 8 characters, at least one uppercase letter, at least one lowercase letter, and at least one numeric digit.
- **FR-004**: The system MUST hash all passwords with a one-way cryptographic hash function and individual random salt (e.g., bcrypt) prior to storage. Plaintext passwords MUST NEVER be stored, logged, or returned in responses.
- **FR-005**: The system MUST provide a single unified authentication endpoint supporting all user roles (`COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`).
- **FR-006**: Upon successful authentication, the system MUST issue a signed session token (JWT) with a standard validity period of 8 hours, containing the user identity (`user_id`), assigned role (`role`), and associated establishment identifier (`tenant_id`, mandatory for staff roles, optional for unassociated comensales). For comensales in Sprint 1, post-login navigation routes to the general catalog (`/catalog`); dynamic establishment selection is deferred to Sprint 2 (`COM-1.5.1`).
- **FR-007**: The system MUST provide identical, generic error messages and timing-resistant behavior for invalid passwords and non-existent email addresses to prevent user enumeration.
- **FR-008**: The system MUST enforce rate limiting on the login service, applying a 15-minute temporary cooldown/block after 5 consecutive failed login attempts for a given account or client IP address.
- **FR-009**: The system MUST reject login attempts and token generation for accounts marked as suspended, inactive, or disabled.
- **FR-010**: The system MUST allow users to request a password reset by providing their registered email address.
- **FR-011**: The system MUST generate a cryptographically random, single-use password reset token with a strict 15-minute expiration window upon receiving a valid recovery request.
- **FR-012**: The system MUST send a transactional password recovery email containing the secure reset link only if the email belongs to an active, valid account, while presenting a uniform confirmation message to the client regardless of email existence.
- **FR-013**: The system MUST enforce a rate-limiting cooldown period between consecutive password reset requests for the same email address or IP to prevent email flooding.
- **FR-014**: The system MUST validate that a password reset token is authentic, unconsumed, and unexpired before presenting the password update interface or accepting a new password.
- **FR-015**: Upon successful password reset, the system MUST mark the reset token as consumed, update the stored password hash, and immediately invalidate all previously issued active session tokens for that account.
- **FR-016**: The password recovery mechanism for administrators MUST reuse the same centralized recovery service used by comensales, guaranteeing identical security invariants.
- **FR-017**: The system MUST emit structured security audit log events for all authentication and credential recovery actions (successful login, failed login, account rate-limit lockout, password reset requested, password reset completed) capturing event type, timestamp, client IP, and user identity, without logging plaintext passwords or raw tokens.
- **FR-018**: The system MUST provide a Comensal Registration view allowing visitors to submit their email and password, displaying clear real-time or submit-time validation for missing fields, invalid email format, and visual indicators highlighting unsatisfied password complexity rules.
- **FR-019**: Upon successful registration, the registration view MUST display a confirmation notice and automatically redirect the user to the Login view (`/login`).
- **FR-020**: The registration view MUST include a navigation action ("Ya tengo una cuenta" / "Iniciar sesión") that aborts registration without preserving entered data and navigates directly to the Login view.
- **FR-021**: The Login view MUST provide an accessible link ("Olvidé mi contraseña") that navigates the user directly to the password recovery request view.
- **FR-022**: The system MUST provide a Password Recovery Request view allowing users to enter their email, submit a reset request, and view a uniform confirmation message regardless of account existence.
- **FR-023**: The password recovery request view MUST provide a cancel/return action that aborts the flow and navigates back to the Login view without dispatching an email.
- **FR-024**: The system MUST provide a Password Reset Confirmation view accessible via the tokenized link, allowing users to input and confirm a new password complying with security policies, and redirecting to Login upon successful reset.

### Key Entities

- **User**: Represents any individual operating the system. Key attributes include unique identifier (`user_id`), normalized email, salted password hash, account status (`active`, `suspended`, `unverified`), assigned role (`COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`), optional tenant association (`tenant_id`), creation timestamp, and credential version / password changed timestamp.
- **Session Context**: Represents an authenticated operational state. Key attributes include subject user identifier, role claim, tenant scope claim, token issue time, and expiration time (8 hours).
- **Password Reset Token**: Represents a temporary, single-use credential blanqueo authorization. Key attributes include hashed token value, associated user identifier, creation timestamp, expiration timestamp (15 minutes), and consumption status (`pending`, `consumed`, `revoked`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can complete account registration in under 60 seconds on standard network conditions.
- **SC-002**: Users can complete the login process and reach their designated role view in under 3 seconds from credential submission.
- **SC-003**: 100% of persisted passwords are cryptographically hashed; zero instances of plaintext passwords or exposed credentials in logs or responses.
- **SC-004**: 100% of issued session tokens deterministically assert correct role and tenant claims, preventing unauthorized cross-tenant data leakage.
- **SC-005**: Password recovery emails are dispatched within 5 seconds of request submission, and reset links expire strictly at 15 minutes.
- **SC-006**: Account enumeration attempts via login and recovery forms produce zero differential indicators (messages or timing variance) between registered and unregistered emails.
- **SC-007**: 100% of previously active sessions are invalidated immediately following a successful password reset.
- **SC-008**: 100% of authentication and password recovery events produce a structured audit log entry with zero leakage of credentials or unhashed tokens.

## Assumptions

- Users have access to a standard web browser and an accessible email account to receive transactional emails.
- A reliable transactional email delivery service is configured for the platform.
- Comensales are platform-level users who can select or browse establishments, while staff roles (`ENCARGADO`, `CHEF`, `ADMINISTRADOR`) are assigned to a specific tenant upon creation.
- Social login (Google SSO), SMS-based recovery, and establishment QR onboarding are explicitly excluded from this MVP scope, as decided in the planning phase.
- Frontend presentation layer views for comensal registration, unified login navigation links, and self-service password recovery are fully in scope as end-to-end user-facing deliverables. Dynamic establishment selection for diners is formally integrated in Sprint 2 via COM-1.5.1.
