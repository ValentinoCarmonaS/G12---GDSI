# Feature Specification: Shopping Cart Management & Diner Profile Demographics

**Feature Branch**: `009-shopping-cart`

**Created**: 2026-10-31

**Status**: Approved

**Input**: User description: "Food ordering web system. Current iteration extends existing shopping cart and diner profile functionality. Four approved and finalized user stories: COM-6.1.1 (Add dish to order), COM-6.1.2 (View accumulated cart summary), COM-6.1.3 (Modify quantities), and COM-1.4.2 (Edit diner demographics)."

---

## Clarifications

### Session 2026-10-31 (Alignment with Approved User Stories & Domain Architecture)

- Q: Should the dish detail view allow selecting the quantity before pressing "Add to Cart", or does it always add 1 unit per click? (COM-6.1.1 AC-1.1, FR-001, FR-002) → A: The dish detail view includes an interactive numeric quantity selector (defaulting to 1 unit and bounded between 1 and the real kitchen stock available); pressing "Add to Cart" adds the selected quantity to the local session in a single interaction.
- Q: At what point should profile photo deletion take effect after confirming the action? (FR-024, FR-025, COM-1.4.2 AC-4.5) → A: Deferred confirmation on form submission: upon confirming image removal in the verification dialog, the UI updates the preview to provisionally display the default avatar; permanent removal on the server and top navigation bar is executed only when the user clicks "Save changes" in the demographic form. If the diner cancels or navigates away without saving, the original photo remains intact.
- Q: How should the cart behave if kitchen stock drops below a quantity already added while the diner navigates? (FR-014, INV-3) → A: Preserve the item in the cart marked with a visible "Insufficient stock" warning badge, disabling addition and blocking progression to checkout. The only way to proceed is for the diner to manually reduce the quantity to the remaining stock or remove the item from the order.
- Q: On which application screens should the floating cart button remain visible when it contains items? (FR-005, FR-008) → A: Exclusively within catalog views and dish detail views of the active establishment. It is intentionally hidden when entering profile edit, configuration, or authentication screens to avoid overlapping with forms.
- Q: How should the system validate kitchen stock and synchronize quantity changes when clicking plus or minus in the cart modal? (FR-014, FR-016) → A: Immediate optimistic UI update with deferred synchronization (300 ms debounce window) to the catalog/inventory service; if the backend detects insufficient stock or a network drop occurs, it immediately rolls back the counter to the last confirmed value and notifies the connection or stock error.
- Q: How is the shopping cart managed with respect to session and active establishment? (COM-6.1.1, COM-6.1.2) → A: The shopping cart is stored in the diner's local session and is strictly bound to the active establishment where the diner is browsing (consistent with Spec 008, where changing establishments prompts the user about clearing the cart).
- Q: What happens when adding a dish that already exists in the cart? (COM-6.1.1) → A: A new line item is not created; the system accumulates the quantity onto the existing dish by adding the selected units, provided it does not exceed the real kitchen stock.
- Q: What is the visibility behavior of the floating cart button? (COM-6.1.2) → A: Strictly bijective (INV-6 and INV-7): if the cart contains at least one item with quantity $\ge 1$, the floating button remains visible across catalog browsing views; if the cart reaches 0 items, the floating indicator hides immediately.
- Q: How is inventory availability validated when incrementing quantities? (COM-6.1.3) → A: The system checks real available kitchen stock. When the cart quantity equals that limit, the increment control is visually and functionally disabled, displaying an inventory limit warning or "out of stock".
- Q: How does the system respond to communication or network failure during rapid quantity modifications? (COM-6.1.3) → A: State rollback is applied: the item counter reverts to the last consistently synchronized value and an explicit connection error notification is displayed.
- Q: What are the validation rules for editing demographic data? (COM-1.4.2) → A:
  - Profile photo: valid image format (JPEG/JPG, PNG, WEBP), size strictly $> 0$ and $\le 5\text{ MB}$ (5,242,880 bytes). Files exceeding 5 MB or unsupported formats are rejected with specific explanatory messages. Photo removal restores the platform's default avatar.
  - Age: positive integer within a coherent biological range (14 to 120 years). Negative numbers, decimals, values $< 14$, values $> 120$, and alphanumeric characters are rejected.
  - Gender: optional selection from a closed standard taxonomy ("Female", "Male", "Non-binary", "Prefer not to say").
  - Address: free-form text field (up to 255 characters) intended exclusively as profile contact information, without performing geographical validations, route calculations, or integrations with external logistics or delivery platforms.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Add Dish to Order (Priority: P1)

