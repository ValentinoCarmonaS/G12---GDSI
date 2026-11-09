# Contract 06: Descuento Atómico de Stock de Insumos por Receta (CHF-5.1.1)

**Tipo de Contrato**: Contrato de Servicio de Dominio Interno / Transaccional (`StockDeductionService`)  
**Componente Responsable**: `com.easyeat.inventory.service.StockDeductionService`  
**Consumidores**: `CheckoutPaymentService` (al confirmarse el pago por pasarela o bonificación 100%)  
**Garantía Transaccional**: `@Transactional(isolation = Isolation.READ_COMMITTED)` con `SELECT ... FOR UPDATE`  

---

## 1. Descripción

Este contrato gobierna la deducción indivisible, atómica y matemática exacta de todos los insumos que integran las recetas técnicas de los platos de una orden confirmada (INV-01, FR-007, FR-008, FR-009, CB-07, CB-08, AS-6, AS-6b).  
Reglas operativas:
1. **Multiplicación por Cantidad (FR-009)**: Cada línea de receta técnica se multiplica por la cantidad de unidades de ese plato contenidas en el pedido.
2. **Consolidación de Insumos Compartidos (C3, CB-06)**: Si múltiples platos demandan la misma materia prima, se acumula la cantidad total necesaria en una sola suma antes de comprobar existencias.
3. **Bloqueo Ordenado Anti-Deadlock**: Las filas de `ingredients` se bloquean ascendentemente por `ingredient_id ASC`.
4. **Principio "Todo o Nada" (INV-01, FR-008)**: Si tan solo un ingrediente cuenta con stock inferior a la demanda total acumulada, se produce un rollback completo: **cero descuentos parciales**.
5. **Auditoría Inmutable**: Cada deducción genera un registro en `stock_audit_entries` con la cantidad exacta debitada y el stock resultante.
6. **Idempotencia (INV-07)**: Si la orden ya fue debitada previamente, el método retorna exitoso sin volver a descontar materias primas.

---

## 2. Definición del Método de Dominio (Java Contract)

```java
public interface StockDeductionService {

    /**
     * Ejecuta el débito atómico de todos los insumos de las recetas de los platos de la orden.
     *
     * @param orderId      Identificador único de la orden confirmada.
     * @param tenantId     Identificador del establecimiento gastronómico.
     * @param orderItems   Lista de platos y cantidades solicitadas en la orden.
     * @return Resultado del débito atómico con detalle de insumos afectados y bandera de idempotencia.
     * @throws InsufficientIngredientStockException Si algún insumo no posee existencias suficientes (Rollback total).
     */
    StockDeductionResult deductRecipeStockForOrder(
            UUID orderId,
            UUID tenantId,
            List<OrderItemSnapshot> orderItems
    );
}
```

---

## 3. Estructuras de Datos del Contrato

### 3.1. Entrada: `OrderItemSnapshot`

| Campo | Tipo | Restricción | Descripción |
|---|---|---|---|
| `dishId` | UUID | Not Null | Identificador del plato ordenado. |
| `dishName` | String | Not Null | Nombre del plato. |
| `quantity` | Integer | $> 0$ | Cantidad de porciones ordenadas. |

### 3.2. Salida: `StockDeductionResult`

| Campo | Tipo | Descripción |
|---|---|---|
| `orderId` | UUID | Identificador de la orden procesada. |
| `tenantId` | UUID | Identificador del restaurante. |
| `totalIngredientsAffected` | Integer | Número de materias primas distintas debitadas. |
| `deductions` | List<IngredientDeductionDetail> | Detalle por cada insumo con stock anterior, cantidad debitada y stock resultante. |
| `executedAt` | Instant | Timestamp exacto en que se concretó la transacción. |
| `alreadyProcessed` | Boolean | `true` si la orden ya había sido debitada previamente (idempotencia). |

### 3.3. Detalle por Insumo: `IngredientDeductionDetail`

```json
{
  "ingredientId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "ingredientName": "Carne vacuna picada",
  "unitOfMeasure": "KILOGRAM",
  "quantityDebited": 0.3000,
  "previousStock": 5.4500,
  "newStock": 5.1500,
  "criticalThreshold": 2,
  "isNowCritical": false
}
```

---

## 4. Excepciones de Negocio y Semántica de Rollback

### `InsufficientIngredientStockException`
- **Condición**: Lanzada cuando para algún insumo $i$:
  $$\text{current\_stock}(i) < \text{total\_demanded}(i)$$
- **Efecto Transaccional**: Rollback integral de la transacción de base de datos.
- **Datos de la Excepción**:
  - `ingredientId`: UUID del insumo faltante.
  - `ingredientName`: Nombre del insumo sin existencias.
  - `demandedQuantity`: Cantidad requerida por la orden.
  - `availableQuantity`: Existencia actual en cocina.
  - `unitOfMeasure`: Unidad de medida.

---

## 5. Garantía de Precisión Fraccionaria (CB-07, SC-009)

Todas las operaciones aritméticas de cálculo de cantidades y deducción de stock utilizan `java.math.BigDecimal` con:
- Escala: `4` decimales.
- Modo de redondeo: `RoundingMode.HALF_UP`.
- Cero conversiones intermedias a tipos de punto flotante (`float`, `double`), asegurando cero pérdida de gramos o mililitros entre las recetas y el inventario.

