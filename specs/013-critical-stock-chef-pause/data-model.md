# Conceptual Data Model & Relational Mapping: Umbral de Stock Crítico y Pausa Manual de Platos

**Feature**: `013-critical-stock-chef-pause`  
**Fecha**: 01/11/2026  
**Fuentes primarias**: `specs/013-critical-stock-chef-pause/spec.md §Key Entities`, `§INV-1` a `§INV-19`, `easyeat/backend/src/main/resources/db/migration/`.

---

## 1. Modelo Conceptual de Dominio

El modelo representa las materias primas del establecimiento, sus parámetros de criticidad operativa, y las tres dimensiones independientes de disponibilidad de los platos terminados.

```
+-----------------------------------------------------------------------------------+
|                                  ESTABLECIMIENTO                                  |
|                                    (tenant_id)                                    |
+-----------------------------------------------------------------------------------+
             |                                                  |
             | 1:N                                              | 1:N
             v                                                  v
+--------------------------+                      +--------------------------+
|       INGREDIENTE        |                      |          PLATO           |
|--------------------------|                      |--------------------------|
| id: UUID                 |                      | id: UUID                 |
| tenant_id: UUID          |                      | tenant_id: UUID          |
| name: String             |                      | name: String             |
| category: Category       |                      | category: DishCategory   |
| unit_of_measure: UOM     |                      | status: DishStatus       |
| current_stock: Decimal   |                      | retail_price: Decimal    |
| status: ACTIVE/DISC      |                      +--------------------------+
+--------------------------+                                   |
             |                                                 |
             | 1:1 (Embed/Attr)                                | 1:1:1 (Tres Estados Independientes)
             v                                                 v
+------------------------------------+    +--------------------------------------------------+
|      UMBRAL DE STOCK CRÍTICO       |    |     TRES CAUSAS DE INDISPONIBILIDAD (INV-10)     |
|------------------------------------|    |--------------------------------------------------|
| threshold: Integer >= 0 (Nullable) |    | 1. sin_stock (Derivado dinámico de recetas)      |
+------------------------------------+    | 2. paused_by_chef: Boolean (Toggle Chef)         |
             |                            | 3. is_active: Boolean & status (Admin)           |
             | Evalúa dinámicamente       +--------------------------------------------------+
             v                                                 |
+------------------------------------+                         | Resuelve disponibilidad
|        ESTADO DE CRITICIDAD        |                         v
|------------------------------------|            +-----------------------------------+
| isCritical: Boolean (Derivado)     |            |  DISPONIBILIDAD CATÁLOGO COMENSAL |
|   current_stock < threshold        |            |-----------------------------------|
|   && status == ACTIVE              |            | Visible: PUBLISHED & active & !pau|
+------------------------------------+            | Comprable: Visible & con stock    |
                                                  +-----------------------------------+
```

---

## 2. Entidades de Dominio

### 2.1 Ingrediente (`Ingredient`)
- **Descripción**: Insumo o materia prima culinaria administrable en el módulo de inventario del establecimiento gastronómico.
- **Atributos de Dominio**:
  - `id`: Identificador unívoco del ingrediente (`UUID`).
  - `tenantId`: Identificador del restaurante/establecimiento propietario (`UUID`).
  - `name`: Nombre descriptivo del insumo (`String`, 1 a 255 caracteres, único por tenant insensensible a mayúsculas).
  - `category`: Categoría operativa (`IngredientCategory`: `Produce`, `Meat`, `Dairy`, `Dry Goods`, `Spices`).
  - `unitOfMeasure`: Unidad física de control (`UnitOfMeasure`: `kg`, `g`, `l`, `ml`, `u`). Inmutable si `currentStock > 0`.
  - `unitPurchaseCost`: Costo unitario de compra (`BigDecimal`, precisión 12, escala 4).
  - `status`: Estado del ciclo de vida (`IngredientStatus`: `ACTIVE`, `DISCONTINUED`).
  - `currentStock`: Cantidad física remanente real (`BigDecimal`, precisión 12, escala 4, $\ge 0$).
  - `createdAt`, `updatedAt`: Marcas temporales de auditoría (`Instant`).
