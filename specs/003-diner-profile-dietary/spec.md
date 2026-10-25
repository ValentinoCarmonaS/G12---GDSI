# Feature Specification: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature Branch**: `003-diner-profile-dietary`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "Define the product specification for a new iteration: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering. This iteration implements diner profile editing, dietary preference and allergen management, and their automatic application to establishment menu catalogs. It encompasses user stories COM-1.4.1 (Edit Personal Data), COM-1.4.3 (Configure Dietary Restrictions & Allergens), and COM-4.2.1 (Automated Profile Filters Applied to Catalog)."

## Clarifications

### Session 2026-10-24

- Q: How must the checkout screen behave if the cart contains non-compliant dishes whose warning was already accepted upon adding them to the cart (COM-1.4.3 CA3 vs COM-4.2.1 CA2)? → A: Single-point risk confirmation: the preventive warning modal when clicking "Agregar al carrito" is the sole required gatekeeper. In the cart drawer and checkout summary, non-compliant dishes prominently retain their visual warning badges for clear visibility, but no redundant blocking modal or extra consent checkbox is required at checkout.
- Q: What cooldown and rate-limiting policy applies to resending email verification links (FR-007)? → A: A strict 60-second cooldown window per user account. The profile UI displays a 60-second visual countdown timer disabling the "Resend verification link" action, and the backend enforces this cooldown, rejecting premature requests with HTTP 429 Too Many Requests (RFC 9457 ProblemDetail).

### Session 2026-10-17

- Q: How must the system behave when a diner attempts to add a dish conflicting with their profile dietary restrictions or allergens to the cart (COM-1.4.3 CA3)? → A: The system displays an immediate preventive warning modal upon clicking "Agregar al carrito" detailing conflicting allergens and restrictions, requiring explicit risk assumption confirmation to proceed. If confirmed, the dish enters the cart permanently displaying an inline allergen warning badge. In checkout, conflicting items retain their visible warning indicators and recorded risk assumption.
- Q: How must pending email change requests and concurrent modifications be managed within the diner profile? → A: Display the pending email with a "Pending verification" status badge alongside explicit "Resend verification link" and "Cancel request" actions, with a 24-hour token expiration. If the diner inputs another email while a request is pending, the previous token is automatically revoked and a new verification workflow is initiated. The existing email remains active for login until verification completes.
- Q: How must the catalog master toggle appear for an authenticated diner with zero configured restrictions in their profile? → A: The master toggle is completely hidden from the catalog interface until the diner configures at least one restriction or allergen in their profile.
- Q: When a diner updates their dietary profile while having an active order already confirmed and in preparation in the kitchen, how is that order handled? → A: Submitted orders in preparation are treated as immutable contractual snapshots without altering active kitchen KDS tickets; the diner profile UI notifies the user that profile updates apply immediately to the active cart and future orders, advising them to contact restaurant staff directly for in-flight orders.
- Q: Is the phone number field mandatory or optional when editing diner personal details (COM-1.4.1)? → A: The phone number is an optional field; if provided, it is strictly validated against standard telephone formatting rules (8 to 15 digits, allowing leading '+' and standard delimiters).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Diner Personal Data Management (Priority: P1)

As a registered diner (comensal),  
I want to view and update my personal contact details (first name, last name, phone number, and email address) in my profile,  
So that my identity and communication preferences remain accurate across the platform without corrupting past transactions.

**Why this priority**: Accurate contact details are fundamental for account identification, order dispatch coordination, and account security (`COM-1.4.1`). Providing self-service editing prevents account abandonment and data drift.

**Independent Test**: Can be tested independently by navigating to the profile screen, modifying name, phone, and initiating email change, verifying input validations, persistence on valid input, cancellation behavior, and verifying that historical order records remain immutable.

**Acceptance Scenarios**:

