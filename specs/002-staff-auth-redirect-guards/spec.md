# Feature Specification: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Feature Branch**: `002-staff-auth-redirect-guards`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "Define the product specification for: Staff Authentication Views and Automatic Role-Based Redirect Guards. This feature covers the login experience and post-authentication routing for the three staff profiles of the EasyEat platform: Counter Manager (Encargado de Mostrador), Kitchen Chef (Chef de Cocina), and Back-Office Administrator (Administrador). It maps to five user stories: ENC-1.1.1, ENC-1.2.1, CHF-1.1.1, CHF-1.2.1, and ADM-1.1.1."

## Clarifications

### Session 2026-10-17

- Q: When an authenticated staff member attempts to navigate to a workspace not matching their assigned role (cross-role access), how must the system respond? → A: The system must execute a silent redirect directly to the user's own assigned workspace without displaying an intermediate error or access-denied screen.
- Q: What is the expected behavior when a staff member logs in from a second browser, tab, or device simultaneously? → A: Single active session enforcement: a new successful login invalidates any previously active session for that account across all devices.
- Q: How does the system handle an account with no assigned role (data inconsistency or unassigned profile)? → A: The system defaults to the `COMENSAL` role, redirecting the user to the diner home/restaurant catalog.
- Q: When a staff member's session expires while actively operating in a workspace or submitting an action, how does the interface react? → A: The action must be aborted without committing changes, and the user must be redirected immediately to the login screen with an explanatory notification ("Tu sesión ha expirado").
- Q: When an unauthenticated user accesses a protected deep link URL and is redirected to the login form, where do they land after successfully authenticating? → A: If the requested destination URL matches the user's authenticated role, they are redirected to that requested target; if the requested URL does not match their role (or no target was preserved), they are redirected to the default root workspace of their role.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Unified Staff Login via Shared Interface (Priority: P1)

As a staff member (Counter Manager, Kitchen Chef, or Administrator),  
I want to log into the platform using my employee email and password through the shared login interface,  
So that I can securely establish my identity and start my work shift without needing role-specific login URLs.

**Why this priority**: Serves as the primary operational gateway for all restaurant personnel (`ENC-1.1.1`, `CHF-1.1.1`, `ADM-1.1.1`). Without unified credential authentication, staff cannot access operational tools.

**Independent Test**: Can be tested independently by submitting valid credentials for each staff role on the shared login form and verifying that identity authentication succeeds, the session context is established, and access is granted.

**Acceptance Scenarios**:

1. **Given** a staff user on the shared login page, **When** they enter their registered staff email and valid password and submit the form, **Then** the system authenticates the user, establishes an active session carrying their role and tenant context, and prepares the workspace redirect.
2. **Given** a staff user on the login page, **When** they submit an empty email, an empty password, or both, **Then** the system blocks submission and visually highlights the required fields.
3. **Given** a staff user on the login page, **When** they enter an email address with invalid syntax, **Then** the system prevents submission and displays an email format validation message.
4. **Given** a staff user on the login page, **When** they enter incorrect credentials (unregistered email or mismatched password), **Then** the system rejects authentication and displays a generic error message without disclosing whether the email exists.
5. **Given** a staff user whose account has been suspended or deactivated, **When** they attempt to authenticate, **Then** the system blocks login and displays an inactive account notification.
6. **Given** a staff user entering their email with leading/trailing whitespace or mixed casing, **When** they submit the correct password, **Then** the system normalizes the email (trimmed and lowercased) and completes authentication successfully.
7. **Given** a staff user submitting the login form, **When** the network connection is unavailable or the service fails, **Then** the system displays a connection error message and enables the user to retry.
8. **Given** a staff user on the login form, **When** they click the submit button multiple times in rapid succession, **Then** the system disables the submission control while the request is in flight to prevent duplicate submissions.

---

### User Story 2 - Automatic Post-Login Workspace Redirection (Priority: P1)

As an authenticated staff member,  
I want the system to automatically redirect me to my dedicated workspace immediately upon login,  
So that I land directly on my operational tools without manual navigation, menu hunting, or role selection.