As a diner,  
I want to click a button to add a dish to the cart,  
So that I can build my order before paying.

**Why this priority**: It is the fundamental conversion action in the food purchasing flow (`COM-6.1.1`). Without the ability to select and add dishes to the order, no orders or transactions can take place on the platform.

**Independent Test**: Can be tested independently by opening the detail view of any dish with available stock, clicking "Add to Cart", and verifying that the item is added to the local session with a visible success confirmation toast.

**Acceptance Scenarios**:

1. **Given** that the diner is on the detail view of a dish with available stock, **When** they adjust the desired quantity using the numeric selector (defaulting to 1 unit) and click "Add to Cart", **Then** that quantity of the dish is added to the diner's local session and the system displays a visible success toast confirming the addition.
2. **Given** that the diner adds a dish that is already present in the cart with quantity $k$, **When** they select $m$ units on the dish detail view and click "Add to Cart", and the accumulated quantity $k + m$ does not exceed real available stock, **Then** the system increments the existing unit quantity to $k + m$, updates totals, and shows success feedback without duplicating the line item.
3. **Given** that the diner views the detail of a dish whose real available kitchen stock is 0 (out of stock), **When** they inspect the dish actions, **Then** the "Add to Cart" button is disabled, preventing any attempt to add it to the local session.
4. **Given** that a dish in the cart already has a quantity equal to real available stock in the kitchen, **When** the diner attempts to click "Add to Cart" again from the detail view, **Then** the system blocks the action and presents an informational warning indicating that maximum product availability has been reached.
5. **Given** a diner with configured dietary restrictions viewing a conflicting dish according to Spec 003 policies, **When** they click "Add to Cart", **Then** the system displays the preventive health warning modal; upon confirming assumption of risk, the dish is added to the cart retaining its dietary warning badge.

---

### User Story 2 - View Accumulated Cart Summary & Interactive Drawer (Priority: P1)

As a diner,  
I want to see a floating button showing the number of selected items and total price,  
So that I can keep track of how much I am spending while browsing.

**Why this priority**: Provides continuous order visibility and budget control (`COM-6.1.2`). Enables the diner to inspect their purchase breakdown at any moment without interrupting catalog exploration or losing navigation context.

**Independent Test**: Can be tested independently by adding one or more dishes to the cart, browsing various catalog categories, and verifying the permanent presence of the floating button with total item count and exact monetary subtotal; clicking the floating button must open the drawer with the itemized breakdown; clearing the cart must hide the button immediately.

**Acceptance Scenarios**:

1. **Given** that the diner has at least one item in their local cart session, **When** browsing the establishment catalog views, **Then** the system displays a floating button visible at all times showing the total number of items and the accumulated monetary subtotal.
2. **Given** that the diner sees the floating button with loaded items, **When** they remove the last remaining item leaving the cart session empty (zero units), **Then** the system immediately hides the floating indicator from the interface.
3. **Given** that the diner clicks the floating button from any catalog view, **When** the system registers the interaction, **Then** it opens a drawer or modal showing the full order breakdown (dish name, unit price, quantity, line subtotal, and grand total).
4. **Given** that the diner adds or removes dishes from any point in the application, **When** the system processes the change, **Then** the floating button updates accumulated item count and subtotal in real time without requiring a page reload.

---

### User Story 3 - Modify Quantities & Remove Items (Priority: P1)

As a diner reviewing my order,  
I want to increase or decrease quantities of an added dish directly from the cart view,  
So that I can make quick adjustments without returning to the main menu.

**Why this priority**: Grants immediate flexibility and control over the order (`COM-6.1.3`). Eliminates navigation friction for quantity adjustments and protects consistency with kitchen physical inventory prior to checkout.