1. **Given** an authenticated diner on the profile editing form, **When** they modify their first name or last name using valid alphabetic characters and spaces and submit "Guardar cambios", **Then** the system persists the updated information and displays a success confirmation message.
2. **Given** a diner on the profile editing form, **When** they enter digits, symbols, or special characters into the first name or last name fields, **Then** the system blocks submission and displays a validation error stating that only letters and spaces are allowed.
3. **Given** a diner on the profile editing form, **When** they leave the first name or last name field empty and submit, **Then** the system prevents submission and highlights the empty fields as mandatory.
4. **Given** a diner on the profile editing form, **When** they enter a phone number with invalid syntax (fewer than 8 digits, more than 15 digits, or containing invalid characters), **Then** the system blocks submission and displays an invalid phone format message.
5. **Given** a diner on the profile editing form, **When** they leave the phone number field empty and submit valid name data, **Then** the system accepts and saves the changes, treating the phone number as an optional attribute.
6. **Given** a diner requesting to change their email address to a new valid, unregistered address, **When** they submit the change, **Then** the system dispatches an asynchronous verification link containing a 24-hour cryptographic token to the new address, maintains the current email address as the active login and notification identifier, and displays the new email with a "Pending verification" badge alongside "Resend link" and "Cancel request" options in the profile.
7. **Given** a diner with an email change request pending, **When** they click "Cancel request", **Then** the system invalidates the verification token, removes the pending status badge, and preserves the active email address.
8. **Given** a diner with an email change request pending, **When** they enter a different new email address and submit, **Then** the system revokes the previous token, dispatches a fresh verification link to the new target address, and updates the pending status in the profile.
9. **Given** a diner accessing the verification link sent to their new email within the 24-hour window, **When** the link is opened, **Then** the system validates the token, promotes the new email to primary login identifier, invalidates the token, revokes active sessions requiring identity re-authentication, and displays a success confirmation.
10. **Given** a verification link that has expired (> 24 hours) or has already been consumed, **When** a user accesses the link, **Then** the system rejects the token and displays an error notification prompting the user to initiate a new request from their profile.
11. **Given** a diner on the profile form, **When** they enter an email address that is already associated with another active user account across the multi-tenant platform, **Then** the system rejects the modification and displays an email unavailable error.
12. **Given** a diner modifying profile fields, **When** they click "Cancelar" or navigate away before saving, **Then** the system discards all uncommitted modifications and retains original persisted values.
13. **Given** past completed orders and transaction receipts, **When** the diner updates their name, surname, or phone number in their profile, **Then** the system ensures all historical orders permanently preserve the original contact details captured at the time the order was placed.

---

### User Story 2 - Dietary Restrictions and Allergens Configuration (Priority: P1)

As a diner with specific dietary requirements or food allergies,  
I want to configure my dietary restrictions and allergens in my profile using a standardized platform taxonomy,  
So that the platform tailors my dining experience, prevents accidental ingestion of harmful ingredients, and guarantees food safety transparency.

**Why this priority**: Food safety is critical to diner trust and well-being (`COM-1.4.3`). Accurate configuration of dietary preferences and allergies provides the baseline data contract for automated catalog filtering and cart safety badges.

**Independent Test**: Can be tested independently by selecting and saving single or multiple dietary lifestyles and allergens, confirming immediate persistence, verifying explicit error alerts on persistence failure (no silent fails or optimistic state), and verifying live cart re-evaluation with inline warning badges.

**Acceptance Scenarios**:

1. **Given** an authenticated diner in the dietary preferences section of their profile, **When** the taxonomy is displayed, **Then** the system renders options organized into two distinct groups: Dietary Lifestyles (Celiac / Gluten-Free / Sin TACC, Vegan, Vegetarian) and Common Allergens (Dairy / Lactose, Peanuts, Tree Nuts, Eggs, Fish, Shellfish, Soy, Wheat).
2. **Given** a diner in the dietary preferences section, **When** they select zero, one, or multiple restrictions across either or both groups and click "Guardar preferencias", **Then** the system immediately persists the updated preferences to the server.
3. **Given** a diner attempting to save dietary preferences, **When** a network disconnection or server persistence error occurs, **Then** the system rejects the operation, applies zero optimistic local-only state, and displays an explicit error alert to prevent the diner from falsely assuming food safety protections are active.
4. **Given** a diner with an active cart containing selected dishes, **When** they update their dietary restrictions in their profile by adding a new restriction or allergen, **Then** the system immediately re-evaluates all items in the active cart, attaching prominent inline warning badges to non-compliant dishes without forcibly evicting them.
5. **Given** a diner who deselects an existing restriction in their profile and saves, **Then** the system updates preferences in real time, immediately removing exclusion filters and allergen warning badges associated with that restriction across both the catalog and the cart.
6. **Given** a diner with active orders already confirmed and currently in preparation in an establishment's kitchen, **When** they update their dietary restrictions in their profile, **Then** the system preserves in-flight kitchen orders as immutable snapshots without mutating active KDS tickets, and displays an advisory notification in the profile instructing the diner to contact the restaurant directly for in-flight orders.

---

### User Story 3 - Automated Catalog Filtering & Food Safety Browsing (Priority: P1)

As an authenticated diner with configured dietary restrictions,  
I want establishment catalogs to automatically filter out non-compliant dishes by default while offering a master toggle to inspect the full menu with clear visual warnings,  
So that I can effortlessly discover safe food options while maintaining full autonomy to review the complete menu.

**Why this priority**: Delivers the primary end-user value of dietary profiling (`COM-4.2.1`). Automated filtering eliminates manual ingredient cross-referencing, accelerates ordering, and prevents accidental allergen exposure.

**Independent Test**: Can be tested independently by logging in with a diner account that has active restrictions, opening an establishment catalog, verifying that non-compliant dishes are hidden by default, toggling the master switch OFF to verify that dishes appear with allergen warning badges, verifying that clicking "Agregar al carrito" on a non-compliant dish triggers the preventive risk-assumption modal, verifying cancellation blocks cart insertion, and verifying session reset behavior.

**Acceptance Scenarios**:

1. **Given** an authenticated diner with active restrictions in their profile accessing an establishment catalog, **When** the menu view loads, **Then** the system automatically injects all active profile restrictions as default exclusion filters, hiding non-compliant dishes from the catalog view.
2. **Given** a diner with multiple active restrictions (e.g., Vegan AND Peanut Allergy), **When** viewing a catalog, **Then** the system applies a strict conjunction rule (AND logic / intersection), displaying only dishes that simultaneously satisfy all active restrictions.
3. **Given** an authenticated diner with at least one active restriction, **When** viewing the catalog, **Then** the interface displays a master filter toggle ("Filter by my profile restrictions") set to ON by default.
4. **Given** a diner viewing the catalog with the master toggle ON, **When** they toggle the control to OFF, **Then** the system re-renders the catalog showing all establishment dishes, displaying a prominent inline visual warning badge on every dish that violates any of the diner's configured restrictions, detailing the specific conflicting restriction or allergen.
5. **Given** an authenticated diner with active restrictions and the catalog master toggle set to OFF, **When** they click "Agregar al carrito" on a dish displaying dietary warning badges, **Then** the system intercepts the action and displays a preventive warning modal detailing conflicting allergens and dietary restrictions, asking whether they assume the risk to proceed. If the diner cancels, the item is not added; if the diner confirms, the dish is added to the cart permanently flagged with an inline allergen warning badge.
6. **Given** a cart containing one or more items with dietary warning badges, **When** the diner reviews the cart or advances through checkout, **Then** the system permanently displays the allergen warning indicators and visibly retains the recorded risk assumption throughout the order summary prior to order emission.
7. **Given** a diner who switched the master toggle to OFF in a catalog, **When** they navigate to a different establishment, reload the browser tab, or initiate a new session, **Then** the system resets the master toggle back to ON, re-applying profile dietary protections.
8. **Given** an establishment with zero dishes meeting the diner's active restriction conjunction, **When** the catalog loads, **Then** the system presents a dedicated empty state screen with the message "No dishes match your dietary restrictions at this restaurant" and an immediate action button "View full menu without profile filters" that switches the master toggle to OFF.
9. **Given** an authenticated diner with zero configured restrictions in their profile, **When** they access a catalog, **Then** the system displays all available dishes and keeps the master filter toggle completely hidden from the interface.
10. **Given** an unauthenticated guest browsing a catalog, **When** the catalog loads, **Then** the system displays the full unfiltered menu with prominent calls to action inviting the visitor to sign in or register to enable automated health filters.
11. **Given** a diner who switched the master toggle OFF, added a conflicting dish to their cart, and switched the master toggle back to ON, **When** reviewing the catalog and cart, **Then** the conflicting dish is hidden again from the catalog menu but remains intact in the cart accompanied by its permanent warning badge.

