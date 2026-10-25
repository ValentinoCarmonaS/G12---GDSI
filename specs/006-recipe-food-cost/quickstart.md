# Quickstart Validation Guide: Recipe Many-to-Many Model and Automated Food Cost Engine

**Feature**: `006-recipe-food-cost`  
**Date**: 25/10/2026  
**Audience**: Developers, QA Engineers, and Technical Evaluators

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

### 1.1 Valid Recipe Composition (Atomic Full Replacement)
```bash
curl -X PUT http://localhost:8080/api/v1/dishes/{dishId}/recipe \
  -H "Authorization: Bearer $CHEF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "ingredientId": "'"$INGREDIENT_FLOUR_ID"'",
        "quantity": 250.0,
        "unitOfMeasure": "G"
      },
      {
        "ingredientId": "'"$INGREDIENT_CHEESE_ID"'",
        "quantity": 150.0,
        "unitOfMeasure": "G"
      },
      {
        "ingredientId": "'"$INGREDIENT_EGG_ID"'",
        "quantity": 2.0,
        "unitOfMeasure": "U"
      }
    ]
  }'
```
**Expected Response**: `200 OK` with JSON array reflecting the 3 saved items.

### 1.2 Incompatible Unit Rejection (Masa vs Volumen)
```bash
curl -X PUT http://localhost:8080/api/v1/dishes/{dishId}/recipe \
  -H "Authorization: Bearer $CHEF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "ingredientId": "'"$INGREDIENT_FLOUR_ID"'",
        "quantity": 100.0,
        "unitOfMeasure": "ML"
      }
    ]
  }'
```
**Expected Response**: `400 Bad Request` with ProblemDetail stating dimensional incompatibility between `ML` and inventory unit `KG`.

### 1.3 Active Dish Rejection (Locking Invariant)
```bash
curl -X PUT http://localhost:8080/api/v1/dishes/{ACTIVE_DISH_ID}/recipe \
  -H "Authorization: Bearer $CHEF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "ingredientId": "'"$INGREDIENT_FLOUR_ID"'",
        "quantity": 200.0,
        "unitOfMeasure": "G"
      }
    ]
  }'
```
**Expected Response**: `409 Conflict` with ProblemDetail indicating that active published dishes cannot have their recipes altered in-place.

---

## Scenario 2: Financial Administrator Audits Food Cost & Labels (GET /cost-audit)

### 2.1 Successful Audit Query by Administrator
```bash
curl -X GET http://localhost:8080/api/v1/dishes/{dishId}/cost-audit \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Expected Response**: `200 OK` with:
- `totalFoodCost`: Exact decimal sum with 2 decimal places.
- `items`: Itemized lines showing ingredient names, portion amounts, converted purchase rates, and line subtotals.
- `requiredDietaryRestrictions`: Union of dietary tags (e.g. `["GLUTEN", "LACTOSE"]`).
- `hasUnpricedIngredients`: `false`.

### 2.2 Confidentiality Check (Chef Denied Access)
```bash
curl -X GET http://localhost:8080/api/v1/dishes/{dishId}/cost-audit \
  -H "Authorization: Bearer $CHEF_TOKEN"
```
**Expected Response**: `403 Forbidden` (RBAC authorization barrier protects commercial supplier costs from kitchen staff).

---

## Scenario 3: Automated Regression Test Execution

Run the narrow test slices and integration suite:

```bash
# Unit tests for Food Cost Engine and metric conversions
./mvnw test -Dtest=FoodCostEngineTest,UnitConversionServiceTest

# Controller and Security slice tests
./mvnw test -Dtest=DishRecipeControllerTest,AdminDishCostAuditControllerTest

# End-to-end database integration test with Testcontainers
./mvnw test -Dtest=RecipePersistenceIntegrationTest
```

