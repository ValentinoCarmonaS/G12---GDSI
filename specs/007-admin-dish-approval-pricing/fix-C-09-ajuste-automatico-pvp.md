# Fix C-09: Ajuste automático de precio final del plato por variación en costo de ingredientes

**Fecha**: 10/11/2026  
**Rama**: `fix/C-09-automatic-pvp-cost-adjustment` (origen: `develop/sprint-4`)  
**Módulos impactados**:
- `specs/004-staff-mgmt-rbac-inventory`
- `specs/006-recipe-food-cost`
- `specs/007-admin-dish-approval-pricing`
- `specs/008-diner-catalog-multi-tenant`

---

## 1. Contexto y Justificación del Requerimiento

En operaciones gastronómicas con alta rotación o volatilidad de precios de insumos, el ingreso de nuevas partidas de stock con variaciones en el costo unitario de compra (`unit_cost`) afecta directamente el costo de preparación de los platos (*Food Cost*). Si el precio de venta al público (PVP) permanece estático ante subas de costos, el margen comercial asignado se erosiona.

La corrección **C-09** formaliza el recálculo automático y sincronizado del PVP para platos en estado `PUBLISHED`, protegiendo el margen comercial convenido y garantizando que el catálogo del comensal exhiba el valor actualizado en tiempo real.

---

## 2. Requerimiento Formal C-09

**Como**: Administrador financiero / sistema.  
**Quiero**: Que el precio de venta al público (PVP) de un plato publicado se recalcule automáticamente cuando aumente el costo de sus insumos, manteniendo el porcentaje (%) de ganancia original asignado (`margin_percentage`).  
**Para**: Asegurar que la variación de costos de stock no erosione el margen del negocio y que el comensal visualice inmediatamente el precio actualizado en el catálogo.

### Criterios de Aceptación:
1. **Recálculo de Food Cost**: Dado un plato en estado `PUBLISHED`, cuando un administrador registra un ingreso de stock que incrementa el costo unitario de un ingrediente de su receta, el sistema recalcula en tiempo real el Food Cost acumulado del plato.
2. **Ajuste por alza de insumo**: Dado el incremento en el Food Cost del plato, cuando el sistema actualiza su precio final, calcula el nuevo PVP conservando el porcentaje original (`margin_percentage`):
   $$PVP_{nuevo} = \frac{\text{Food\_Cost}_{nuevo}}{1 - (\text{margin\_percentage} / 100)}$$
   con redondeo exacto `HALF_UP` a 2 decimales.
3. **Comportamiento asimétrico ante baja de insumo**: Dado un plato en estado `PUBLISHED`, cuando un ingreso de stock disminuye el costo de un ingrediente, el sistema actualiza el Food Cost interno, pero mantiene el PVP público sin cambios ($PVP_{nuevo} = PVP_{anterior}$), incrementando el margen neto del local.
4. **Visibilidad inmediata para el comensal**: Dado el recálculo automático del PVP por alza de insumos, cuando el sistema actualiza el plato publicado, el comensal visualiza el nuevo precio actualizado de inmediato en la pantalla de descubrimiento y en el catálogo del establecimiento (COM-1.5.1 / C-01 / COM-4.1.1).
5. **Auditoría completa**: Dado cualquier ajuste automático de precio en un plato publicado, el sistema registra un evento en la traza/historial de auditoría (`dish_price_history`) especificando: ingrediente modificado, costo anterior/nuevo del ingrediente, Food Cost anterior/nuevo del plato y el PVP resultante.

---

## 3. Decisiones de Diseño y Arquitectura (Grill-Me)

1. **Persistencia del margen original**:
   - Se incorpora la columna `margin_percentage NUMERIC(5,2)` en la tabla `dishes`.
   - Se persiste al momento en que el administrador fija el precio o aprueba el plato (`DishGovernanceService`).
   - Para platos existentes sin este campo, se deduce a partir del último registro en `dish_price_history` o se inicializa con el margen vigente.
2. **Ejecución y aislamiento transaccional**:
   - El recálculo y ajuste se dispara de manera sincrónica dentro de la transacción de ajuste de stock (`StockAdjustmentService`), garantizando consistencia atómica entre el inventario y el catálogo publicado.
3. **Persistencia de auditoría y actor**:
   - Cada ajuste automático crea un registro en `dish_price_history` con actor del administrador que ejecutó el ingreso de stock (o usuario del sistema), registrando el snapshot del nuevo Food Cost, el nuevo PVP y el detalle del ingrediente causante.

---

## 4. Matriz de Trazabilidad de Requisitos por Especificación

| Especificación | Requisito / Clave | Descripción del impacto |
|---|---|---|
| **Spec 004** (`staff-mgmt-rbac-inventory`) | `FR-015e` | Trigger síncrono al agregar stock (`action = ADD`) con cambio de `unit_purchase_cost`. |
| **Spec 006** (`recipe-food-cost`) | `FR-012` | Método de servicio interno para reevaluación inmediata de Food Cost de platos afectados. |
| **Spec 007** (`admin-dish-approval-pricing`) | `FR-010a`..`FR-010d`, `SC-010` | Persistencia de `margin_percentage`, fórmula de PVP ante alzas, preservación de PVP ante bajas, y traza de auditoría. |
| **Spec 008** (`diner-catalog-multi-tenant`) | `FR-033` | Exhibición del nuevo PVP en catálogo vivo y pantalla de descubrimiento sin demoras. |