**Independent Test**: Can be tested independently by opening the cart drawer, clicking increment and decrement controls on an item, and verifying instant recalculation of line subtotals and grand total; verifying that reaching real stock disables addition; verifying that decrementing from quantity 1 removes the dish; and simulating a network failure to verify counter rollback and error alert.

**Acceptance Scenarios**:

1. **Given** that the diner has opened the cart drawer with added items, **When** they click the increment (+) control on a dish, **Then** the system increments the dish unit quantity by 1 and immediately recalculates the line subtotal and grand total.
2. **Given** that the diner has a dish with quantity $> 1$ in the cart drawer, **When** they click the decrement (-) control on that dish, **Then** the system decreases unit quantity by 1 and immediately recalculates line subtotal and grand total.
3. **Given** that the diner clicks increment on a dish, **When** the selected quantity reaches the maximum available kitchen stock for that product, **Then** the system disables the increment action and displays a visible warning indicating the inventory limit has been reached.
4. **Given** that the diner has exactly one unit (quantity = 1) of a dish in the cart, **When** they click decrement (-) on that dish, **Then** the system removes the product completely from the order, deletes the line item, and updates order totals.
5. **Given** that the diner rapidly modifies quantities of one or more dishes, **When** the system experiences a network failure or connection drop before synchronizing the new state with the service, **Then** it reverts the item counter to its previously confirmed numeric state and notifies the user of the connection error.

---

### User Story 4 - Edit Diner Demographics & Avatar Photo (Priority: P2)

As a diner,  
I want to add a profile photo, age, gender, and contact address,  
So that I can personalize my experience and streamline future orders.

**Why this priority**: Personalizes user identity on the platform and completes their contact profile (`COM-1.4.2`). Complements baseline personal data (first name, last name, email, and phone managed in Spec 003).

**Independent Test**: Can be tested independently by navigating to the demographic profile section, uploading a valid image $\le 5\text{ MB}$ and confirming immediate avatar update in the top navbar; attempting upload of files $> 5\text{ MB}$ or unsupported formats to verify rejection; entering valid and invalid age values; removing photo to validate restoration of the default avatar; and saving address to verify plain text storage as contact info.

**Acceptance Scenarios**:

1. **Given** that the diner edits their demographic data in the profile form, **When** they upload a profile image in an accepted format with size $\le 5\text{ MB}$ and save changes, **Then** the system updates their avatar and the new photo is immediately displayed in the top navigation bar.
2. **Given** that the diner attempts to upload a profile photo, **When** the file exceeds the 5 MB limit (5,242,880 bytes), **Then** the system rejects the file, blocks the upload, and displays a visible message stating the maximum allowed size (5 MB).
3. **Given** that the diner selects a file for their profile photo, **When** the file format is not an accepted image format (supported: JPEG, PNG, WEBP), **Then** the system blocks the upload and displays a message specifying supported formats.
4. **Given** that the diner fills the age field in the form, **When** they enter a negative number, $< 14$, $> 120$, decimals, or alphanumeric characters, **Then** the system invalidates the field, preventing submission and prompting for a coherent age.
5. **Given** that the diner fills the age field with an integer between 14 and 120 years and saves, **Then** the system validates and successfully persists the age in the user's profile.
6. **Given** that the diner has a custom profile photo configured, **When** they select delete image, confirm in the verification dialog, and click "Save changes", **Then** the system persistently removes the custom photo and restores the platform's default avatar in the navbar and profile. If they cancel or exit without saving, the previous photo is preserved intact.
7. **Given** that the diner enters their address in the profile form, **When** they save changes, **Then** the system stores the address purely as contact text in their profile, without computing delivery routes, distance estimations, or external logistics integrations.
8. **Given** that the diner selects their gender from the standard options ("Female", "Male", "Non-binary", "Prefer not to say") or leaves it unspecified, **When** they save the form, **Then** the system stores the selected preference in their profile.

---

### Edge Cases

