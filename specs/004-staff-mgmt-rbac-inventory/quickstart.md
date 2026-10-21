# Quickstart Validation and Execution Guide (Phase 1)

**Feature**: Staff Management, RBAC, and Initial Ingredient Inventory  
**Branch**: `004-staff-mgmt-rbac-inventory`  
**Date**: 2026-10-20  
**Status**: Completed  

---

## 1. Prerequisites and Environment Setup

### Software Requirements
- **Java**: OpenJDK 21 (LTS).
- **Maven**: 3.9.x or higher.
- **Docker**: Docker Engine 24.x or higher with Testcontainers support.
- **PostgreSQL**: 16.x (for manual local execution; Testcontainers manages an isolated container during automated tests).

### Required Environment Variables
```bash
export JWT_SECRET="c2VjdXJlX2p3dF9zZWNyZXRfa2V5X2Zvcl9lYXN5ZWF0X3BsYXRmb3JtXzIwMjYh"
export SPRING_PROFILES_ACTIVE="test"
```

### Running Automated Test Suite
To execute the complete unit, integration, and security test suite:
```bash
cd easyeat/backend
./mvnw clean test
```

---

## 2. End-to-End Validation Scenarios

The following scenarios prove the full functional capabilities of this iteration. API payload schemas and response structures are defined in [contracts/openapi.yaml](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/contracts/openapi.yaml) and database schemas in [data-model.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/data-model.md).

---

### Scenario 1: Staff Registration and One-Time Temporary Credential Provisioning (ADM-3.1.1)

**Objective**: Verify that an Administrator can register an employee in their establishment and that the credential is delivered exactly once in plaintext for screen display.

1. **Authentication as Administrator**:
   Obtain an Administrator JWT token scoped to `tenant_id` `11111111-1111-1111-1111-111111111111`.
2. **Request**:
   `POST /api/v1/admin/staff`
   ```json
   {
     "fullName": "Carlos Garmendia",
     "email": "carlos.garmendia@easyeat.com",
     "role": "CHEF"
   }
   ```
3. **Expected Outcome**:
   - HTTP Status `201 Created`.
   - Response body contains `temporaryCredential` (e.g., `"Tmp$9wK2#mL1"`) and `status = "PENDING_FIRST_LOGIN"`.
   - Plaintext credential is **never** persisted in the database; only its BCrypt hash.
4. **Roster Verification**:
   `GET /api/v1/admin/staff`
   - HTTP Status `200 OK`.
   - Staff list includes Carlos Garmendia with status `PENDING_FIRST_LOGIN`.

---

### Scenario 2: First Login and Mandatory Password Change (FR-003)

**Objective**: Verify that a new staff member cannot perform operational tasks until completing the mandatory password change.

1. **Login with Temporary Credential**:
   `POST /api/v1/auth/login` with email `carlos.garmendia@easyeat.com` and the temporary credential.
   - HTTP Status `200 OK`. Returns JWT with claim `must_change_password: true`.
2. **Attempt Operational Request with Temporary Token**:
   `GET /api/v1/chef/ingredients` using this token.
   - HTTP Status `403 Forbidden` with detail: `"Must complete mandatory password change before accessing platform functionality"`.
3. **Mandatory Password Change**:
   `POST /api/v1/auth/first-login-password-change`
   ```json
   {
     "currentPassword": "Tmp$9wK2#mL1",
     "newPassword": "MyPermanentChefPassword2026!",
     "confirmNewPassword": "MyPermanentChefPassword2026!"
   }
   ```
   - HTTP Status `200 OK`.
   - User status in database transitions to `ACTIVE` and `must_change_password` is set to `false`.
4. **Operational Access Restored**:
   User logs in with the new password; new token allows browsing the kitchen ingredient catalog.

---

### Scenario 3: RBAC Enforcement and Multi-Tenant Isolation (ADM-3.1.2)

**Objective**: Demonstrate that role boundaries are enforced and cross-tenant data access is strictly prevented.

1. **Role-Based Denial**:
   - Authenticated as `CHEF`: Send `POST /api/v1/admin/ingredients`.
   - Result: HTTP `403 Forbidden` and a structured security audit event emitted in the log.
   - Authenticated as `COUNTER_CLERK`: Send `GET /api/v1/chef/ingredients`.
   - Result: HTTP `403 Forbidden`.
2. **Multi-Tenant Isolation**:
   - Administrator of Restaurant A (`tenant_id = 11111111-1111-1111-1111-111111111111`) queries or modifies an ingredient ID belonging to Restaurant B (`tenant_id = 22222222-2222-2222-2222-222222222222`).
   - Result: HTTP `404 Not Found` (or `403 Forbidden`), ensuring the existence of another tenant's data is never exposed.

---

### Scenario 4: Ingredient Creation, Validation, and Detail Editing (ADM-4.1.1)

**Objective**: Validate attribute constraints, fixed category set, and unit of measure modification locking.

1. **Valid Creation**:
   `POST /api/v1/admin/ingredients`
   ```json
   {
     "name": "Extra Virgin Olive Oil",
     "category": "Dry Goods",
     "unitOfMeasure": "l",
     "unitPurchaseCost": 4500.0000
   }
   ```
   - HTTP Status `201 Created`, initial status `ACTIVE`, `current_stock = 0.0000`.