- **Invariantes Locales**:
  - `currentStock >= 0`: Garantizado por el bloqueo pesimista en ajustes (spec 004).
  - Solo los usuarios con rol `ADMIN` pueden crearlo, editarlo o descontinuarlo.

---

### 2.2 Umbral de Stock Crítico (`CriticalStockThreshold`)
- **Descripción**: Parámetro numérico entero individual fijado por el administrador para un insumo específico. Define la cota estricta de seguridad operativa para reposición de compras.
- **Atributos de Dominio**:
  - `criticalStockThreshold`: Valor numérico entero $\ge 0$ (`Integer`, nullable).
- **Invariantes Locales**:
  - **Individualidad estricta (INV-1, SC-001)**: Cada ingrediente tiene su propio valor. No existe parámetro global, único ni por categoría.
  - **Dominio entero no negativo (INV-2, FR-002, SC-002)**: $\text{threshold} \in \mathbb{N}_0 \cup \{\text{null}\}$. Negativos, fracciones o decimales son rechazados en validación.
  - **Inexistencia de tope artificial (EC-022)**: Admite cualquier entero $\ge 0$ sin límites superiores preestablecidos.
  - **Tratamiento de valor nulo / no configurado (INV-7, EC-002)**: Si es `null`, el ingrediente se considera sin referencia fijada y no emite alertas.
  - **Tratamiento de valor cero (EC-001)**: Si es `0`, el ingrediente solo alertará ante la anomalía de stock estrictamente negativo ($< 0$).
  - **Autorización exclusiva (INV-16, FR-017, SC-009)**: Modificable únicamente por el rol `ADMIN`.

---

### 2.3 Estado de Criticidad (`CriticalityState`)
- **Descripción**: Condición lógica reactiva calculada en tiempo de consulta. Determina si el ingrediente requiere reabastecimiento urgente y debe desplegar una alerta visual en el panel administrativo.
- **Fórmula de Evaluación (INV-3, INV-14, FR-004, Clarification C-1)**:
  $$\text{isCritical}(i) = (\text{status}(i) = \text{'ACTIVE'}) \land (\text{threshold}(i) \ne \text{null}) \land (\text{currentStock}(i) < \text{threshold}(i))$$
- **Invariantes Locales**:
  - **Condición estrictamente menor (INV-3, C-1)**: Si $\text{currentStock} = \text{threshold}$, $\text{isCritical} = \text{false}$ (margen de seguridad).
  - **Suspensión en estado descontinuado (INV-17, FR-021, EC-024)**: Si $\text{status} = \text{'DISCONTINUED'}$, $\text{isCritical} = \text{false}$ siempre, preservando el umbral en histórico.
  - **No interferencia comercial (INV-6, FR-009, SC-011)**: $\text{isCritical} = \text{true}$ no altera stocks, no bloquea ventas de platos asociados, no modifica precios ni oculta platos en el menú mientras el stock físico sea $> 0$.
  - **Exclusividad visual (INV-5, FR-008, SC-005)**: Su activación genera únicamente un resaltado visual en la pantalla de inventario de Back-Office. Cero correos, cero SMS, cero push, cero órdenes automáticas.

---

