# Quickstart Validation Guide: Recipe Many-to-Many Model and Automated Food Cost Engine

**Feature**: `006-recipe-food-cost`  
**Date**: 25/10/2026  
**Audience**: Developers, QA Engineers, and Technical Evaluators (Aligned with Spec 005)

This guide outlines runnable scenarios to verify the recipe persistence and automated food cost auditing capabilities against the running EasyEat backend service.

---

## Prerequisites

1. PostgreSQL database running locally via Docker Compose:
   ```bash
   cd easyeat/backend && docker-compose up -d
   ```
2. Build and start the backend service:
   ```bash
   ./mvnw clean spring-boot:run
   ```
3. Authenticate to obtain JWT tokens for `CHEF` and `ADMIN` test accounts.

---

## Scenario 1: Chef Persists a Technical Recipe (PUT /recipe)

### 1.1 Valid Recipe Composition (Atomic Full Replacement in DRAFT)
```bash
curl -X PUT http://localhost:8080/api/v1/dishes/{draftDishId}/recipe \
  -H "Authorization: Bearer $CHEF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ingredients": [
      {
        "ingredientId": "'"$INGREDIENT_FLOUR_ID"'",
        "quantity": 250.0
      },
      {
        "ingredientId": "'"$INGREDIENT_CHEESE_ID"'",
        "quantity": 150.0
      },
      {
        "ingredientId": "'"$INGREDIENT_EGG_ID"'",
        "quantity": 2.0
      }
    ]
  }'
```
**Expected Response**: `200 OK` with JSON array reflecting the 3 saved items linked in `dish_recipe_ingredients`.

### 1.2 Recipe Modification Blocked on Submitted Dish (Locking Invariant)
```bash
curl -X PUT http://localhost:8080/api/v1/dishes/{pendingPriceDishId}/recipe \
  -H "Authorization: Bearer $CHEF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ingredients": [
      {
        "ingredientId": "'"$INGREDIENT_FLOUR_ID"'",
        "quantity": 300.0
      }
    ]
  }'
```
**Expected Response**: `409 Conflict` with ProblemDetail indicating that recipe editing is locked once the dish is submitted for review (`PENDING_PRICE`).

---

## Scenario 2: Financial Administrator Audits Food Cost & Labels (GET /cost-audit)

### 2.1 Audit Blocked on Draft Dish (State Gate)
```bash
curl -X GET http://localhost:8080/api/v1/dishes/{draftDishId}/cost-audit \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected Response**: `409 Conflict` with ProblemDetail stating that dishes must be in `PENDING_PRICE` status to be audited.

### 2.2 Successful Audit Query on Submitted Dish (PENDING_PRICE)
```bash
curl -X GET http://localhost:8080/api/v1/dishes/{pendingPriceDishId}/cost-audit \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected Response**: `200 OK` with:
- `totalFoodCost`: Exact decimal sum with 2 decimal places.
- `items`: Itemized lines showing ingredient names, portion amounts, native units of measure, unit acquisition costs, and line subtotals.
- `requiredDietaryRestrictions`: Declared dietary restriction codes (e.g. `["CELIAC", "DAIRY"]`).
- `hasUnpricedIngredients`: `false`.

### 2.3 Financial Confidentiality Check (Chef Denied Access)
```bash
curl -X GET http://localhost:8080/api/v1/dishes/{pendingPriceDishId}/cost-audit \
  -H "Authorization: Bearer $CHEF_TOKEN"
```
**Expected Response**: `403 Forbidden` (RBAC authorization barrier strictly protects commercial supplier costs from kitchen staff).

---

## Scenario 3: Automated Regression Test Execution

Run the narrow test slices and integration suite:

```bash
# Unit tests for Food Cost Engine and decimal arithmetic
./mvnw test -Dtest=FoodCostEngineTest

# Controller and Security slice tests
./mvnw test -Dtest=AdminDishCostAuditControllerTest

# End-to-end database integration test with Testcontainers
./mvnw test -Dtest=DishCostAuditIntegrationTest
```