---

### Edge Cases

- **Diners with zero configured restrictions**: The catalog opens in standard unfiltered mode; the master toggle is hidden entirely to avoid visual clutter.
- **Diners with 4+ overlapping restrictions**: The conjunction (AND logic) can severely restrict visible items; the system evaluates the intersection deterministically without performance degradation and presents the dedicated empty state if visible items count reaches zero.
- **Establishment with zero compliant dishes**: Dedicated empty state rendered with an immediate action button to turn the master filter OFF and inspect full menu with warning badges.
- **Profile restriction update with active cart items**: Immediate server-side re-evaluation of cart items attaching visual badges to incompatible items without evicting them.
- **Toggle OFF, incompatible dish added, toggle ON**: The incompatible dish remains in the cart with its persistent badge while the catalog re-applies exclusion filtering.
- **Rapid toggling of master filter during data loading**: Client-side debounce and idempotent state handling ensuring the final rendered view strictly reflects the user's latest toggle state without UI flickering or stale data races.
- **Session termination, tab closure, and browser refresh**: Master toggle state is strictly ephemeral and scoped to the active page session; refreshing or opening a new restaurant resets the filter to ON.
- **Case-insensitivity and whitespace in email change requests**: Email addresses are trimmed of leading/trailing spaces and lowercased prior to uniqueness validation and token dispatch.
- **Consecutive email change submissions**: A new submission invalidates any previously pending verification token immediately, ensuring only a single active token exists per account.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow authenticated diners to view and edit their First Name, Last Name, Phone Number, and Email Address.
- **FR-002**: The system MUST validate that First Name and Last Name fields contain only valid alphabetic characters and spaces, rejecting numeric digits, symbols, and whitespace-only submissions.
- **FR-003**: The system MUST treat the Phone Number field as optional, but when provided, MUST validate that it conforms to standard telephone formatting rules (8 to 15 digits, allowing optional leading '+' and standard formatting separators).
- **FR-004**: The system MUST enforce global email address uniqueness across the entire multi-tenant platform in a case-insensitive manner.
- **FR-005**: When a diner submits an email address change, the system MUST initiate an asynchronous verification workflow, dispatching a secure verification link with a single-use cryptographic token valid for 24 hours to the new address.
- **FR-006**: While an email change verification remains pending, the system MUST retain the existing email address as the active identifier for login, communications, and account notifications.
- **FR-007**: The system MUST display a "Pending verification" status badge next to the requested new email address in the diner profile, providing explicit actions to "Resend verification link" and "Cancel request", with an enforced 60-second cooldown timer between resend dispatches (disabling the action in UI and rejecting premature requests with HTTP 429).
- **FR-008**: If a diner submits another new email address while a verification request is pending, the system MUST revoke the previous token immediately and issue a new token and verification link for the updated address.
- **FR-009**: Upon successful access of the verification link within its 24-hour validity window, the system MUST promote the new email to primary login address, invalidate the token, and revoke active sessions requiring re-authentication.
- **FR-010**: The system MUST discard uncommitted personal data modifications when the diner cancels or navigates away from the profile editing screen.
- **FR-011**: Historical order records and past financial transaction receipts MUST permanently retain the original contact details captured at order placement time and MUST NOT be mutated by profile updates. *(Architectural invariant: guaranteed by entity decoupling; the future `orders` table in Sprint 2/3 will capture an immutable denormalized customer snapshot rather than a mutable join).*
- **FR-012**: The system MUST provide a standardized, closed taxonomy of dietary restrictions organized into two categories: Dietary Lifestyles (Celiac / Gluten-Free / Sin TACC, Vegan, Vegetarian) and Common Allergens (Dairy / Lactose, Peanuts, Tree Nuts, Eggs, Fish, Shellfish, Soy, Wheat).
- **FR-013**: The system MUST permit diners to select zero, one, or multiple dietary restrictions and allergens simultaneously in their profile.
- **FR-014**: Dietary preference persistence MUST be validated on the server; upon any network or database failure, the system MUST display an explicit error alert and MUST NOT apply optimistic local-only state.
- **FR-015**: When dietary restrictions are updated in a profile while an active cart session exists, the system MUST immediately re-evaluate all items in the cart, attaching inline visual warning badges to non-compliant dishes without forcibly evicting them.
- **FR-016**: Submitted orders in preparation in kitchen KDS MUST remain immutable contractual snapshots; the profile interface MUST inform the diner that profile changes apply to future orders and active cart, advising direct establishment contact for in-flight orders.
- **FR-017**: When an authenticated diner accesses an establishment catalog, the system MUST automatically inject all active dietary restrictions from their profile as default exclusion filters.
- **FR-018**: Catalog dietary filtering MUST apply strict conjunction (AND logic / intersection), displaying only dishes that simultaneously satisfy all active profile restrictions: Visible Dishes = Total Available Dishes ∩ {Dishes compliant with Restriction_1} ∩ ... ∩ {Dishes compliant with Restriction_N}.
- **FR-019**: The catalog interface MUST provide a master toggle ("Filter by my profile restrictions") set to ON by default for any diner with at least one active restriction.
- **FR-020**: When the master toggle is switched to OFF, the system MUST render all available establishment dishes and MUST prominently display an inline visual warning badge on every dish violating any of the diner's active restrictions, explicitly naming the conflicting restriction or allergen.
- **FR-021**: When a diner attempts to add a non-compliant dish to the cart (with master toggle OFF), the system MUST display an immediate preventive warning modal detailing conflicting allergens and dietary restrictions, requiring explicit risk assumption confirmation to add the dish to the cart. If confirmed, the item MUST enter the cart permanently displaying an inline allergen warning badge.
- **FR-022**: If dishes with dietary warnings exist in the cart, the system MUST maintain permanent inline allergen warning indicators in the cart drawer and checkout summary, ensuring the previously confirmed risk assumption remains visibly displayed without requiring a redundant blocking modal or extra consent step at checkout.
- **FR-023**: The OFF state of the master catalog toggle MUST be strictly session-scoped; navigating to a different restaurant, reloading the page, or initiating a new session MUST reset the toggle to ON.
- **FR-024**: If an establishment has zero dishes meeting the diner's active restriction conjunction, the catalog MUST display a dedicated empty state message with an immediate action button to switch the master toggle OFF and view the full menu.
- **FR-025**: For authenticated diners with zero configured restrictions in their profile, the system MUST display all available dishes and MUST completely hide the master filter toggle.
- **FR-026**: For unauthenticated guests, the system MUST display the complete unfiltered catalog by default, accompanied by visible prompts to sign in or register to enable personalized dietary filters.
- **FR-027**: All validation rules (name formats, phone syntax, email uniqueness, restriction conjunction queries, and cart safety checks) MUST be enforced by the backend independently of client-side validation.