### 2.4 Plato (`Dish`)
- **Descripción**: Preparación gastronómica ofrecida a los comensales, compuesta por una receta técnica de ingredientes y un precio de venta aprobado.
- **Atributos de Dominio Relevantes**:
  - `id`: Identificador unívoco (`UUID`).
  - `tenantId`: Identificador del restaurante (`UUID`).
  - `name`: Nombre comercial del plato (`String`, $\le 100$ caracteres).
  - `category`: Categoría gastronómica (`DishCategory`).
  - `status`: Estado del ciclo de vida culinario (`DishStatus`: `DRAFT`, `PENDING_PRICE`, `PUBLISHED`, `CANCELLED`).
  - `retailPrice`: Precio final al consumidor (`BigDecimal`, 2 decimales, $> 0$).
  - `isActive`: Estado de activación comercial del administrador (`Boolean`).
  - `pausedByChef`: Estado de pausa manual temporal del chef (`Boolean`).

---

### 2.5 Las Tres Causas de Indisponibilidad como Estados Independientes (Invariante Central INV-10)

> [!IMPORTANT]
> **Prohibición de colapso en flag único**: El modelo técnico prohíbe taxativamente reducir la disponibilidad a un flag unificado `isAvailable` en los paneles del personal. Las tres causas coexisten de manera ortogonal y consultable por separado (INV-10, SC-008, SC-014).

| Dimensión | 1. Falta de Stock (`sin_stock`) | 2. Pausa Manual del Chef (`paused_by_chef`) | 3. Desactivación Administrativa (`desactivacion_admin`) |
|---|---|---|---|
| **Representación en el Modelo** | Cálculo dinámico en `DishAvailabilityEngine` evaluando $\forall l \in \text{Recipe}, \text{stock}(l.\text{ing}) \ge l.\text{qty}$. | Atributo booleano dedicado `dishes.paused_by_chef` (`DEFAULT FALSE`). | Atributos `dishes.is_active` (`DEFAULT TRUE`) y `dishes.status` (`PUBLISHED`). |
| **Actor con Autoridad** | Sistema (derivado reactivo del consumo y ajustes). | Chef (conmutado vía toggle de cocina). | Administrador (gestión de catálogo comercial). |
| **Motivo Operativo** | Insumos de la receta agotados en cocina. | Contingencia operativa en servicio (falla de equipos, demora excesiva). | Retiro comercial, estacionalidad o decisión administrativa. |
| **Vía de Recuperación** | Reabastecimiento físico de los ingredientes faltantes. | Despausa voluntaria mediante el toggle del chef (`paused_by_chef = false`). | Reactivación manual en panel administrativo (`is_active = true`). |
| **Efecto en el Comensal** | Plato visible en catálogo, atenuado con distintivo "Agotado" y compra bloqueada. | Plato **totalmente oculto** del catálogo (INV-8). | Plato **totalmente oculto** o no publicado en el catálogo. |
| **Intercambiabilidad** | Ninguna. Ni el admin ni el chef pueden "forzar" stock sin insumos. | Ninguna. El administrador no puede operar el toggle del chef (INV-10, C-4). | Ninguna. El chef no puede activar comercialmente platos cancelados. |
| **Independencia Operativa** | Operable con independencia de las otras causas (INV-12, INV-13). | **Siempre operable** por el chef (INV-19, FR-023), aun con `sin_stock` o desactivo. | Operable por el administrador sin bloquearse por la pausa del chef. |

---

## 3. Esquema Relacional y Migración de Base de Datos

La persistencia se implementa mediante la migración secuencial Flyway **`V11__critical_stock_and_chef_pause.sql`**, extendiendo las tablas existentes sin recreaciones ni rotura de constraints previos.

### 3.1 DDL de Migración (`V11__critical_stock_and_chef_pause.sql`)