**Why this priority**: Operational efficiency and speed are critical in food service environments (`ENC-1.2.1`, `CHF-1.2.1`, `ADM-1.1.1`). Staff must begin processing orders or managing operations without friction.

**Independent Test**: Can be tested independently by logging in as each distinct role and verifying that immediate redirection occurs to the exact destination workspace prescribed by the role-to-workspace routing policy.

**Acceptance Scenarios**:

1. **Given** a user authenticated with the Counter Manager role (`ENCARGADO`), **When** authentication completes, **Then** the system immediately redirects the user to the Counter / Dispatch view without intermediate prompts.
2. **Given** a user authenticated with the Kitchen Chef role (`CHEF`), **When** authentication completes, **Then** the system immediately redirects the user to the Kitchen Display System (KDS) view without intermediate prompts.
3. **Given** a user authenticated with the Administrator role (`ADMINISTRADOR`), **When** authentication completes, **Then** the system immediately redirects the user to the Back-Office Administration panel without intermediate prompts.
4. **Given** a user authenticated with the Diner role (`COMENSAL`), **When** authentication completes, **Then** the system redirects the user to the diner home / restaurant catalog view.
5. **Given** an unauthenticated user attempting to access a valid deep link matching their role (e.g., a specific KDS station URL for a Chef), **When** they log in successfully, **Then** the system redirects them directly to the preserved target URL instead of the default root view.
6. **Given** an unauthenticated user attempting to access a deep link not matching their role (e.g., a Chef attempting to access the Back-Office panel), **When** they log in successfully, **Then** the system ignores the requested target and redirects them to their own role's default workspace (KDS view).

---

### User Story 3 - Role-Based Route Protection and Cross-Role Isolation (Priority: P1)

As the system security guardian,  
I want all staff workspace URLs to be continuously protected by route guards,  
So that unauthorized individuals and users from differing roles cannot view or interact with restricted operational environments.

**Why this priority**: Core architectural invariant. Prevents unauthorized operational actions (e.g., chefs modifying administrative settings, counter managers altering kitchen orders, or diners viewing internal dispatch screens).

**Independent Test**: Can be tested by directly requesting forbidden workspace URLs with different authenticated roles and without authentication, verifying that access is deterministically denied and redirected.

**Acceptance Scenarios**:

1. **Given** an unauthenticated user, **When** they attempt to navigate directly to any staff workspace URL (Counter, KDS, or Back-Office), **Then** the system intercepts the navigation and redirects the user to the login screen, preserving the requested target for post-login evaluation.
2. **Given** a user authenticated as Kitchen Chef, **When** they navigate directly to the Counter view URL or the Back-Office panel URL, **Then** the system denies access and silently redirects the user to their own Kitchen (KDS) view.
3. **Given** a user authenticated as Counter Manager, **When** they navigate directly to the Kitchen KDS URL or the Back-Office panel URL, **Then** the system denies access and silently redirects the user to their own Counter / Dispatch view.
4. **Given** a user authenticated as Administrator, **When** they navigate directly to the Counter view or Kitchen KDS view, **Then** the system denies access and silently redirects the user to the Back-Office panel.
5. **Given** a user authenticated as Diner, **When** they attempt to navigate to any staff workspace URL, **Then** the system denies access and silently redirects the user to the diner home / restaurant catalog.
6. **Given** an authenticated user who was redirected away from a forbidden workspace, **When** they press the browser's Back button, **Then** the route guard intercepts the history event and prevents re-entering the forbidden view, remaining on their own valid workspace.

---

### User Story 4 - Session Expiration Handling and Action Protection (Priority: P2)

As a staff member operating in a protected workspace,  
I want the system to handle session expiration securely and predictably,  
So that stale sessions do not execute uncommitted operations and expired interfaces cannot be manipulated.

**Why this priority**: Guarantees data integrity and tenant security during long operational shifts when credentials or sessions reach their time limit.

**Independent Test**: Can be tested by letting a session expire or revoking the active session token, then executing a workspace navigation or action, and confirming that the action is blocked and the user is redirected to login.

**Acceptance Scenarios**:

1. **Given** a staff user operating in any protected workspace, **When** their session expires due to elapsed time or invalidation, **Then** subsequent route navigation is blocked and the user is immediately redirected to the login screen with a notification stating "Tu sesión ha expirado".
2. **Given** a staff user with an expired session submitting an in-workspace action (e.g., updating order status), **When** the submission is dispatched, **Then** the system aborts the operation without committing state changes, invalidates client session state, and redirects the user to the login screen with the expiration notice.
3. **Given** an active staff session on Device A, **When** the same staff member logs in successfully on Device B, **Then** the session on Device A is invalidated, and the next action or navigation on Device A redirects to the login screen.

---

### User Story 5 - Explicit Logout and History Invalidation (Priority: P2)

As an authenticated staff member finishing a shift,  
I want to log out explicitly from my workspace,  
So that my active session is completely terminated and subsequent users of the shared terminal cannot access my operational workspace.

**Why this priority**: Vital for shared restaurant workstations where multiple staff members alternate on the same physical terminal throughout the day.

**Independent Test**: Can be tested by triggering logout from a staff workspace, verifying that session data is purged, the user lands on the login page, and browser back navigation does not reveal cached protected screens.

**Acceptance Scenarios**:

1. **Given** an authenticated staff user in their workspace, **When** they select the "Cerrar sesión" action, **Then** the system invalidates the session, purges local session storage, and redirects the user to the login page.
2. **Given** a user who has just logged out, **When** they click the browser's Back button, **Then** the system detects the absence of an active session, prevents rendering cached workspace data, and keeps the user on the login screen.

---

### Edge Cases

- **Concurrent Multi-Device Login**: A staff user logs into the same account on a second device or browser tab while a session is active. The new login terminates previous active sessions (single active session model).
- **Session Expiration During Mutation**: A staff member dispatches an action at the exact moment their token expires. The system blocks the mutation atomically, rolls back uncommitted client changes, and redirects to login with an expiration notice.
- **Browser Back Button on Forbidden Redirection**: A user attempts cross-role navigation, gets redirected to their own view, and presses Back. The guard intercepts the navigation and maintains the user on their authorized workspace.
- **Browser Back Button After Logout**: A user presses Back after logout. The guard enforces unauthenticated access rules, redirects to login, and ensures no cached sensitive information is exposed.
- **Account Without Assigned Role**: A user account exists without an assigned staff role due to a data anomaly. The system falls back safely to treating the user as `COMENSAL`, routing them to the diner catalog.
- **Rapid Double-Submission**: The user clicks the login button multiple times quickly. The client locks form controls and debounces requests to prevent duplicate authentication attempts.
- **Obsolete Credential Autofill**: Browser autofills outdated credentials. The system evaluates them through the normal validation flow, fails gracefully with an anti-enumeration error, and preserves the email for correction.
- **Whitespace and Case Sensitivity in Email**: Email entered with mixed casing or whitespace. The system normalizes email to lowercase and strips whitespace before processing.
- **Deep Link Role Mismatch**: Unauthenticated user clicks a deep link for a workspace of a different role. Upon authentication, the system detects the mismatch and routes to the user's correct workspace instead of the requested deep link.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST provide a single, shared login interface for all user roles without role selectors, role-specific URLs, or differentiated login portals.
- **FR-002**: The login interface MUST collect an email address and password, validating that both fields are non-empty before initiating authentication.
- **FR-003**: The login interface MUST validate email syntax and display an informative validation message if the format is invalid.
- **FR-004**: The system MUST normalize the email address (strip leading/trailing whitespace and convert to lowercase) prior to submitting the authentication request.
- **FR-005**: The system MUST disable the login submit button and prevent duplicate submission requests while an authentication request is in flight.
- **FR-006**: Upon failed authentication due to mismatched or non-existent credentials, the system MUST display a generic security error message that prevents account enumeration.
- **FR-007**: Upon successful authentication, the system MUST immediately evaluate the role claim carried in the session context and execute an automatic redirect according to the following strict routing table:
  - Role `ENCARGADO` MUST redirect to the Counter / Dispatch view.
  - Role `CHEF` MUST redirect to the Kitchen Display System (KDS) view.
  - Role `ADMINISTRADOR` MUST redirect to the Back-Office Administration panel.
  - Role `COMENSAL` MUST redirect to the Diner home / restaurant catalog view.