- **Adding the same dish repeatedly**: Each addition adds $+m$ to the existing item quantity; duplicate line items for the same dish are never created in the cart.
- **Quantity selector bounds in detail view**: Clamped to integer values between 1 and remaining available kitchen stock; negative values, zero, decimals, or quantities exceeding available stock are disallowed.
- **Exact stock reached and repeated clicks**: If real kitchen stock is 5 units and the cart already has 5 units, increment remains disabled; extra clicks do not alter quantity or totals.
- **Concurrent stock drop in kitchen**: If an item is in the cart and kitchen stock drops below that quantity (even to 0), the product is not evicted; a prominent "Insufficient stock" warning is shown, increment is disabled, and checkout is blocked until adjusted or removed.
- **Decrementing on quantity 1**: Decrementing quantity 1 completely removes the item from the cart without leaving 0 or negative quantities.
- **Clearing cart from drawer**: Removing the final item updates the drawer to an empty state and immediately hides the floating cart button.
- **Rapid clicks and network disconnection**: Rapid clicks update the UI optimistically and debounce backend sync by 300 ms; if the call fails due to connection loss or stock rejection, the item counter reverts to the last confirmed value with an error alert.
- **Cent precision and accumulated totals**: Fractional monetary values (e.g. \$9.99, \$14.50) compute subtotals and grand totals using exact two-decimal fixed-point integer cent arithmetic, eliminating IEEE 754 drift.
- **Dish with initial stock of 0**: "Add to Cart" action is disabled with an "Out of stock" badge in both catalog card and detail modal.
- **Profile image file of exactly 5.0 MB**: A file of exactly 5,242,880 bytes is accepted as valid (inclusive upper bound). A file of 5,242,881 bytes is rejected.
- **Unsupported MIME format**: Files like `.pdf`, `.gif`, `.svg`, `.exe`, or `.bmp` are rejected upon selection before server upload, informing allowed formats (JPEG, PNG, WEBP).
- **Age numerical boundaries**: 13 or younger, and 121 or older, are rejected as biologically incoherent. 14 and 120 are accepted. Negative signs, decimals, or non-numeric characters invalidate the field immediately.
- **Photo deletion without existing photo**: If the user has only the default avatar, delete photo action is disabled or hidden.
- **Free-form contact address**: Accepts alphanumeric strings up to 255 characters with spaces and punctuation, treated strictly as descriptive contact info.
- **Closing modal without discarding changes**: Closing the cart drawer preserves all selected items and quantities in the local session.

---

## Requirements *(mandatory)*

### Mathematical & Business Invariants

- **INV-1 (Cart total quantity)**: The total quantity of items displayed in the floating indicator and order summary must equal the arithmetic sum of unit quantities of all items in the local session:
  $$\text{cart\_total\_quantity} = \sum_{i=1}^{n} \text{unit\_quantity}_i$$
  *Numerical example*: If the cart contains 2 units of Dish A and 3 units of Dish B, the total cart quantity is strictly $2 + 3 = 5$ units.

- **INV-2 (Exact monetary subtotal and total)**: The monetary subtotal of each item is unit retail price multiplied by quantity, and cart grand total is the sum of item subtotals:
  $$\text{item\_subtotal}_i = \text{unit\_price}_i \times \text{unit\_quantity}_i$$
  $$\text{cart\_subtotal} = \sum_{i=1}^{n} \text{item\_subtotal}_i$$
  All calculations must be performed with exact two-decimal fixed-point precision in integer cents, without floating-point drift. Comparisons use strict arithmetic equality.
  *Numerical example*: 3 items at \$12.33 total exactly \$36.99; added to 1 item at \$7.01 gives an exact total of \$44.00.

- **INV-3 (Available stock upper bound)**: For every item in the cart, requested quantity must never exceed available kitchen stock:
  $$\forall i \in \text{Cart}, \quad \text{unit\_quantity}_i \le \text{available\_stock}_i$$
  When equality is reached ($\text{unit\_quantity}_i = \text{available\_stock}_i$), increment actions must be disabled. If kitchen stock drops concurrently ($\text{unit\_quantity}_i > \text{available\_stock}_i$), the item enters a blocked state ("Insufficient stock") and the system prevents checkout until adjusted or removed.

- **INV-4 (Profile image size boundary)**: Any uploaded avatar file must have a size in bytes strictly greater than 0 and less than or equal to 5 MB (5,242,880 bytes):
  $$0 < \text{image\_size\_bytes} \le 5,242,880$$
  Any file outside this interval must be rejected.