---

### Key Entities *(data model concepts)*

- **DinerProfile**: Represents personal contact data and dietary preferences for a user with the diner role. Attributes include First Name, Last Name, optional Phone Number, active Email Address, and a collection of active Dietary Restrictions.
- **EmailChangeRequest**: Represents a transient pending email update. Attributes include User ID, Proposed Email, Cryptographic Verification Token, Created Timestamp, Expiration Timestamp (24 hours), and Status (Pending, Verified, Revoked, Expired).
- **DietaryRestriction**: A standardized platform taxonomy entry. Attributes include Unique Code, Name, Group (Dietary Lifestyle or Common Allergen), and Exclusion Criteria.
- **CatalogDish**: Represents an establishment menu item. Attributes include Dish ID, Tenant ID, Name, Description, Price, Availability Status, and a collection of associated Allergens and Dietary Compliance Tags.
- **CatalogFilterSessionState**: Represents the ephemeral client session state for catalog browsing in an establishment. Attributes include Master Toggle State (ON/OFF), defaulting to ON when the diner profile has active restrictions.
- **EvaluatedCartItem**: Represents a dish added to the diner's cart, enriched with a real-time compliance evaluation status (Compliant vs Non-Compliant) and an array of specific conflicting restriction/allergen tags.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of non-compliant dishes are excluded from the catalog view when the profile master filter is set to ON.
- **SC-002**: 100% of non-compliant dishes rendered in the catalog (with master toggle OFF), in the cart, or in checkout review prominently display an inline visual warning badge identifying the exact conflicting allergen or diet.
- **SC-003**: 100% of network or server persistence failures during profile or dietary preference updates produce an explicit error notification and result in 0% unverified local optimistic state.
- **SC-004**: 100% of historical order records and past transactions retain their original contact data without modification following diner profile edits. *(Verification deferred to the Checkout/Orders sprint when the `orders` persistence model is implemented).*
- **SC-005**: 100% of attempts to add non-compliant dishes to the cart require and record explicit risk assumption consent via preventive modal prior to cart insertion, retaining permanent visible warnings through checkout.
- **SC-006**: Fewer than 2% of diners encountering an empty catalog due to restrictive filter conjunctions abandon their session, measured by usage of the "View full menu without profile filters" action button.
- **SC-007**: Automated catalog filter evaluation and master toggle switching completes with a perceived UI latency of under 1 second under standard network conditions.

---

## Assumptions

- Dishes in restaurant catalogs dynamically reflect their active ingredients and allergen profiles; any recipe substitution made by kitchen staff (COM-1.4.3 CA4) automatically updates catalog filter compliance in real time, though the recipe editing action itself belongs to the kitchen/inventory management module.
- A transactional email dispatch service is operational to transmit email verification links with cryptographic tokens.
- Diners configuring profiles and experiencing automated catalog filtering are authenticated with valid session tokens.
- Session revocation on email change confirmation (FR-009) delegates directly to `token_version` on the `User` entity implemented in Spec 001.
- The user identity database model preserves backward compatibility with `full_name` from Spec 001/002 alongside `first_name` and `last_name`.
- Frontend profile and catalog interfaces reuse the existing Axios client (`apiClient.ts`), `AuthContext`, and `AppRoutes.tsx` with `RoleRouteGuard` from Spec 002 without duplicating authentication or routing infrastructure.
- Demographic profile updates (profile avatar, age, gender, delivery address) are governed by user story COM-1.4.2 and deferred to Sprint 3.
- Category filters, price range sliders (COM-4.2.2), and catalog keyword search (COM-4.2.3) are separate features scheduled for subsequent iterations and do not alter the profile dietary filter baseline.
