# Plan de Pruebas Manuales: Spec 006 - Recipe Food Cost

**Fecha**: 28/10/2026  
**Rama**: `006-recipe-food-cost`  
**Objetivo**: Validar manualmente en el entorno local el esquema transaccional N:M de recetas, las restricciones de estado del plato, el cálculo de Food Cost por porción (`ADM-5.1.2`) y la seguridad RBAC.

---

## 1. Preparación del Entorno Local

1. Levantar la base de datos PostgreSQL local:
   ```bash
   cd easyeat/backend
   docker compose up -d
   ```
2. Iniciar el backend con Java 21:
   ```bash
   mvn spring-boot:run
   ```
3. Iniciar el frontend (en otra terminal):
   ```bash
   cd easyeat/frontend
   npm run dev
   ```

---

## 2. Casos de Prueba Manuales

### Caso 1: Persistencia de Receta Técnica como Chef (Happy Path)
- **Rol requerido**: `CHEF`
- **Precondición**: Tener un plato creado en estado `DRAFT` y conocer IDs de insumos activos de inventario.
- **Acción**:
  Ejecutar la petición para asociar insumos a la receta:
  ```bash
  curl -X PUT http://localhost:8080/api/v1/dishes/{DRAFT_DISH_ID}/recipe \
    -H "Authorization: Bearer $CHEF_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "ingredients": [
        { "ingredientId": "'"$INGREDIENT_1_ID"'", "quantity": 0.25 },
        { "ingredientId": "'"$INGREDIENT_2_ID"'", "quantity": 150.0 }
      ]
    }'
  ```
- **Resultado esperado**:
  - Código HTTP `200 OK`.
  - El cuerpo retorna la lista con las 2 líneas persistidas.
  - En la base de datos, la tabla `dish_recipe_ingredients` contiene las 2 filas asociadas al `dish_id`.

---

### Caso 2: Rechazo de Cantidad Inválida y Duplicados
- **Rol requerido**: `CHEF`
- **Acción A (Cantidad <= 0)**:
  Enviar `quantity: 0` o valor negativo en uno de los insumos.
  - **Resultado esperado**: HTTP `400 Bad Request` con título `Invalid Recipe Ingredient Quantity`.
- **Acción B (Insumo duplicado)**:
  Enviar el mismo `ingredientId` dos veces en la misma solicitud.
  - **Resultado esperado**: HTTP `400 Bad Request` indicando duplicado.

---

### Caso 3: Bloqueo de Modificación de Receta Fuera de Borrador (Locking Invariant)
- **Rol requerido**: `CHEF`
- **Precondición**: Un plato que ya fue enviado a revisión y se encuentra en estado `PENDING_PRICE` o publicado.
- **Acción**:
  Intentar ejecutar `PUT /api/v1/dishes/{PENDING_DISH_ID}/recipe`.
- **Resultado esperado**:
  - Código HTTP `409 Conflict`.
  - El cuerpo ProblemDetail indica que el plato está bloqueado en estado `PENDING_PRICE`.

---

### Caso 4: Intento de Auditoría en Plato en Borrador (State Gate)
- **Rol requerido**: `ADMIN`
- **Precondición**: Plato en estado `DRAFT` con o sin receta.
- **Acción**:
  Consultar el endpoint de auditoría financiera:
  ```bash
  curl -X GET http://localhost:8080/api/v1/dishes/{DRAFT_DISH_ID}/cost-audit \
    -H "Authorization: Bearer $ADMIN_TOKEN"
  ```
- **Resultado esperado**:
  - Código HTTP `409 Conflict`.
  - El detalle especifica que el plato requiere estar en estado `PENDING_PRICE` para ser auditado.

---

### Caso 5: Auditoría Exitosa de Costo con Etiquetas Dietarias (Happy Path ADM-5.1.2)
- **Rol requerido**: `ADMIN`
- **Precondición**: Plato en estado `PENDING_PRICE` con receta técnica asignada e insumos con costos de adquisición cargados en inventario.
- **Acción**:
  ```bash
  curl -X GET http://localhost:8080/api/v1/dishes/{PENDING_DISH_ID}/cost-audit \
    -H "Authorization: Bearer $ADMIN_TOKEN"
  ```
- **Resultado esperado**:
  - Código HTTP `200 OK`.
  - `totalFoodCost` calculado exactamente como la suma de cada `(quantity * unitPurchaseCost)` redondeado a 2 decimales (`HALF_UP`).
  - `hasUnpricedIngredients` es `false` y `warningNotes` está vacío.
  - `requiredDietaryRestrictions` incluye los códigos declarados del plato (ej. `["CELIAC"]`).
  - Lista `items` con desglose detallado de cada insumo.

---

### Caso 6: Detección de Insumos sin Costo de Adquisición (Unpriced Warning)
- **Rol requerido**: `ADMIN`
- **Precondición**: Plato en estado `PENDING_PRICE` cuya receta contiene al menos un insumo con costo unitario de compra `0.00` o no asignado en inventario.
- **Acción**:
  Ejecutar `GET /api/v1/dishes/{PENDING_DISH_ID}/cost-audit`.
- **Resultado esperado**:
  - Código HTTP `200 OK` (no falla la auditoría).
  - La bandera `hasUnpricedIngredients` es `true`.
  - `warningNotes` contiene la advertencia con el nombre del insumo sin precio.
  - En la lista `items`, el insumo tiene `unpricedWarning: true` y subtotal `0.00`.
  - En el frontend, el componente `CostAuditPanel` muestra el banner amarillo de advertencia.

---

### Caso 7: Barrera de Confidencialidad Financiera (Seguridad RBAC)
- **Rol requerido**: `CHEF` o usuario sin rol `ADMIN`
- **Acción**:
  El Chef intenta acceder a `GET /api/v1/dishes/{PENDING_DISH_ID}/cost-audit`.
- **Resultado esperado**:
  - Código HTTP `403 Forbidden`.
  - Se protege la confidencialidad de los costos comerciales de proveedores frente al personal de cocina.