- **INV-5 (Coherent biological age range)**: Age entered by diner must be an integer strictly between 14 and 120 years:
  $$\text{age} \in \mathbb{Z} \quad \land \quad 14 \le \text{age} \le 120$$
  Negative numbers, decimals, and non-numeric characters are rejected.

- **INV-6 (Empty cart hiding)**: The cart session is empty if and only if the floating button is completely hidden from the interface:
  $$\text{cart\_session\_empty} \iff \text{floating\_button\_hidden}$$

- **INV-7 (Non-empty cart contextual visibility)**: The cart session holds $\ge 1$ item and the diner is browsing the active establishment catalog if and only if the floating button is visible:
  $$\neg(\text{cart\_session\_empty}) \land \text{in\_catalog\_view} \iff \text{floating\_button\_visible}$$
  Displaying updated values for $\text{cart\_total\_quantity}$ and $\text{cart\_subtotal}$ at all times.

- **INV-8 (Functional address decoupling)**: The profile address is stored and accessed exclusively as descriptive contact text:
  $$\text{address} \implies \text{profile\_contact\_data}$$
  It does not feed route engines, calculate shipping fees, generate map coordinates, or transmit to external logistics services in this iteration.

- **INV-9 (Reversibility & deferred optimistic sync)**: During continuous quantity adjustments, the client applies immediate visual feedback and batches changes with a 300 ms debounce window; upon network drop or inventory conflict, the item counter must deterministically revert to the last confirmed value:
  $$\text{network\_error\_or\_insufficient\_stock} \implies \text{counter\_rolled\_back} \land \text{error\_notification}$$

---

### Functional Requirements