2. **Duplicate Name Rejection**:
   Attempt to create another ingredient with the same name within the same tenant.
   - HTTP Status `409 Conflict`.
3. **Invalid Category or Unit Rejection**:
   Attempt creation with `category: "Beverages"` or `unitOfMeasure: "tons"`.
   - HTTP Status `400 Bad Request` with validation error details.

---

### Scenario 5: Initial Stock Load and Adjustments with Non-Negative Invariant (ADM-4.1.2)

**Objective**: Verify Absolute and Relative adjustment modes, mandatory justification, and negative stock rejection.

1. **Initial Stock Load (Absolute Mode)**:
   `POST /api/v1/admin/ingredients/{id}/stock-adjustments`
   ```json
   {
     "mode": "ABSOLUTE",
     "value": 20.0000,
     "justification": "Initial inventory load per receipt #1040"
   }
   ```
   - HTTP Status `200 OK`. `new_stock = 20.0000`, `net_delta = 20.0000`.
2. **Valid Relative Adjustment (Negative Delta / Spoilage)**:
   `POST /api/v1/admin/ingredients/{id}/stock-adjustments`
   ```json
   {
     "mode": "RELATIVE",
     "value": -3.5000,
     "justification": "Broken bottle during warehouse reorganization"
   }
   ```
   - HTTP Status `200 OK`. `previous_stock = 20.0000`, `new_stock = 16.5000`, `net_delta = -3.5000`.
3. **Negative Resulting Stock Rejection**:
   Send relative adjustment with `value = -25.0000` (would drop stock to `-8.5000`).
   - HTTP Status `422 Unprocessable Entity` with message: `"Resulting stock balance cannot fall below zero"`.
   - Current stock in database remains untouched at `16.5000`.
4. **Zero-Delta Rejection**:
   Send relative adjustment with `value = 0`.
   - HTTP Status `400 Bad Request` (or `422`).
5. **Immutable Audit Trail Verification**:
   `GET /api/v1/admin/ingredients/{id}/stock-audit`
   - HTTP Status `200 OK`. Returns 2 immutable records capturing administrator, delta, previous stock, and justification.

---

### Scenario 6: Concurrency Validation with Pessimistic Locking (FR-020)

**Objective**: Verify race-condition protection via `SELECT ... FOR UPDATE`.

1. **Integration Test Setup**:
   - Seed an ingredient with initial stock `10.0000`.
   - Launch two concurrent worker threads attempting to apply a relative adjustment of `-8.0000` each at the exact same moment.
2. **Expected Outcome**:
   - One thread acquires the row lock first and decrements `8.0000`, updating stock to `2.0000`.
   - The second thread unblocks, reads the updated stock of `2.0000`, and attempts to subtract `8.0000`; the non-negative invariant validation fails and the transaction safely rolls back with HTTP `422 Unprocessable Entity`.
   - Final stock is never `-6.0000`; it remains consistently at `2.0000` with exactly 1 new audit log entry.

---

### Scenario 7: Ingredient Lifecycle and Reactivation (FR-014)

**Objective**: Verify transition to `DISCONTINUED`, reactivation to `ACTIVE`, and archival rules.

1. **Discontinue with Positive Stock**:
   Ingredient with stock `16.5000`.
   `PATCH /api/v1/admin/ingredients/{id}/status` with `targetStatus = "DISCONTINUED"`.
   - HTTP Status `200 OK`. Ingredient status is now `DISCONTINUED`.
2. **Chef Catalog Exclusion**:
   `GET /api/v1/chef/ingredients`.
   - The discontinued ingredient is **no longer visible** in the Chef's view.
3. **Stock Adjustment on Discontinued Item**:
   `POST /api/v1/admin/ingredients/{id}/stock-adjustments` with absolute adjustment `value = 0` and reason `"Liquidation of remaining stock"`.
   - HTTP Status `200 OK`. Stock reaches `0.0000`.
4. **Reactivation vs. Archival**:
   - If Administrator sends `targetStatus = "ACTIVE"`, the ingredient transitions back to Active and reappears in the Chef catalog.
   - If Administrator sends `targetStatus = "ARCHIVED"` (when stock == `0.0000`), the ingredient archives permanently.
   - If archival is attempted while stock is > 0, API responds with HTTP `409 Conflict`.

---

### Scenario 8: Kitchen Ingredient Catalog Exploration by Chef (CHF-2.1.1)

**Objective**: Validate paginated kitchen catalog browsing with financial cost suppression.

1. **Chef Query**:
   `GET /api/v1/chef/ingredients?page=0&size=20`
2. **Expected Outcome**:
   - HTTP Status `200 OK`.
   - Ingredients sorted alphabetically ascending (A-Z) by name.
   - Each item includes `name`, `category`, `unitOfMeasure`, and `stockAvailability` (`IN_STOCK` or `OUT_OF_STOCK`).
   - Fields `unitPurchaseCost` and `stockAuditHistory` **never** appear in the response payload.
3. **Filtering and Search**:
   - `GET /api/v1/chef/ingredients?category=Produce&search=tomato`
   - Returns only produce ingredients matching the search keyword.