```sql
-- ====================================================================
-- EasyEat V11: Critical Stock Threshold and Chef Dish Manual Pause
-- Owns: ingredients.critical_stock_threshold, dishes.paused_by_chef,
--       and performance indexes for critical stock and pause filtering.
-- ====================================================================

-- 1. Extender tabla ingredients con umbral crítico individual
ALTER TABLE ingredients
    ADD COLUMN IF NOT EXISTS critical_stock_threshold INTEGER;

ALTER TABLE ingredients
    ADD CONSTRAINT chk_ingredient_critical_threshold
        CHECK (critical_stock_threshold IS NULL OR critical_stock_threshold >= 0);

COMMENT ON COLUMN ingredients.critical_stock_threshold IS
    'Individual critical stock threshold defined by the Administrator (ADM-4.1.3). '
    'NULL means no threshold configured. When current_stock < critical_stock_threshold, '
    'a visual alert is displayed in the back-office inventory (ADM-4.1.4).';

-- Índice para acelerar el filtro rápido de insumos críticos en el inventario (FR-020)
CREATE INDEX IF NOT EXISTS idx_ingredients_critical_alert
    ON ingredients (tenant_id, status, critical_stock_threshold)
    WHERE status = 'ACTIVE' AND critical_stock_threshold IS NOT NULL;

-- 2. Extender tabla dishes con control de pausa manual del chef
ALTER TABLE dishes
    ADD COLUMN IF NOT EXISTS paused_by_chef BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dishes.paused_by_chef IS
    'Manual operational pause toggled by the Chef during service (CHF-2.2.5). '
    'When TRUE, dish is completely hidden from diner catalog (INV-8). '
    'Independent of stock availability and administrative status (INV-10).';

-- Índice compuesto para consultas de catálogo activo del comensal y bandeja del chef
CREATE INDEX IF NOT EXISTS idx_dishes_catalog_visibility
    ON dishes (tenant_id, status, is_active, paused_by_chef);
```

---

## 4. Mapeo de Entidades JPA

### 4.1 Entidad `Ingredient` (`com.easyeat.inventory.entity.Ingredient`)
- Se añade el campo mapeado:
  ```java
  @Column(name = "critical_stock_threshold")
  private Integer criticalStockThreshold;
  ```
- Método auxiliar de dominio para evaluar criticidad:
  ```java
  @Transient
  public boolean isCritical() {
      if (this.status != IngredientStatus.ACTIVE || this.criticalStockThreshold == null) {
          return false;
      }
      return this.currentStock.compareTo(BigDecimal.valueOf(this.criticalStockThreshold)) < 0;
  }
  ```

### 4.2 Entidad `Dish` (`com.easyeat.catalog.domain.Dish`)
- Se añade el campo mapeado:
  ```java
  @Column(name = "paused_by_chef", nullable = false)
  private Boolean pausedByChef = false;
  ```
- Getters y setters correspondientes: `getPausedByChef()`, `setPausedByChef(Boolean pausedByChef)`.

---

## 5. Matriz de Trazabilidad del Modelo de Datos

| Entidad / Atributo / Regla | Requerimientos Cubiertos | Invariantes Garantizadas | Criterios de Éxito / AC |
|---|---|---|---|
| `ingredients.critical_stock_threshold` | FR-001, FR-002, FR-003, FR-017 | INV-1, INV-2, INV-7, INV-16 | SC-001, SC-002, SC-009, US1 AC-1..AC-4 |
| `isCritical()` dinámico | FR-004, FR-005, FR-006, FR-007, FR-010, FR-021 | INV-3, INV-4, INV-5, INV-6, INV-14, INV-17 | SC-003, SC-004, SC-005, SC-011, SC-012, SC-013, US2 AC-1..AC-5 |
| `dishes.paused_by_chef` | FR-011, FR-012, FR-013, FR-014, FR-018, FR-022, FR-023 | INV-8, INV-9, INV-10, INV-11, INV-16, INV-18, INV-19 | SC-006, SC-007, SC-008, SC-010, SC-014, US3 AC-1..AC-5 |
| Filtro rápido `criticalOnly` | FR-020 | INV-4, INV-14 | SC-015, US2 AC-6 |
| Preservación carrito ante pausa | FR-019 | INV-8, INV-10 | EC-023, C-8 |
| Modal de detalle ante pausa | FR-016 | INV-15 | EC-017, C-5 |