- **FR-001**: The system MUST provide on the dish detail view an interactive quantity selector (defaulting to 1 unit, bounded between 1 and available kitchen stock) along with the "Add to Cart" action. *(Covers COM-6.1.1 AC-1.1)*
- **FR-002**: Upon clicking "Add to Cart", the system MUST record the selected quantity in the diner's local session and display a visible success toast. *(Covers COM-6.1.1 AC-1.1)*
- **FR-003**: If the diner adds a dish already in the cart with quantity $k$, the system MUST add selected quantity $m$ to existing quantity ($k + m$) without duplicate lines, provided $k + m \le \text{availableStock}$. *(Covers COM-6.1.1 AC-1.1, INV-1, INV-3)*
- **FR-004**: The system MUST maintain the shopping cart associated with the active establishment context. *(Consistency with Spec 008 FR-029)*
- **FR-005**: The system MUST display an interactive floating button exclusively within catalog browsing and dish detail views of the active establishment, provided the cart contains at least 1 item ($\ge 1$), hiding on profile or settings views. *(Covers COM-6.1.2 AC-2.1, INV-7)*
- **FR-006**: The floating button MUST display total accumulated items and consolidated monetary subtotal at all times. *(Covers COM-6.1.2 AC-2.1, INV-1, INV-2)*
- **FR-007**: The system MUST update quantities and subtotal on the floating button in real time after any addition, modification, or removal, without page reload. *(Covers COM-6.1.2 AC-2.4)*
- **FR-008**: When total items in the cart reach zero, the system MUST hide the floating button immediately. *(Covers COM-6.1.2 AC-2.2, INV-6)*
- **FR-009**: Upon clicking the floating button, the system MUST display a drawer or modal showing the full order breakdown. *(Covers COM-6.1.2 AC-2.3)*
- **FR-010**: The detailed cart view MUST show for each dish: name, unit retail price, increment/decrement controls, item subtotal, and consolidated grand total. *(Covers COM-6.1.2 AC-2.3, COM-6.1.3 AC-3.1)*
- **FR-011**: From the cart view, the system MUST allow clicking increment (+) and decrement (-) controls for each dish. *(Covers COM-6.1.3 AC-3.1)*
- **FR-012**: Clicking increment (+) MUST increase dish quantity by 1 and instantly recalculate line subtotal and grand total. *(Covers COM-6.1.3 AC-3.1, INV-1, INV-2)*
- **FR-013**: Clicking decrement (-) on a dish with quantity $> 1$ MUST reduce quantity by 1 and instantly recalculate line subtotal and grand total. *(Covers COM-6.1.3 AC-3.1, INV-1, INV-2)*
- **FR-014**: If the diner attempts to increment and quantity equals available kitchen stock, the system MUST disable the control and display an inventory limit warning. *(Covers COM-6.1.3 AC-3.2, INV-3)*
- **FR-014a**: If kitchen stock drops below cart quantity while browsing or reviewing, the system MUST preserve the item without eviction, display an "Insufficient stock" warning badge, disable increment, and block checkout until adjusted or removed. *(Covers COM-6.1.3 AC-3.2, INV-3)*
- **FR-015**: If a dish has quantity 1 and decrement (-) is clicked, the system MUST remove the item completely from the order, delete the line item, and update totals. *(Covers COM-6.1.3 AC-3.3)*
- **FR-016**: The system MUST apply optimistic UI updates for +/- clicks, batching modifications with a 300 ms debounce window; upon network failure or stock rejection, the system MUST deterministically roll back the counter to the last confirmed value and display an error alert. *(Covers COM-6.1.3 AC-3.4, INV-9)*
- **FR-017**: All monetary calculations (item subtotals and grand total) MUST be processed with two-decimal fixed-point precision in integer cents. *(Covers INV-2)*
- **FR-018**: The system MUST provide a demographic profile editing form including fields: profile photo (avatar), age, gender, and contact address. *(Covers COM-1.4.2)*
- **FR-019**: The system MUST validate that avatar files belong to accepted formats: JPEG (`.jpg`, `.jpeg`), PNG (`.png`), or WEBP (`.webp`). *(Covers COM-1.4.2 AC-4.3)*
- **FR-020**: If an unsupported file format is selected, the system MUST block upload and display an error specifying accepted formats. *(Covers COM-1.4.2 AC-4.3)*
- **FR-021**: The system MUST validate that avatar image size does not exceed 5 MB (5,242,880 bytes). *(Covers COM-1.4.2 AC-4.1, AC-4.2, INV-4)*
- **FR-022**: If an image exceeds 5 MB, the system MUST reject the file and display an error indicating the maximum allowed size. *(Covers COM-1.4.2 AC-4.2, INV-4)*
- **FR-023**: Upon successfully saving a valid new profile photo, the system MUST persist the image in the user profile and immediately update the avatar displayed in the top navbar. *(Covers COM-1.4.2 AC-4.1)*
- **FR-024**: The system MUST provide an explicit action to delete an existing profile photo, prompting confirmation in a verification dialog. *(Covers COM-1.4.2 AC-4.5)*
- **FR-025**: Upon confirming deletion in the dialog, the system MUST update the form preview to provisionally display the default avatar; server persistence and navbar update MUST consolidate only when clicking "Save changes" in the form. If canceled without saving, the previous photo is preserved. *(Covers COM-1.4.2 AC-4.5)*
- **FR-026**: The system MUST validate that age is an integer within the biological range of 14 to 120 years. *(Covers COM-1.4.2 AC-4.4, INV-5)*
- **FR-027**: If the user enters a negative, $< 14$, $> 120$, decimal, or alphanumeric age, the system MUST mark the field invalid, prevent saving, and prompt for a coherent age. *(Covers COM-1.4.2 AC-4.4, INV-5)*
- **FR-028**: The system MUST allow the diner to select gender from options: "Female", "Male", "Non-binary", "Prefer not to say", or leave it unspecified as optional. *(Covers COM-1.4.2)*
- **FR-029**: The system MUST allow entering a contact address up to 255 characters as an optional field. *(Covers COM-1.4.2 AC-4.6, INV-8)*
- **FR-030**: The system MUST store address purely as contact text without geocoding, route estimation, shipping fee calculation, or logistics integrations. *(Covers COM-1.4.2 AC-4.6, INV-8)*
- **FR-031**: Demographic editing MUST maintain compatibility and preserve baseline user data (name, email, phone) and dietary restrictions from Spec 003. *(Consistency with Spec 003)*
- **FR-032**: If the diner cancels profile editing before clicking save, the system MUST discard unconfirmed modifications and keep previously persisted demographic values. *(Covers COM-1.4.2)*