- **FR-008**: The post-login redirection MUST occur immediately without presenting any intermediate selection screens, manual prompts, or confirmation modals.
- **FR-009**: If an unauthenticated user was intercepted while attempting to access a protected deep link URL, the post-login redirect MUST route to that deep link IF and ONLY IF the target belongs to the user's authenticated role; otherwise, it MUST route to the default workspace for that role.
- **FR-010**: All staff workspace URLs MUST be protected by continuous route guards that evaluate authentication state and role permissions prior to rendering any view.
- **FR-011**: If an unauthenticated user attempts direct navigation to any protected staff workspace, the route guard MUST intercept the request and redirect the user to the login screen, preserving the target URL for deep-link evaluation.
- **FR-012**: If an authenticated user attempts direct navigation to a staff workspace that does not match their assigned role (cross-role access), the route guard MUST silently redirect the user to their own assigned workspace without displaying an intermediate error screen.
- **FR-013**: The system MUST enforce a single active session policy per staff account: a new successful login MUST invalidate any existing active sessions for that account.
- **FR-014**: If a staff user's session expires or is invalidated during active workspace usage, the system MUST immediately block further workspace interaction and redirect the user to the login screen with an explanatory expiration notification.
- **FR-015**: Any workspace action or data mutation attempted with an expired or invalid session MUST be aborted without persisting changes or producing side effects.
- **FR-016**: Upon explicit logout by a staff user, the system MUST terminate the active session, purge client-side session credentials, redirect to the login screen, and configure cache-control protections to prevent viewing workspace content via browser back navigation.
- **FR-017**: If an authenticated account lacks an explicit staff role assignment, the system MUST apply a safe fallback treating the user as `COMENSAL` and redirecting to the diner catalog view.
- **FR-018**: If the authentication request fails due to network unavailability or server errors, the login interface MUST display a clear communication error notification and allow the user to retry.

### Key Entities

- **Staff Account**: Represents an employee authorized to operate in restaurant workspaces. Key attributes include unique user identifier, email, assigned role (`ENCARGADO`, `CHEF`, `ADMINISTRADOR`), associated tenant identifier (`tenant_id`), account status (`active`, `suspended`), and session revision indicator.
- **Session Authorization Context**: Represents the verified client credentials and authority. Key attributes include user identifier, assigned role, tenant scope, session validity period, and active session identifier.
- **Route Access Policy**: Represents the rule mapping each protected workspace route to its authorized role(s). Defines the destination for valid access, unauthorized cross-role redirects, and unauthenticated fallback.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of successful staff logins complete automated redirection to the role-assigned workspace in under 2 seconds from credential submission on standard network connections.
- **SC-002**: 100% of unauthenticated attempts to access staff workspace URLs are intercepted and redirected to the login interface.
- **SC-003**: 0% unauthorized cross-role access: 100% of cross-role navigation attempts are intercepted and silently redirected to the user's assigned workspace with zero leakage of unauthorized workspace content.
- **SC-004**: 100% of credential failures produce identical, anti-enumeration error feedback without revealing whether the email address exists in the system.
- **SC-005**: 100% of mutations or actions dispatched with an expired session are rejected with zero database updates or state side effects.
- **SC-006**: 100% of post-logout browser back navigations fail to display cached protected workspace views and redirect to the login page.
- **SC-007**: 100% of duplicate form submissions triggered by rapid double-clicking are prevented by client submission debouncing.

## Assumptions

- The centralized authentication service (developed under feature `001-central-auth-recovery`) generates valid session tokens containing `user_id`, `role`, `tenant_id`, and `token_version`.
- Staff accounts are provisioned and linked to their specific tenant by an Administrator through employee onboarding (`ADM-3.1.1` and `ADM-3.1.2`); staff do not select establishments during login.
- Diner registration and diner-specific catalog views exist and are maintained by separate feature deliverables (`COM-1.1.1`, `COM-1.2.1`).
- The internal layouts, controls, and specific features within the destination workspaces (Counter Dispatch, Kitchen KDS, Back-Office Panel) are independent deliverables; this specification governs landing on the correct workspace and continuous perimeter route protection.
- Client applications operate within standard modern web browsers with session storage and navigation history support.