---

### Key Entities

- **Shopping Cart (`ShoppingCart` / Local Session)**: Transient order container in diner's local session, bound to active establishment. Attributes: session ID, establishment ID (`tenantId`), line items, total quantity, subtotal amount, floating button visibility.
- **Cart Line Item (`CartItem`)**: Gastronomic dish added to the order. Attributes: dish ID, dish name, unit retail price, quantity ($\ge 1$), line subtotal, real available kitchen stock, dietary warning flags.
- **Dish Catalog Reference (`Dish Reference`)**: Read-only reference from active catalog. Supplies dish name, retail price, and available stock.
- **Diner Demographics (`DinerDemographics`)**: Extension of user profile storing demographic attributes: user ID, avatar reference, age (integer 14..120 or null), gender (closed taxonomy), contact address (string $\le 255$).
- **Profile Avatar (`ProfileAvatar`)**: Visual user representation in navbar and profile header. Stored as custom binary file (JPEG/PNG/WEBP $\le 5\text{ MB}$) in `user_photos` (BYTEA) or fallback default SVG asset.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Diner can add desired dish quantity to cart in at most 2 interactions from dish detail view (selector adjustment + "Add to Cart" click).
- **SC-002**: Floating cart button reflects updated item count and subtotal in $\le 1\text{ s}$ after any addition, modification, or removal without page reload.
- **SC-003**: 100% of attempts to add or increment units beyond available kitchen stock are blocked immediately with a visible warning.
- **SC-004**: 100% of network failures during rapid quantity modification result in deterministic counter rollback to the last confirmed state and user notification.
- **SC-005**: 100% of profile image files exceeding 5 MB (5,242,880 bytes) or unsupported formats are rejected with clear error messages without mutating previous avatar.
- **SC-006**: 100% of incoherent age values ($< 14$, $> 120$, negative, decimal, alphanumeric) are intercepted and invalidated, preventing profile corruption.
- **SC-007**: Subtotal displayed in floating button and cart drawer strictly matches arithmetic sum of $(\text{unit\_price} \times \text{quantity})$ with two-decimal precision.
- **SC-008**: Upon successfully saving after confirming photo deletion, default avatar is restored in top navbar and profile in $\le 1\text{ s}$.
- **SC-009**: Upon removing the last item from the cart, floating button hides in $\le 500\text{ ms}$.

---

## Assumptions

- **A-001**: Diner is authenticated with role `DINER` (COMENSAL) via Spec 001/002.
- **A-002**: Catalog dishes have approved retail prices via Spec 007 and available stock governed by recipes and ingredients via Specs 004/005/006.
- **A-003**: Cart session is managed locally in browser `sessionStorage`; switching establishments in catalog triggers confirmation modal per Spec 008.
- **A-004**: Age range is closed between 14 and 120 years inclusive.
- **A-005**: Address is entered as a single free-form string up to 255 characters without cadastral validation.
- **A-006**: Avatar image formats comprise universal web standards: JPEG/JPG, PNG, WEBP.
- **A-007**: 5 MB limit is strictly inclusive at 5,242,880 bytes ($5 \times 1024 \times 1024$ bytes).
- **A-008**: Default institutional avatar is served as a local static SVG asset.

---

## Out of Scope

- **Payment Gateway & Checkout Processing**: Deferred to Sprint 4.
- **Kitchen Order Dispatch**: Order ticket issuance and KDS transmission (Sprint 4).
- **Delivery Logistics & Routing**: Route estimation, distance calculation, courier assignment.
- **Authentication, Registration & Recovery**: Covered by Specs 001 and 002.
- **Baseline Personal Data & Dietary Preferences**: First/last name, phone, email, allergen tags (Spec 003).
- **Catalog Browsing, Filters & Search**: Covered by Spec 008.
- **Order History & Completed Purchases**.
- **Promotional Coupons & Marketing Discounts**.
- **Multi-Device Real-Time Cart Synchronization**.
- **Multi-Currency Handling**: Operates solely in establishment legal tender.
